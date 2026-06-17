package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPoiTrafficDaily;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtPoiTrafficDailyMapper;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Item;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.PoiAdEffectdataResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.poiAdEffectdata.Prop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门店营业分析日流量数据服务
 *
 * @author system
 * @since 2026-04-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPoiTrafficDailyService {

    static final int FLOW_PROP_NATURAL = 1;
    static final int FLOW_PROP_PAID = 2;
    static final int FLOW_PROP_ALL = 3;
    private static final int QUERY_DAYS = 1;
    private static final List<Integer> SUPPORTED_FLOW_PROPS = List.of(FLOW_PROP_NATURAL, FLOW_PROP_PAID, FLOW_PROP_ALL);
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter BIZ_DATE_COMPACT = DateTimeFormatter.BASIC_ISO_DATE;

    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MtPoiTrafficDailyMapper mtPoiTrafficDailyMapper;
    private final MeituanClient meituanClient;
    private final MtWaimaiAdDecryptService mtWaimaiAdDecryptService;
    private final MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler;

    /**
     * 同步昨天的门店流量数据
     */
    public void syncYesterdayTrafficData() {
        syncTrafficData(LocalDate.now(DEFAULT_ZONE_ID).minusDays(1));
    }

    /**
     * 同步指定日期的门店流量数据
     *
     * @param bizDate 业务日期
     */
    public void syncTrafficData(LocalDate bizDate) {
        if (bizDate == null) {
            throw new IllegalArgumentException("业务日期不能为空");
        }

        LocalDateTime now = LocalDateTime.now(DEFAULT_ZONE_ID);
        List<MtAccessToken> candidates = mtAccessTokenMapper.selectTrafficSyncCandidates(now);
        log.info("开始同步门店营业分析流量，bizDate={}, candidateCount={}, supportedFlowProps={}",
                bizDate, candidates.size(), SUPPORTED_FLOW_PROPS);
        int successCount = 0;
        int failCount = 0;
        for (MtAccessToken candidate : candidates) {
            try {
                syncShopTrafficData(candidate, bizDate, now);
                successCount++;
            } catch (RuntimeException e) {
                failCount++;
                log.error("同步门店营业分析流量失败，shopId={}, bizDate={}", candidate.getShopId(), bizDate, e);
            }
        }

        log.info("同步门店营业分析流量完成，bizDate={}, total={}, success={}, fail={}",
                bizDate, candidates.size(), successCount, failCount);
    }

    private void syncShopTrafficData(MtAccessToken accessToken, LocalDate bizDate, LocalDateTime now) {
        log.info("开始同步单门店营业分析流量，shopId={}, bizDate={}, flowPropCount={}",
                accessToken.getShopId(), bizDate, SUPPORTED_FLOW_PROPS.size());
        String currentAccessToken = accessToken.getAccessToken();
        for (Integer flowProp : SUPPORTED_FLOW_PROPS) {
            TrafficFetchResult fetchResult = fetchPoiTraffic(accessToken.getShopId(), currentAccessToken, flowProp);
            currentAccessToken = fetchResult.accessToken();
            accessToken.setAccessToken(currentAccessToken);
            PoiAdEffectdataResponse payload = fetchResult.payload();
            TrafficAggregate aggregate = aggregateTraffic(
                    accessToken.getShopId(),
                    currentAccessToken,
                    bizDate,
                    payload.getData()
            );

            MtPoiTrafficDaily record = new MtPoiTrafficDaily();
            record.setShopId(accessToken.getShopId());
            record.setBizDate(bizDate);
            record.setFlowProp(flowProp);
            record.setExposureCount(aggregate.exposureCount());
            record.setVisitCount(aggregate.visitCount());
            record.setPayloadJson(buildPayloadJson(bizDate, flowProp, payload, aggregate));
            record.setCreateTime(now);
            record.setUpdateTime(now);
            mtPoiTrafficDailyMapper.insertOrUpdate(record);

            log.info("同步门店营业分析流量成功，shopId={}, bizDate={}, flowProp={}, exposureCount={}, visitCount={}",
                    accessToken.getShopId(), bizDate, flowProp, aggregate.exposureCount(), aggregate.visitCount());
        }
        log.info("完成同步单门店营业分析流量，shopId={}, bizDate={}", accessToken.getShopId(), bizDate);
    }

    private TrafficFetchResult fetchPoiTraffic(Long shopId, String accessToken, Integer flowProp) {
        try {
            PoiAdEffectdataRequest request = new PoiAdEffectdataRequest();
            request.setFlowProp(flowProp);
            request.setDays(QUERY_DAYS);
            MtMeituanAuthorizationFailureHandler.AuthorizedCallResult<MeituanResponse<PoiAdEffectdataResponse>> callResult =
                    mtMeituanAuthorizationFailureHandler.invokeWithRefreshRetry(
                            shopId,
                            accessToken,
                            "MT_POI_TRAFFIC_DAILY",
                            token -> meituanClient.invokeApi(request, token),
                            this::inspectPoiTrafficAuthFailure
                    );
            MeituanResponse<PoiAdEffectdataResponse> response = callResult.response();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询门店营业分析流量失败，shopId={}, flowProp={}, response={}", shopId, flowProp, response);
                throw new IllegalStateException("查询门店营业分析流量失败");
            }

            PoiAdEffectdataResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("查询门店营业分析流量业务失败，shopId={}, flowProp={}, result={}",
                        shopId, flowProp, payload.getResult());
                throw new IllegalStateException("查询门店营业分析流量业务失败");
            }
            return new TrafficFetchResult(payload, callResult.accessToken());
        } catch (MtSdkException e) {
            log.error("查询门店营业分析流量异常，shopId={}, flowProp={}", shopId, flowProp, e);
            throw new IllegalStateException("查询门店营业分析流量异常", e);
        }
    }

    private MtMeituanAuthorizationFailureHandler.AuthFailureInfo inspectPoiTrafficAuthFailure(
            MeituanResponse<PoiAdEffectdataResponse> response
    ) {
        if (response == null) {
            return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.notAuthFailure();
        }
        MtMeituanAuthorizationFailureHandler.AuthFailureInfo topLevel =
                MtMeituanAuthorizationFailureHandler.AuthFailureInfo.of(response.getCode(), response.getMsg());
        if (topLevel.authFailure()) {
            return topLevel;
        }
        PoiAdEffectdataResponse payload = response.getData();
        if (payload == null || payload.getResult() == null || Boolean.TRUE.equals(payload.getResult().getSuccess())) {
            return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.notAuthFailure();
        }
        return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.of(
                payload.getResult().getCode(),
                payload.getResult().getMessage()
        );
    }

    private TrafficAggregate aggregateTraffic(Long shopId,
                                              String accessToken,
                                              LocalDate bizDate,
                                              List<Item> items) {
        List<Item> effectiveItems = selectEffectiveItems(bizDate, items);
        Map<String, String> decryptCache = new HashMap<>();
        int exposureCount = 0;
        int visitCount = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Item item : effectiveItems) {
            LocalDate itemBizDate = resolveBizDate(item.getDt());
            if (item.getData() == null) {
                continue;
            }
            for (Prop prop : item.getData()) {
                int show = parseMetricValue(shopId, accessToken, prop.getShow(), "show", decryptCache);
                int click = parseMetricValue(shopId, accessToken, prop.getClick(), "click", decryptCache);
                exposureCount += show;
                visitCount += click;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("dt", item.getDt());
                row.put("bizDate", itemBizDate);
                row.put("flowProp", prop.getFlowProp());
                row.put("pos", prop.getPos());
                row.put("show", prop.getShow());
                row.put("showDecrypted", show);
                row.put("click", prop.getClick());
                row.put("clickDecrypted", click);
                rows.add(row);
            }
        }
        return new TrafficAggregate(exposureCount, visitCount, rows);
    }

    private List<Item> selectEffectiveItems(LocalDate bizDate, List<Item> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<Item> matched = new ArrayList<>();
        for (Item item : items) {
            LocalDate itemBizDate = resolveBizDate(item.getDt());
            if (bizDate.equals(itemBizDate)) {
                matched.add(item);
            }
        }
        if (!matched.isEmpty()) {
            return matched;
        }
        return items;
    }

    private LocalDate resolveBizDate(Long dt) {
        if (dt == null) {
            return null;
        }
        String raw = String.valueOf(dt);
        try {
            if (raw.length() == 8) {
                return LocalDate.parse(raw, BIZ_DATE_COMPACT);
            }
            if (raw.length() == 10) {
                return Instant.ofEpochSecond(dt).atZone(DEFAULT_ZONE_ID).toLocalDate();
            }
            if (raw.length() >= 13) {
                return Instant.ofEpochMilli(dt).atZone(DEFAULT_ZONE_ID).toLocalDate();
            }
        } catch (DateTimeParseException | ArithmeticException ignored) {
            return null;
        }
        return null;
    }

    private int parseMetricValue(
            Long shopId,
            String accessToken,
            String value,
            String fieldName,
            Map<String, String> decryptCache
    ) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }

        String normalized = value.trim();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            String decrypted = mtWaimaiAdDecryptService.decryptValue(
                    shopId,
                    accessToken,
                    normalized,
                    "门店营业分析指标",
                    decryptCache
            );
            try {
                return Integer.parseInt(decrypted.trim());
            } catch (NumberFormatException e) {
                log.error("解析门店营业分析指标异常，shopId={}, fieldName={}, value={}, decrypted={}",
                        shopId, fieldName, value, decrypted, e);
                throw new IllegalStateException("解析门店营业分析指标异常", e);
            }
        }
    }

    private String buildPayloadJson(LocalDate bizDate,
                                    Integer flowProp,
                                    PoiAdEffectdataResponse payload,
                                    TrafficAggregate aggregate) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("source", "mt.wmoper.ng.waimaiad.poi.effectdata");
        content.put("bizDate", bizDate);
        content.put("flowProp", flowProp);
        content.put("days", QUERY_DAYS);
        content.put("result", payload.getResult());
        content.put("rows", aggregate.rows());
        content.put("exposureCount", aggregate.exposureCount());
        content.put("visitCount", aggregate.visitCount());
        return JsonUtil.toJson(content);
    }

    private record TrafficAggregate(int exposureCount, int visitCount, List<Map<String, Object>> rows) {
    }

    private record TrafficFetchResult(PoiAdEffectdataResponse payload, String accessToken) {
    }
}
