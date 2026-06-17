package com.gogoga.backend.service.promotion;

import com.gogoga.backend.entity.mt.MtOrderHourlyAggregate;
import com.gogoga.backend.entity.mt.MtShopPeakWindowProfile;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.mapper.MtPromotionSettingMapper;
import com.gogoga.backend.mapper.MtShopPeakWindowProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopPeakWindowProfileServiceTest {

    @Mock
    private MtPromotionSettingMapper mtPromotionSettingMapper;

    @Mock
    private MtOrderMapper mtOrderMapper;

    @Mock
    private MtShopPeakWindowProfileMapper mtShopPeakWindowProfileMapper;

    @Test
    void shouldPersistInsufficientProfileWhenSamplesAreNotEnough() {
        ShopPeakWindowProfileService service = new ShopPeakWindowProfileService(
                mtPromotionSettingMapper,
                mtOrderMapper,
                mtShopPeakWindowProfileMapper,
                new ShopPeakWindowDayTypeResolver(),
                new ShopPeakWindowDetector()
        );
        ArgumentCaptor<MtShopPeakWindowProfile> captor = ArgumentCaptor.forClass(MtShopPeakWindowProfile.class);
        doAnswer(invocation -> 1).when(mtShopPeakWindowProfileMapper).upsert(captor.capture());
        when(mtOrderMapper.selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(1L), any(), any(), any()))
                .thenReturn(List.of(
                        aggregate(LocalDate.of(2026, 4, 14), 11, 2),
                        aggregate(LocalDate.of(2026, 4, 15), 11, 1)
                ));

        service.refreshProfilesForShop(1L, LocalDateTime.of(2026, 4, 15, 4, 10));

        List<MtShopPeakWindowProfile> profiles = captor.getAllValues();
        MtShopPeakWindowProfile weekdayProfile = profiles.stream()
                .filter(item -> "WEEKDAY".equals(item.getDayType()))
                .findFirst()
                .orElseThrow();
        assertEquals("INSUFFICIENT", weekdayProfile.getProfileStatus());
        assertEquals("SAMPLE_INSUFFICIENT", weekdayProfile.getUnavailableReason());
    }

    @Test
    void shouldFallbackFromWeekendToWeekdayProfileWhenWeekendProfileIsMissing() {
        ShopPeakWindowProfileService service = new ShopPeakWindowProfileService(
                mtPromotionSettingMapper,
                mtOrderMapper,
                mtShopPeakWindowProfileMapper,
                new ShopPeakWindowDayTypeResolver(),
                new ShopPeakWindowDetector()
        );
        MtShopPeakWindowProfile weekdayProfile = new MtShopPeakWindowProfile();
        weekdayProfile.setShopId(1L);
        weekdayProfile.setDayType("WEEKDAY");
        weekdayProfile.setProfileStatus("USABLE");
        weekdayProfile.setSampleDayCount(5);
        weekdayProfile.setSampleOrderCount(30);
        weekdayProfile.setPeakWindowsJson("""
                [{"startHour":11,"endHour":13,"startTime":"11:00","endTime":"13:00","orderCount":12}]
                """);

        when(mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(1L, "WEEKEND")).thenReturn(null);
        when(mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(1L, "WEEKDAY")).thenReturn(weekdayProfile);

        ShopPeakWindowProfileLookupResult result = service.lookup(1L, LocalDateTime.of(2026, 4, 18, 12, 30));

        assertEquals(ShopPeakDayType.WEEKEND, result.getDayType());
        assertEquals(ShopPeakDayType.WEEKDAY, result.getProfileDayType());
        assertEquals(ShopPeakWindowProfileLookupResult.STATUS_USABLE, result.getProfileStatus());
        assertTrue(result.isGatingEnabled());
        assertTrue(result.isPeakMatched());
        assertEquals("11:00-13:00", result.getMatchedWindow());
    }

    @Test
    void shouldFallbackFromHolidayToWeekendThenWeekday() {
        ShopPeakWindowProfileService service = new ShopPeakWindowProfileService(
                mtPromotionSettingMapper,
                mtOrderMapper,
                mtShopPeakWindowProfileMapper,
                new ShopPeakWindowDayTypeResolver() {
                    @Override
                    public ShopPeakDayType resolve(LocalDateTime slotTime) {
                        return ShopPeakDayType.HOLIDAY;
                    }

                    @Override
                    public ShopPeakDayType resolve(LocalDate date) {
                        return ShopPeakDayType.HOLIDAY;
                    }
                },
                new ShopPeakWindowDetector()
        );
        MtShopPeakWindowProfile weekdayProfile = new MtShopPeakWindowProfile();
        weekdayProfile.setShopId(1L);
        weekdayProfile.setDayType("WEEKDAY");
        weekdayProfile.setProfileStatus("USABLE");
        weekdayProfile.setSampleDayCount(6);
        weekdayProfile.setSampleOrderCount(40);
        weekdayProfile.setPeakWindowsJson("""
                [{"startHour":18,"endHour":20,"startTime":"18:00","endTime":"20:00","orderCount":18}]
                """);

        when(mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(1L, "HOLIDAY")).thenReturn(null);
        when(mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(1L, "WEEKEND")).thenReturn(null);
        when(mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(1L, "WEEKDAY")).thenReturn(weekdayProfile);

        ShopPeakWindowProfileLookupResult result = service.lookup(1L, LocalDateTime.of(2026, 10, 1, 18, 30));

        assertEquals(ShopPeakDayType.HOLIDAY, result.getDayType());
        assertEquals(ShopPeakDayType.WEEKDAY, result.getProfileDayType());
        assertEquals(ShopPeakWindowProfileLookupResult.STATUS_USABLE, result.getProfileStatus());
        assertTrue(result.isGatingEnabled());
        assertTrue(result.isPeakMatched());
        assertEquals("18:00-20:00", result.getMatchedWindow());
    }

    @Test
    void shouldUseWeekdayProfileDirectlyForAdjustedWorkday() {
        ShopPeakWindowProfileService service = new ShopPeakWindowProfileService(
                mtPromotionSettingMapper,
                mtOrderMapper,
                mtShopPeakWindowProfileMapper,
                new ShopPeakWindowDayTypeResolver(new ChinaWorkdayCalendarService(null) {
                    @Override
                    public ChinaWorkdayCalendarDayType resolve(LocalDate date) {
                        return LocalDate.of(2026, 5, 9).equals(date)
                                ? ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY
                                : null;
                    }
                }),
                new ShopPeakWindowDetector()
        );
        MtShopPeakWindowProfile weekdayProfile = new MtShopPeakWindowProfile();
        weekdayProfile.setShopId(1L);
        weekdayProfile.setDayType("WEEKDAY");
        weekdayProfile.setProfileStatus("USABLE");
        weekdayProfile.setSampleDayCount(5);
        weekdayProfile.setSampleOrderCount(30);
        weekdayProfile.setPeakWindowsJson("""
                [{"startHour":11,"endHour":13,"startTime":"11:00","endTime":"13:00","orderCount":12}]
                """);
        when(mtShopPeakWindowProfileMapper.selectByShopIdAndDayType(1L, "WEEKDAY")).thenReturn(weekdayProfile);

        ShopPeakWindowProfileLookupResult result = service.lookup(1L, LocalDateTime.of(2026, 5, 9, 12, 30));

        assertEquals(ShopPeakDayType.WEEKDAY, result.getDayType());
        assertEquals(ShopPeakDayType.WEEKDAY, result.getProfileDayType());
        assertTrue(result.isPeakMatched());
        verify(mtShopPeakWindowProfileMapper, never()).selectByShopIdAndDayType(1L, "WEEKEND");
    }

    @Test
    void shouldGroupHolidaySamplesIntoWeekendAndAdjustedWorkdayIntoWeekday() {
        ShopPeakWindowProfileService service = new ShopPeakWindowProfileService(
                mtPromotionSettingMapper,
                mtOrderMapper,
                mtShopPeakWindowProfileMapper,
                new ShopPeakWindowDayTypeResolver(new ChinaWorkdayCalendarService(null) {
                    @Override
                    public ChinaWorkdayCalendarDayType resolve(LocalDate date) {
                        if (LocalDate.of(2026, 6, 19).equals(date)) {
                            return ChinaWorkdayCalendarDayType.HOLIDAY;
                        }
                        if (LocalDate.of(2026, 5, 9).equals(date)) {
                            return ChinaWorkdayCalendarDayType.ADJUSTED_WORKDAY;
                        }
                        return null;
                    }
                }),
                new ShopPeakWindowDetector()
        );
        ArgumentCaptor<MtShopPeakWindowProfile> captor = ArgumentCaptor.forClass(MtShopPeakWindowProfile.class);
        doAnswer(invocation -> 1).when(mtShopPeakWindowProfileMapper).upsert(captor.capture());
        when(mtOrderMapper.selectHourlyAggregatesByShopIdAndOrderSendTimeBetweenExcludingStatus(eq(1L), any(), any(), any()))
                .thenReturn(List.of(
                        aggregate(LocalDate.of(2026, 6, 19), 11, 7),
                        aggregate(LocalDate.of(2026, 5, 9), 11, 5)
                ));

        service.refreshProfilesForShop(1L, LocalDateTime.of(2026, 6, 20, 4, 10));

        List<MtShopPeakWindowProfile> profiles = captor.getAllValues();
        MtShopPeakWindowProfile weekdayProfile = profiles.stream()
                .filter(item -> "WEEKDAY".equals(item.getDayType()))
                .findFirst()
                .orElseThrow();
        MtShopPeakWindowProfile weekendProfile = profiles.stream()
                .filter(item -> "WEEKEND".equals(item.getDayType()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, weekdayProfile.getSampleDayCount());
        assertEquals(5, weekdayProfile.getSampleOrderCount());
        assertEquals(1, weekendProfile.getSampleDayCount());
        assertEquals(7, weekendProfile.getSampleOrderCount());
    }

    private MtOrderHourlyAggregate aggregate(LocalDate orderDate, int hourBucket, int orderCount) {
        MtOrderHourlyAggregate aggregate = new MtOrderHourlyAggregate();
        aggregate.setOrderDate(orderDate);
        aggregate.setHourBucket(hourBucket);
        aggregate.setOrderCount(orderCount);
        aggregate.setIncomeAmount(BigDecimal.TEN);
        return aggregate;
    }
}
