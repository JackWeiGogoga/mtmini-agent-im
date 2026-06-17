package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import com.gogoga.backend.entity.mt.MtShopPeakWindowProfile;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门店高峰画像服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopPeakWindowProfileService {

    public static final String PROFILE_STATUS_USABLE = "USABLE";
    public static final String PROFILE_STATUS_INSUFFICIENT = "INSUFFICIENT";
    public static final String UNAVAILABLE_REASON_PROFILE_MISSING = "PROFILE_MISSING";
    public static final String UNAVAILABLE_REASON_SAMPLE_INSUFFICIENT = "SAMPLE_INSUFFICIENT";

    private static final int LOOKBACK_DAYS = 21;
    private static final int MIN_SAMPLE_DAY_COUNT = 3;
    private static final int MIN_SAMPLE_ORDER_COUNT = 10;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final MtPromotionSettingMapper mtPromotionSettingMapper;
    private final MtOrderMapper mtOrderMapper;
    private final MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;
    private final ShopPeakWindowDayTypeResolver dayTypeResolver;
    private final ShopPeakWindowDetector shopPeakWindowDetector;
    private PromotionRecentOrderStatisticService promotionRecentOrderStatisticService;

    @Autowired(required = false)
    public void setPromotionRecentOrderStatisticService(PromotionRecentOrderStatisticService promotionRecentOrderStatisticService) {
        this.promotionRecentOrderStatisticService = promotionRecentOrderStatisticService;
    }

    public ShopPeakWindowProfileLookupResult lookup(Long shopId, LocalDateTime slotTime) {
        ShopPeakDayType dayType = dayTypeResolver.resolve(slotTime);
        ShopPeakWindowProfileLookupResult result = new ShopPeakWindowProfileLookupResult();
        result.setDayType(dayType);
        ChinaWorkdayCalendarService.ChinaWorkdayCalendarDay calendarDay =
                slotTime == null ? null : dayTypeResolver.resolveCalendarDay(slotTime.toLocalDate());
        result.setCalendarDayType(calendarDay == null ? null : calendarDay.dayType());
        result.setHolidayName(calendarDay == null ? null : calendarDay.holidayName());

        if (shopId == null) {
            result.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE);
            result.setGatingEnabled(false);
            result.setUnavailableReason(UNAVAILABLE_REASON_PROFILE_MISSING);
            return result;
        }

        MtShopPeakWindowProfile profile = null;
        ShopPeakDayType profileDayType = null;
        for (ShopPeakDayType fallbackDayType : resolveFallbackChain(dayType)) {
            MtShopPeakWindowProfile currentProfile = mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(shopId, fallbackDayType.name());
            if (currentProfile == null) {
                continue;
            }
            if (PROFILE_STATUS_USABLE.equals(currentProfile.getProfileStatus())) {
                profile = currentProfile;
                profileDayType = fallbackDayType;
                break;
            }
            if (profile == null) {
                profile = currentProfile;
                profileDayType = fallbackDayType;
            }
        }
        if (profile == null) {
            result.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE);
            result.setGatingEnabled(false);
            result.setUnavailableReason(UNAVAILABLE_REASON_PROFILE_MISSING);
            return result;
        }

        result.setProfileDayType(profileDayType);
        result.setSampleDayCount(profile.getSampleDayCount());
        result.setSampleOrderCount(profile.getSampleOrderCount());
        result.setUnavailableReason(profile.getUnavailableReason());
        result.setGeneratedAt(profile.getGeneratedAt());
        if (!PROFILE_STATUS_USABLE.equals(profile.getProfileStatus())) {
            result.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE);
            result.setGatingEnabled(false);
            return result;
        }

        List<ShopPeakWindow> windows = parseWindows(profile.getPeakWindowsJson());
        result.setPeakWindows(windows);
        LocalTime slot = slotTime == null ? null : slotTime.toLocalTime();
        for (ShopPeakWindow window : windows) {
            if (window.matches(slot)) {
                result.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
                result.setGatingEnabled(true);
                result.setPeakMatched(true);
                result.setMatchedWindow(window.toDisplayText());
                return result;
            }
        }
        result.setProfileStatus(ShopPeakWindowProfileLookupResult.STATUS_USABLE);
        result.setGatingEnabled(true);
        result.setPeakMatched(false);
        return result;
    }

    private List<ShopPeakDayType> resolveFallbackChain(ShopPeakDayType dayType) {
        if (dayType == ShopPeakDayType.HOLIDAY) {
            return List.of(ShopPeakDayType.HOLIDAY, ShopPeakDayType.WEEKEND, ShopPeakDayType.WEEKDAY);
        }
        if (dayType == ShopPeakDayType.WEEKEND) {
            return List.of(ShopPeakDayType.WEEKEND, ShopPeakDayType.WEEKDAY);
        }
        return List.of(ShopPeakDayType.WEEKDAY);
    }

    public void refreshProfilesForInScopeShops(LocalDateTime now) {
        LocalDateTime refreshTime = now == null ? LocalDateTime.now() : now;
        List<Long> shopIds = mtPromotionSettingMapper.selectPeakProfileRefreshShopIds(refreshTime);
        log.info("开始刷新门店高峰画像，shopCount={}, refreshTime={}", shopIds.size(), refreshTime);
        for (Long shopId : shopIds) {
            refreshProfilesForShop(shopId, refreshTime);
        }
        log.info("门店高峰画像刷新完成，shopCount={}, refreshTime={}", shopIds.size(), refreshTime);
    }

    public void refreshProfilesForShop(Long shopId, LocalDateTime now) {
        if (shopId == null) {
            return;
        }
        LocalDateTime refreshTime = now == null ? LocalDateTime.now() : now;
        LocalDate endDateExclusive = refreshTime.toLocalDate().plusDays(1);
        LocalDate startDateInclusive = endDateExclusive.minusDays(LOOKBACK_DAYS);
        List<MtOrderHourlyAggregate> aggregates = mtOrderMapper.selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(
                shopId,
                MtOrderStatusEnum.CANCELED.getCode(),
                startDateInclusive.atStartOfDay(ZONE_ID).toEpochSecond(),
                endDateExclusive.atStartOfDay(ZONE_ID).toEpochSecond()
        );

        Map<ShopPeakDayType, List<MtOrderHourlyAggregate>> groupedByDayType = groupByDayType(aggregates);
        for (ShopPeakDayType dayType : List.of(ShopPeakDayType.WEEKDAY, ShopPeakDayType.WEEKEND)) {
            persistProfile(shopId, dayType, groupedByDayType.getOrDefault(dayType, List.of()), refreshTime);
        }
    }

    private void persistProfile(
            Long shopId,
            ShopPeakDayType dayType,
            List<MtOrderHourlyAggregate> aggregates,
            LocalDateTime refreshTime
    ) {
        Set<LocalDate> sampleDates = new LinkedHashSet<>();
        int sampleOrderCount = 0;
        for (MtOrderHourlyAggregate aggregate : aggregates) {
            if (aggregate.getOrderDate() != null) {
                sampleDates.add(aggregate.getOrderDate());
            }
            sampleOrderCount += aggregate.getOrderCount() == null ? 0 : aggregate.getOrderCount();
        }

        List<MtOrderHourlyAggregate> mergedByHour = mergeByHour(aggregates);
        List<ShopPeakWindow> windows = shopPeakWindowDetector.detect(mergedByHour);

        MtShopPeakWindowProfile profile = new MtShopPeakWindowProfile();
        profile.setShopId(shopId);
        profile.setDayType(dayType.name());
        profile.setLookbackDays(LOOKBACK_DAYS);
        profile.setSampleDayCount(sampleDates.size());
        profile.setSampleOrderCount(sampleOrderCount);
        profile.setGeneratedAt(refreshTime);
        profile.setCreateTime(refreshTime);
        profile.setUpdateTime(refreshTime);

        if (sampleDates.size() < MIN_SAMPLE_DAY_COUNT || sampleOrderCount < MIN_SAMPLE_ORDER_COUNT || CollectionUtils.isEmpty(windows)) {
            profile.setProfileStatus(PROFILE_STATUS_INSUFFICIENT);
            profile.setUnavailableReason(UNAVAILABLE_REASON_SAMPLE_INSUFFICIENT);
            profile.setPeakWindowsJson(CollectionUtils.isEmpty(windows) ? "[]" : JsonUtil.toJson(windows));
        } else {
            profile.setProfileStatus(PROFILE_STATUS_USABLE);
            profile.setUnavailableReason(null);
            profile.setPeakWindowsJson(JsonUtil.toJson(windows));
        }
        mtShopPeakWindowProfileMapper.upsert(profile);
        if (promotionRecentOrderStatisticService != null) {
            promotionRecentOrderStatisticService.invalidateByShopIdAndBizDateOnOrAfter(shopId, refreshTime.toLocalDate());
        }
        log.info("门店高峰画像刷新完成，shopId={}, dayType={}, status={}, sampleDayCount={}, sampleOrderCount={}, windows={}",
                shopId, dayType, profile.getProfileStatus(), profile.getSampleDayCount(), profile.getSampleOrderCount(), profile.getPeakWindowsJson());
    }

    private Map<ShopPeakDayType, List<MtOrderHourlyAggregate>> groupByDayType(List<MtOrderHourlyAggregate> aggregates) {
        Map<ShopPeakDayType, List<MtOrderHourlyAggregate>> grouped = new EnumMap<>(ShopPeakDayType.class);
        if (aggregates == null) {
            return grouped;
        }
        for (MtOrderHourlyAggregate aggregate : aggregates) {
            ShopPeakDayType dayType = resolveProfileSampleDayType(aggregate.getOrderDate());
            grouped.computeIfAbsent(dayType, key -> new ArrayList<>()).add(aggregate);
        }
        return grouped;
    }

    private ShopPeakDayType resolveProfileSampleDayType(LocalDate orderDate) {
        ShopPeakDayType dayType = dayTypeResolver.resolve(orderDate);
        return dayType == ShopPeakDayType.HOLIDAY ? ShopPeakDayType.WEEKEND : dayType;
    }

    private List<MtOrderHourlyAggregate> mergeByHour(List<MtOrderHourlyAggregate> aggregates) {
        Map<Integer, MtOrderHourlyAggregate> merged = new java.util.TreeMap<>();
        for (MtOrderHourlyAggregate aggregate : aggregates) {
            if (aggregate.getHourBucket() == null) {
                continue;
            }
            MtOrderHourlyAggregate current = merged.computeIfAbsent(aggregate.getHourBucket(), key -> {
                MtOrderHourlyAggregate item = new MtOrderHourlyAggregate();
                item.setHourBucket(key);
                item.setOrderCount(0);
                item.setIncomeAmount(java.math.BigDecimal.ZERO);
                return item;
            });
            current.setOrderCount(current.getOrderCount() + (aggregate.getOrderCount() == null ? 0 : aggregate.getOrderCount()));
            current.setIncomeAmount(current.getIncomeAmount().add(
                    aggregate.getIncomeAmount() == null ? java.math.BigDecimal.ZERO : aggregate.getIncomeAmount()
            ));
        }
        return new ArrayList<>(merged.values());
    }

    private List<ShopPeakWindow> parseWindows(String peakWindowsJson) {
        List<ShopPeakWindow> windows = JsonUtil.toList(peakWindowsJson, ShopPeakWindow.class);
        return windows == null ? List.of() : windows;
    }
}
