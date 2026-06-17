package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPromotionAdRealtimeSnapshot;
import com.gogoga.backend.entity.mt.MtPromotionAdDaily;
import com.gogoga.backend.entity.mt.MtPromotionAdHourlyDetail;
import com.gogoga.backend.mapper.MtPromotionAdDailyMapper;
import com.gogoga.backend.mapper.MtPromotionAdHourlyDetailMapper;
import com.gogoga.backend.mapper.MtPromotionAdRealtimeSnapshotMapper;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataDetailResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adDataDetail.AdDataInfoList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推广广告实时快照服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPromotionAdRealtimeSnapshotService {

    private static final DateTimeFormatter BIZ_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MtPromotionAdRealtimeSnapshotMapper mtPromotionAdRealtimeSnapshotMapper;
    private final MtPromotionAdDailyMapper mtPromotionAdDailyMapper;
    private final MtPromotionAdHourlyDetailMapper mtPromotionAdHourlyDetailMapper;
    private final MeituanClient meituanClient;
    private final MtWaimaiAdDecryptService mtWaimaiAdDecryptService;

    public MtPromotionAdRealtimeSnapshot captureCurrentSlotSnapshot(Long shopId, String accessToken, LocalDateTime slotTime) {
        if (shopId == null || StringUtils.isBlank(accessToken) || slotTime == null) {
            throw new IllegalArgumentException("推广广告实时快照参数不完整");
        }
        LocalDate bizDate = slotTime.toLocalDate();
        AdDataDetailResponse payload = fetchAdDataDetail(shopId, accessToken, bizDate);
        Map<String, String> decryptCache = new HashMap<>();
        int dayShowCount = parseIntMetric(shopId, accessToken, payload.getShow(), "dayShow", decryptCache);
        int dayClickCount = parseIntMetric(shopId, accessToken, payload.getClick(), "dayClick", decryptCache);
        long dayDebitCents = parseLongMetric(shopId, accessToken, payload.getDebit(), "dayDebit", decryptCache);

        HourlySnapshot currentHourlySnapshot = resolveCurrentHourlySnapshot(
                shopId,
                accessToken,
                slotTime,
                payload.getAdDataInfoList(),
                decryptCache
        );

        MtPromotionAdRealtimeSnapshot snapshot = new MtPromotionAdRealtimeSnapshot();
        snapshot.setShopId(shopId);
        snapshot.setBizDate(bizDate);
        snapshot.setSlotTime(slotTime);
        snapshot.setChannel(MtPromotionAdDataService.CHANNEL_DIANJIN);
        snapshot.setCurrentHourRange(currentHourlySnapshot.timeRange());
        snapshot.setHourShowCount(currentHourlySnapshot.showCount());
        snapshot.setHourClickCount(currentHourlySnapshot.clickCount());
        snapshot.setHourDebitCents(currentHourlySnapshot.debitCents());
        snapshot.setDayShowCount(dayShowCount);
        snapshot.setDayClickCount(dayClickCount);
        snapshot.setDayDebitCents(dayDebitCents);
        snapshot.setPayloadJson(buildPayloadJson(slotTime, payload, snapshot, currentHourlySnapshot));
        snapshot.setCreateTime(slotTime);
        snapshot.setUpdateTime(slotTime);
        mtPromotionAdRealtimeSnapshotMapper.insertOrUpdate(snapshot);
        upsertRealtimeDetailRows(shopId, accessToken, slotTime, payload, snapshot, decryptCache);
        return snapshot;
    }

    public MtPromotionAdRealtimeSnapshot selectByShopIdAndBizDateAndSlotTime(Long shopId, LocalDate bizDate, LocalDateTime slotTime) {
        return mtPromotionAdRealtimeSnapshotMapper.selectByShopIdAndBizDateAndSlotTime(shopId, bizDate, slotTime);
    }

    public MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeRange(Long shopId,
                                                                               LocalDateTime startSlotTime,
                                                                               LocalDateTime endSlotTime) {
        return mtPromotionAdRealtimeSnapshotMapper.selectLatestByShopIdAndSlotTimeRange(shopId, startSlotTime, endSlotTime);
    }

    public MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeBefore(Long shopId, LocalDateTime slotTime) {
        return mtPromotionAdRealtimeSnapshotMapper.selectLatestByShopIdAndSlotTimeBefore(shopId, slotTime);
    }

    public MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeBeforeOrEqual(Long shopId, LocalDateTime slotTime) {
        return mtPromotionAdRealtimeSnapshotMapper.selectLatestByShopIdAndSlotTimeBeforeOrEqual(shopId, slotTime);
    }

    public MtPromotionAdRealtimeSnapshot selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(Long shopId,
                                                                                            LocalDateTime startSlotTime,
                                                                                            LocalDateTime endSlotTime) {
        return mtPromotionAdRealtimeSnapshotMapper.selectLatestByShopIdAndSlotTimeRangeBeforeOrEqual(
                shopId,
                startSlotTime,
                endSlotTime
        );
    }

    public MtPromotionAdRealtimeSnapshot selectEarliestByShopIdAndSlotTimeRange(Long shopId,
                                                                                 LocalDateTime startSlotTime,
                                                                                 LocalDateTime endSlotTime) {
        return mtPromotionAdRealtimeSnapshotMapper.selectEarliestByShopIdAndSlotTimeRange(shopId, startSlotTime, endSlotTime);
    }

    private AdDataDetailResponse fetchAdDataDetail(Long shopId, String accessToken, LocalDate bizDate) {
        try {
            AdDataDetailRequest request = new AdDataDetailRequest();
            request.setChannel(MtPromotionAdDataService.CHANNEL_DIANJIN);
            request.setDate(bizDate.format(BIZ_DATE_FORMATTER));
            MeituanResponse<AdDataDetailResponse> response = meituanClient.invokeApi(request, accessToken);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询实时推广广告明细失败，shopId={}, bizDate={}, response={}", shopId, bizDate, response);
                throw new IllegalStateException("查询实时推广广告明细失败");
            }
            AdDataDetailResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("查询实时推广广告明细业务失败，shopId={}, bizDate={}, result={}", shopId, bizDate, payload.getResult());
                throw new IllegalStateException("查询实时推广广告明细业务失败");
            }
            return payload;
        } catch (MtSdkException e) {
            log.error("查询实时推广广告明细异常，shopId={}, bizDate={}", shopId, bizDate, e);
            throw new IllegalStateException("查询实时推广广告明细异常", e);
        }
    }

    private HourlySnapshot resolveCurrentHourlySnapshot(Long shopId,
                                                        String accessToken,
                                                        LocalDateTime slotTime,
                                                        List<AdDataInfoList> rows,
                                                        Map<String, String> decryptCache) {
        String expectedHourRange = String.format("%02d:00-%02d:00", slotTime.getHour(), (slotTime.getHour() + 1) % 24);
        if (rows == null || rows.isEmpty()) {
            return new HourlySnapshot(null, 0, 0, 0L, null);
        }
        for (AdDataInfoList row : rows) {
            if (!StringUtils.equals(row.getTime(), expectedHourRange)) {
                continue;
            }
            int showCount = parseIntMetric(shopId, accessToken, row.getShow(), "hourShow", decryptCache);
            int clickCount = parseIntMetric(shopId, accessToken, row.getClick(), "hourClick", decryptCache);
            long debitCents = parseLongMetric(shopId, accessToken, row.getDebit(), "hourDebit", decryptCache);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("time", row.getTime());
            payload.put("show", row.getShow());
            payload.put("showDecrypted", showCount);
            payload.put("click", row.getClick());
            payload.put("clickDecrypted", clickCount);
            payload.put("debit", row.getDebit());
            payload.put("debitDecryptedCents", debitCents);
            return new HourlySnapshot(row.getTime(), showCount, clickCount, debitCents, JsonUtil.toJson(payload));
        }
        return new HourlySnapshot(null, 0, 0, 0L, null);
    }

    private int parseIntMetric(Long shopId, String accessToken, String value, String fieldName, Map<String, String> decryptCache) {
        if (StringUtils.isBlank(value)) {
            return 0;
        }
        String normalized = value.trim();
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            String decrypted = mtWaimaiAdDecryptService.decryptValue(
                    shopId, accessToken, normalized, "实时推广广告明细" + fieldName, decryptCache);
            try {
                return Integer.parseInt(decrypted.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("解析实时推广广告明细整型指标异常", e);
            }
        }
    }

    private long parseLongMetric(Long shopId, String accessToken, String value, String fieldName, Map<String, String> decryptCache) {
        if (StringUtils.isBlank(value)) {
            return 0L;
        }
        String normalized = value.trim();
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ignored) {
            String decrypted = mtWaimaiAdDecryptService.decryptValue(
                    shopId, accessToken, normalized, "实时推广广告明细" + fieldName, decryptCache);
            try {
                return Long.parseLong(decrypted.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("解析实时推广广告明细金额指标异常", e);
            }
        }
    }

    private String buildPayloadJson(LocalDateTime slotTime,
                                    AdDataDetailResponse payload,
                                    MtPromotionAdRealtimeSnapshot snapshot,
                                    HourlySnapshot hourlySnapshot) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("source", "mt.wmoper.ng.waimaiad.data.detail.realtime");
        content.put("slotTime", slotTime);
        content.put("bizDate", snapshot.getBizDate());
        content.put("channel", snapshot.getChannel());
        content.put("dayShowCount", snapshot.getDayShowCount());
        content.put("dayClickCount", snapshot.getDayClickCount());
        content.put("dayDebitCents", snapshot.getDayDebitCents());
        content.put("currentHourRange", snapshot.getCurrentHourRange());
        content.put("hourShowCount", snapshot.getHourShowCount());
        content.put("hourClickCount", snapshot.getHourClickCount());
        content.put("hourDebitCents", snapshot.getHourDebitCents());
        content.put("result", payload.getResult());
        content.put("hourPayload", hourlySnapshot.payloadJson() == null ? null : JsonUtil.toMap(hourlySnapshot.payloadJson()));
        return JsonUtil.toJson(content);
    }

    private void upsertRealtimeDetailRows(Long shopId,
                                          String accessToken,
                                          LocalDateTime slotTime,
                                          AdDataDetailResponse payload,
                                          MtPromotionAdRealtimeSnapshot snapshot,
                                          Map<String, String> decryptCache) {
        MtPromotionAdDaily daily = new MtPromotionAdDaily();
        daily.setShopId(shopId);
        daily.setBizDate(snapshot.getBizDate());
        daily.setChannel(MtPromotionAdDataService.CHANNEL_DIANJIN);
        daily.setShowCount(snapshot.getDayShowCount());
        daily.setClickCount(snapshot.getDayClickCount());
        daily.setDebitCents(snapshot.getDayDebitCents());
        daily.setPayloadJson(buildRealtimeDailyPayloadJson(slotTime, payload, snapshot));
        daily.setCreateTime(slotTime);
        daily.setUpdateTime(slotTime);
        mtPromotionAdDailyMapper.insertOrUpdate(daily);

        if (payload.getAdDataInfoList() == null || payload.getAdDataInfoList().isEmpty()) {
            return;
        }
        for (AdDataInfoList item : payload.getAdDataInfoList()) {
            int showCount = parseIntMetric(shopId, accessToken, item.getShow(), "persistHourShow", decryptCache);
            int clickCount = parseIntMetric(shopId, accessToken, item.getClick(), "persistHourClick", decryptCache);
            long debitCents = parseLongMetric(shopId, accessToken, item.getDebit(), "persistHourDebit", decryptCache);

            MtPromotionAdHourlyDetail detail = new MtPromotionAdHourlyDetail();
            detail.setShopId(shopId);
            detail.setBizDate(snapshot.getBizDate());
            detail.setChannel(MtPromotionAdDataService.CHANNEL_DIANJIN);
            detail.setTimeRange(item.getTime());
            detail.setShowCount(showCount);
            detail.setClickCount(clickCount);
            detail.setDebitCents(debitCents);
            detail.setPayloadJson(buildRealtimeHourlyPayloadJson(slotTime, snapshot.getBizDate(), item,
                    showCount, clickCount, debitCents));
            detail.setCreateTime(slotTime);
            detail.setUpdateTime(slotTime);
            mtPromotionAdHourlyDetailMapper.insertOrUpdate(detail);
        }
    }

    private String buildRealtimeDailyPayloadJson(LocalDateTime slotTime,
                                                 AdDataDetailResponse payload,
                                                 MtPromotionAdRealtimeSnapshot snapshot) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("source", "mt.wmoper.ng.waimaiad.data.detail.realtime");
        content.put("slotTime", slotTime);
        content.put("bizDate", snapshot.getBizDate());
        content.put("channel", snapshot.getChannel());
        content.put("show", payload.getShow());
        content.put("showDecrypted", snapshot.getDayShowCount());
        content.put("click", payload.getClick());
        content.put("clickDecrypted", snapshot.getDayClickCount());
        content.put("debit", payload.getDebit());
        content.put("debitDecryptedCents", snapshot.getDayDebitCents());
        content.put("result", payload.getResult());
        return JsonUtil.toJson(content);
    }

    private String buildRealtimeHourlyPayloadJson(LocalDateTime slotTime,
                                                  LocalDate bizDate,
                                                  AdDataInfoList item,
                                                  int showCount,
                                                  int clickCount,
                                                  long debitCents) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "mt.wmoper.ng.waimaiad.data.detail.realtime");
        payload.put("slotTime", slotTime);
        payload.put("bizDate", bizDate);
        payload.put("channel", MtPromotionAdDataService.CHANNEL_DIANJIN);
        payload.put("time", item.getTime());
        payload.put("show", item.getShow());
        payload.put("showDecrypted", showCount);
        payload.put("click", item.getClick());
        payload.put("clickDecrypted", clickCount);
        payload.put("debit", item.getDebit());
        payload.put("debitDecryptedCents", debitCents);
        return JsonUtil.toJson(payload);
    }

    private record HourlySnapshot(String timeRange, int showCount, int clickCount, long debitCents, String payloadJson) {
    }
}
