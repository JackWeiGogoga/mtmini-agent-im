package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtPromotionAdDailyMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataInfoList;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataHistoryRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataHistoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门店推广广告明细数据同步服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPromotionAdDataService {

    static final int CHANNEL_DIANJIN = 1;
    static final int AUTH_SYNC_DAYS = 7;
    static final int HISTORY_TYPE_SINGLE_SHOP = 3;
    static final String BACKFILL_STATUS_INIT = "INIT";
    static final String BACKFILL_STATUS_SUCCESS = "SUCCESS";
    static final String BACKFILL_STATUS_EMPTY_SUCCESS = "EMPTY_SUCCESS";
    static final String BACKFILL_STATUS_FAILED = "FAILED";
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter BIZ_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HISTORY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long BACKFILL_RETRY_DAYS = 1L;

    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MtPromotionAdDailyMapper mtPromotionAdDailyMapper;
    private final MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;
    private final MtShopService mtShopService;
    private final MeituanClient meituanClient;
    private final MtWaimaiAdDecryptService mtWaimaiAdDecryptService;
    private final MtMeituanAuthorizationFailureHandler mtMeituanAuthorizationFailureHandler;

    public void syncYesterdayAdData() {
        syncAdData(LocalDate.now(DEFAULT_ZONE_ID).minusDays(1));
    }

    public void syncAdData(LocalDate bizDate) {
        if (bizDate == null) {
            throw new IllegalArgumentException("业务日期不能为空");
        }
        LocalDateTime now = LocalDateTime.now(DEFAULT_ZONE_ID);
        List<MtAccessToken> candidates = mtAccessTokenMapper.selectTrafficSyncCandidates(now);
        log.info("开始同步门店推广广告明细，bizDate={}, candidateCount={}, channel={}",
                bizDate, candidates.size(), CHANNEL_DIANJIN);
        int successCount = 0;
        int failCount = 0;
        for (MtAccessToken candidate : candidates) {
            try {
                syncShopAdData(candidate.getShopId(), candidate.getAccessToken(), bizDate, now);
                successCount++;
            } catch (RuntimeException e) {
                failCount++;
                log.error("同步门店推广广告明细失败，shopId={}, bizDate={}", candidate.getShopId(), bizDate, e);
            }
        }
        log.info("同步门店推广广告明细完成，bizDate={}, total={}, success={}, fail={}",
                bizDate, candidates.size(), successCount, failCount);
    }

    public void syncRecentAdDataAfterAuthorization(Long shopId, String accessToken) {
        ensureRecentAdDataBackfill(shopId, accessToken, "授权后补齐", true);
    }

    public void ensureRecentAdDataBackfillBeforeLogGeneration(Long shopId, String accessToken) {
        ensureRecentAdDataBackfill(shopId, accessToken, "日志生成前补偿", true);
    }

    private void ensureRecentAdDataBackfill(Long shopId, String accessToken, String trigger, boolean swallowException) {
        if (shopId == null || StringUtils.isBlank(accessToken)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(DEFAULT_ZONE_ID);
        MtShop shop = mtShopService.findById(shopId);
        if (shop == null || !shouldAttemptBackfill(shop, now)) {
            return;
        }
        log.info("{}推广广告明细开始，shopId={}, currentStatus={}", trigger, shopId, shop.getPromotionAdBackfillStatus());
        try {
            BackfillSummary summary = syncRecentAdDataRange(shopId, accessToken, now);
            String targetStatus = summary.hasAnyData() ? BACKFILL_STATUS_SUCCESS : BACKFILL_STATUS_EMPTY_SUCCESS;
            mtShopService.updatePromotionAdBackfillState(shopId, targetStatus, now);
            log.info("{}推广广告明细完成，shopId={}, status={}, syncedDays={}", trigger, shopId, targetStatus, summary.syncedDays());
        } catch (RuntimeException e) {
            mtShopService.updatePromotionAdBackfillState(shopId, BACKFILL_STATUS_FAILED, now);
            log.warn("{}推广广告明细失败，shopId={}, reason={}", trigger, shopId, e.getMessage());
            if (!swallowException) {
                throw e;
            }
        }
    }

    private boolean shouldAttemptBackfill(MtShop shop, LocalDateTime now) {
        String status = StringUtils.defaultIfBlank(shop.getPromotionAdBackfillStatus(), BACKFILL_STATUS_INIT);
        if (BACKFILL_STATUS_SUCCESS.equals(status) || BACKFILL_STATUS_EMPTY_SUCCESS.equals(status)) {
            return false;
        }
        if (BACKFILL_STATUS_FAILED.equals(status)) {
            return shop.getPromotionAdBackfillAt() == null
                    || shop.getPromotionAdBackfillAt().isBefore(now.minusDays(BACKFILL_RETRY_DAYS));
        }
        return true;
    }

    private BackfillSummary syncRecentAdDataRange(Long shopId, String accessToken, LocalDateTime now) {
        AccessTokenHolder accessTokenHolder = new AccessTokenHolder(accessToken);
        HistoryBackfillSummary historySummary = syncRecentDailyHistory(shopId, accessTokenHolder, now);
        boolean yesterdayHasAnyData = syncShopAdData(shopId, accessTokenHolder, now.toLocalDate().minusDays(1), now);
        return new BackfillSummary(historySummary.hasAnyData() || yesterdayHasAnyData, historySummary.syncedDays());
    }

    private HistoryBackfillSummary syncRecentDailyHistory(Long shopId, AccessTokenHolder accessTokenHolder, LocalDateTime now) {
        AdDataHistoryResponse payload = fetchAdDataHistory(shopId, accessTokenHolder);
        List<com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList> rows = payload.getAdDataInfoList();
        if (rows == null || rows.isEmpty()) {
            return new HistoryBackfillSummary(false, 0);
        }

        Map<String, String> decryptCache = new HashMap<>();
        boolean hasAnyData = false;
        int syncedDays = 0;
        for (com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList row : rows) {
            LocalDate bizDate = parseHistoryBizDate(row == null ? null : row.getTime());
            if (bizDate == null) {
                log.warn("跳过无法识别日期的推广广告历史数据，shopId={}, rawTime={}", shopId, row == null ? null : row.getTime());
                continue;
            }

            int showCount = parseIntMetric(shopId, accessTokenHolder.value(), row.getShow(), "historyDayShow", decryptCache);
            int clickCount = parseIntMetric(shopId, accessTokenHolder.value(), row.getClick(), "historyDayClick", decryptCache);
            long debitCents = parseLongMetric(shopId, accessTokenHolder.value(), row.getDebit(), "historyDayDebit", decryptCache);

            MtPromotionAdDaily daily = new MtPromotionAdDaily();
            daily.setShopId(shopId);
            daily.setBizDate(bizDate);
            daily.setChannel(CHANNEL_DIANJIN);
            daily.setShowCount(showCount);
            daily.setClickCount(clickCount);
            daily.setDebitCents(debitCents);
            daily.setPayloadJson(buildHistoryDailyPayloadJson(bizDate, row, showCount, clickCount, debitCents));
            daily.setCreateTime(now);
            daily.setUpdateTime(now);
            mtPromotionAdDailyMapper.insertOrUpdate(daily);

            syncedDays++;
            if (showCount > 0 || clickCount > 0 || debitCents > 0) {
                hasAnyData = true;
            }
        }
        return new HistoryBackfillSummary(hasAnyData, syncedDays);
    }

    @Transactional(rollbackFor = Exception.class)
    boolean syncShopAdData(Long shopId, String accessToken, LocalDate bizDate, LocalDateTime now) {
        return syncShopAdData(shopId, new AccessTokenHolder(accessToken), bizDate, now);
    }

    private boolean syncShopAdData(Long shopId, AccessTokenHolder accessTokenHolder, LocalDate bizDate, LocalDateTime now) {
        AdDataDetailResponse payload = fetchAdDataDetail(shopId, accessTokenHolder, bizDate);
        AdDataAggregate aggregate = aggregate(shopId, accessTokenHolder.value(), bizDate, payload);
        if (!aggregate.hasAnyData() && hasPersistedNonZeroAdData(shopId, bizDate)) {
            log.warn("跳过零值推广广告明细覆盖，shopId={}, bizDate={}, detailCount={}",
                    shopId, bizDate, aggregate.rows().size());
            return true;
        }

        MtPromotionAdDaily daily = new MtPromotionAdDaily();
        daily.setShopId(shopId);
        daily.setBizDate(bizDate);
        daily.setChannel(CHANNEL_DIANJIN);
        daily.setShowCount(aggregate.summaryShowCount());
        daily.setClickCount(aggregate.summaryClickCount());
        daily.setDebitCents(aggregate.summaryDebitCents());
        daily.setPayloadJson(buildDailyPayloadJson(bizDate, payload, aggregate));
        daily.setCreateTime(now);
        daily.setUpdateTime(now);
        mtPromotionAdDailyMapper.insertOrUpdate(daily);

        mtPromotionAdHourlyDetailMapper.deleteByShopIdAndBizDateAndChannel(shopId, bizDate, CHANNEL_DIANJIN);
        for (HourlyAggregate row : aggregate.rows()) {
            MtPromotionAdHourlyDetail detail = new MtPromotionAdHourlyDetail();
            detail.setShopId(shopId);
            detail.setBizDate(bizDate);
            detail.setChannel(CHANNEL_DIANJIN);
            detail.setTimeRange(row.timeRange());
            detail.setShowCount(row.showCount());
            detail.setClickCount(row.clickCount());
            detail.setDebitCents(row.debitCents());
            detail.setPayloadJson(row.payloadJson());
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            mtPromotionAdHourlyDetailMapper.insertOrUpdate(detail);
        }
        log.info("同步门店推广广告明细成功，shopId={}, bizDate={}, detailCount={}, show={}, click={}, debitCents={}",
                shopId, bizDate, aggregate.rows().size(), aggregate.summaryShowCount(),
                aggregate.summaryClickCount(), aggregate.summaryDebitCents());
        return aggregate.hasAnyData();
    }

    private boolean hasPersistedNonZeroAdData(Long shopId, LocalDate bizDate) {
        MtPromotionAdDaily daily = mtPromotionAdDailyMapper.selectByShopIdAndBizDateAndChannel(
                shopId, bizDate, CHANNEL_DIANJIN);
        if (hasPositiveValue(daily == null ? null : daily.getShowCount())
                || hasPositiveValue(daily == null ? null : daily.getClickCount())
                || hasPositiveValue(daily == null ? null : daily.getDebitCents())) {
            return true;
        }
        List<MtPromotionAdHourlyDetail> details = mtPromotionAdHourlyDetailMapper.selectByShopIdAndBizDateAndChannel(
                shopId, bizDate, CHANNEL_DIANJIN);
        if (details == null || details.isEmpty()) {
            return false;
        }
        return details.stream().anyMatch(detail ->
                hasPositiveValue(detail.getShowCount())
                        || hasPositiveValue(detail.getClickCount())
                        || hasPositiveValue(detail.getDebitCents()));
    }

    private boolean hasPositiveValue(Number value) {
        return value != null && value.longValue() > 0L;
    }

    private AdDataDetailResponse fetchAdDataDetail(Long shopId, AccessTokenHolder accessTokenHolder, LocalDate bizDate) {
        try {
            AdDataDetailRequest request = new AdDataDetailRequest();
            request.setChannel(CHANNEL_DIANJIN);
            request.setDate(bizDate.format(BIZ_DATE_FORMATTER));
            MtMeituanAuthorizationFailureHandler.AuthorizedCallResult<MeituanResponse<AdDataDetailResponse>> callResult =
                    mtMeituanAuthorizationFailureHandler.invokeWithRefreshRetry(
                            shopId,
                            accessTokenHolder.value(),
                            "MT_PROMOTION_AD_DETAIL",
                            token -> meituanClient.invokeApi(request, token),
                            this::inspectAdDataDetailAuthFailure
                    );
            accessTokenHolder.value(callResult.accessToken());
            MeituanResponse<AdDataDetailResponse> response = callResult.response();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询门店推广广告明细失败，shopId={}, bizDate={}, response={}", shopId, bizDate, response);
                throw new IllegalStateException("查询门店推广广告明细失败");
            }
            AdDataDetailResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("查询门店推广广告明细业务失败，shopId={}, bizDate={}, result={}",
                        shopId, bizDate, payload.getResult());
                throw new IllegalStateException("查询门店推广广告明细业务失败");
            }
            return payload;
        } catch (MtSdkException e) {
            log.error("查询门店推广广告明细异常，shopId={}, bizDate={}", shopId, bizDate, e);
            throw new IllegalStateException("查询门店推广广告明细异常", e);
        }
    }

    private AdDataHistoryResponse fetchAdDataHistory(Long shopId, AccessTokenHolder accessTokenHolder) {
        try {
            AdDataHistoryRequest request = new AdDataHistoryRequest();
            request.setChannel(CHANNEL_DIANJIN);
            request.setDays(AUTH_SYNC_DAYS);
            request.setType(HISTORY_TYPE_SINGLE_SHOP);
            MtMeituanAuthorizationFailureHandler.AuthorizedCallResult<MeituanResponse<AdDataHistoryResponse>> callResult =
                    mtMeituanAuthorizationFailureHandler.invokeWithRefreshRetry(
                            shopId,
                            accessTokenHolder.value(),
                            "MT_PROMOTION_AD_HISTORY",
                            token -> meituanClient.invokeApi(request, token),
                            this::inspectAdDataHistoryAuthFailure
                    );
            accessTokenHolder.value(callResult.accessToken());
            MeituanResponse<AdDataHistoryResponse> response = callResult.response();
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询门店推广广告历史数据失败，shopId={}, response={}", shopId, response);
                throw new IllegalStateException("查询门店推广广告历史数据失败");
            }
            AdDataHistoryResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("查询门店推广广告历史数据业务失败，shopId={}, result={}", shopId, payload.getResult());
                throw new IllegalStateException("查询门店推广广告历史数据业务失败");
            }
            return payload;
        } catch (MtSdkException e) {
            log.error("查询门店推广广告历史数据异常，shopId={}", shopId, e);
            throw new IllegalStateException("查询门店推广广告历史数据异常", e);
        }
    }

    private MtMeituanAuthorizationFailureHandler.AuthFailureInfo inspectAdDataDetailAuthFailure(
            MeituanResponse<AdDataDetailResponse> response
    ) {
        if (response == null) {
            return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.notAuthFailure();
        }
        MtMeituanAuthorizationFailureHandler.AuthFailureInfo topLevel =
                MtMeituanAuthorizationFailureHandler.AuthFailureInfo.of(response.getCode(), response.getMsg());
        if (topLevel.authFailure()) {
            return topLevel;
        }
        AdDataDetailResponse payload = response.getData();
        if (payload == null || payload.getResult() == null || Boolean.TRUE.equals(payload.getResult().getSuccess())) {
            return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.notAuthFailure();
        }
        return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.of(
                payload.getResult().getCode(),
                payload.getResult().getMessage()
        );
    }

    private MtMeituanAuthorizationFailureHandler.AuthFailureInfo inspectAdDataHistoryAuthFailure(
            MeituanResponse<AdDataHistoryResponse> response
    ) {
        if (response == null) {
            return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.notAuthFailure();
        }
        MtMeituanAuthorizationFailureHandler.AuthFailureInfo topLevel =
                MtMeituanAuthorizationFailureHandler.AuthFailureInfo.of(response.getCode(), response.getMsg());
        if (topLevel.authFailure()) {
            return topLevel;
        }
        AdDataHistoryResponse payload = response.getData();
        if (payload == null || payload.getResult() == null || Boolean.TRUE.equals(payload.getResult().getSuccess())) {
            return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.notAuthFailure();
        }
        return MtMeituanAuthorizationFailureHandler.AuthFailureInfo.of(
                payload.getResult().getCode(),
                payload.getResult().getMessage()
        );
    }

    private AdDataAggregate aggregate(Long shopId, String accessToken, LocalDate bizDate, AdDataDetailResponse payload) {
        Map<String, String> decryptCache = new HashMap<>();
        int summaryShowCount = parseIntMetric(shopId, accessToken, payload.getShow(), "dayShow", decryptCache);
        int summaryClickCount = parseIntMetric(shopId, accessToken, payload.getClick(), "dayClick", decryptCache);
        long summaryDebitCents = parseLongMetric(shopId, accessToken, payload.getDebit(), "dayDebit", decryptCache);

        List<HourlyAggregate> rows = new ArrayList<>();
        if (payload.getAdDataInfoList() != null) {
            for (AdDataInfoList item : payload.getAdDataInfoList()) {
                int showCount = parseIntMetric(shopId, accessToken, item.getShow(), "hourShow", decryptCache);
                int clickCount = parseIntMetric(shopId, accessToken, item.getClick(), "hourClick", decryptCache);
                long debitCents = parseLongMetric(shopId, accessToken, item.getDebit(), "hourDebit", decryptCache);

                Map<String, Object> rowPayload = new LinkedHashMap<>();
                rowPayload.put("source", "mt.wmoper.ng.waimaiad.data.detail");
                rowPayload.put("bizDate", bizDate);
                rowPayload.put("channel", CHANNEL_DIANJIN);
                rowPayload.put("time", item.getTime());
                rowPayload.put("show", item.getShow());
                rowPayload.put("showDecrypted", showCount);
                rowPayload.put("click", item.getClick());
                rowPayload.put("clickDecrypted", clickCount);
                rowPayload.put("debit", item.getDebit());
                rowPayload.put("debitDecryptedCents", debitCents);
                rows.add(new HourlyAggregate(item.getTime(), showCount, clickCount, debitCents, JsonUtil.toJson(rowPayload)));
            }
        }
        return new AdDataAggregate(summaryShowCount, summaryClickCount, summaryDebitCents, rows);
    }

    private int parseIntMetric(Long shopId,
                               String accessToken,
                               String value,
                               String fieldName,
                               Map<String, String> decryptCache) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        String normalized = value.trim();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            String decrypted = mtWaimaiAdDecryptService.decryptValue(
                    shopId, accessToken, normalized, "推广广告明细" + fieldName, decryptCache);
            try {
                return Integer.parseInt(decrypted.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("解析推广广告明细整型指标异常", e);
            }
        }
    }

    private long parseLongMetric(Long shopId,
                                 String accessToken,
                                 String value,
                                 String fieldName,
                                 Map<String, String> decryptCache) {
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        String normalized = value.trim();
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            String decrypted = mtWaimaiAdDecryptService.decryptValue(
                    shopId, accessToken, normalized, "推广广告明细" + fieldName, decryptCache);
            try {
                return Long.parseLong(decrypted.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("解析推广广告明细金额指标异常", e);
            }
        }
    }

    private String buildDailyPayloadJson(LocalDate bizDate, AdDataDetailResponse payload, AdDataAggregate aggregate) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("source", "mt.wmoper.ng.waimaiad.data.detail");
        content.put("bizDate", bizDate);
        content.put("channel", CHANNEL_DIANJIN);
        content.put("result", payload.getResult());
        content.put("show", payload.getShow());
        content.put("showDecrypted", aggregate.summaryShowCount());
        content.put("click", payload.getClick());
        content.put("clickDecrypted", aggregate.summaryClickCount());
        content.put("debit", payload.getDebit());
        content.put("debitDecryptedCents", aggregate.summaryDebitCents());
        content.put("rows", aggregate.rows().stream().map(row -> JsonUtil.toMap(row.payloadJson())).toList());
        return JsonUtil.toJson(content);
    }

    private String buildHistoryDailyPayloadJson(LocalDate bizDate,
                                                com.meituan.sdk.model.wmoperNg.waimaiad.adDataHistory.AdDataInfoList row,
                                                int showCount,
                                                int clickCount,
                                                long debitCents) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("source", "mt.wmoper.ng.waimaiad.data.history");
        content.put("bizDate", bizDate);
        content.put("channel", CHANNEL_DIANJIN);
        content.put("days", AUTH_SYNC_DAYS);
        content.put("type", HISTORY_TYPE_SINGLE_SHOP);
        content.put("time", row == null ? null : row.getTime());
        content.put("show", row == null ? null : row.getShow());
        content.put("showDecrypted", showCount);
        content.put("click", row == null ? null : row.getClick());
        content.put("clickDecrypted", clickCount);
        content.put("debit", row == null ? null : row.getDebit());
        content.put("debitDecryptedCents", debitCents);
        return JsonUtil.toJson(content);
    }

    private LocalDate parseHistoryBizDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            return LocalDate.parse(normalized, BIZ_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(normalized, HISTORY_DATE_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private record AdDataAggregate(int summaryShowCount,
                                   int summaryClickCount,
                                   long summaryDebitCents,
                                   List<HourlyAggregate> rows) {
        boolean hasAnyData() {
            if (summaryShowCount > 0 || summaryClickCount > 0 || summaryDebitCents > 0) {
                return true;
            }
            return rows != null && rows.stream().anyMatch(HourlyAggregate::hasAnyData);
        }
    }

    private record HourlyAggregate(String timeRange,
                                   int showCount,
                                   int clickCount,
                                   long debitCents,
                                   String payloadJson) {
        boolean hasAnyData() {
            return showCount > 0 || clickCount > 0 || debitCents > 0;
        }
    }

    private record BackfillSummary(boolean hasAnyData, int syncedDays) {
    }

    private record HistoryBackfillSummary(boolean hasAnyData, int syncedDays) {
    }

    private static final class AccessTokenHolder {

        private String value;

        private AccessTokenHolder(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }

        private void value(String value) {
            this.value = value;
        }
    }
}
