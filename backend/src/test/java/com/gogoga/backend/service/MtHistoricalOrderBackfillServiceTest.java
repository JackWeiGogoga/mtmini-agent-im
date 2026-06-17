package com.gogoga.backend.service;

import com.gogoga.backend.client.MtOrderOpenApiClient;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileLookupResult;
import com.gogoga.backend.service.promotion.ShopPeakWindowProfileService;
import com.meituan.sdk.model.wmoperNg.order.wmoperOrderQueryOrderDetail.WmoperOrderQueryOrderDetailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtHistoricalOrderBackfillServiceTest {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_INT_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Mock
    private MtOrderOpenApiClient mtOrderOpenApiClient;

    @Mock
    private MtOrderService mtOrderService;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private ShopPeakWindowProfileService shopPeakWindowProfileService;

    @InjectMocks
    private MtHistoricalOrderBackfillService mtHistoricalOrderBackfillService;

    @Test
    void shouldBackfillRecentCompletedOrdersAndRefreshPeakProfile() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");

        LocalDate yesterdayDate = dayAgo(1);
        LocalDate twoDaysAgoDate = dayAgo(2);
        String yesterday = formatDay(yesterdayDate);
        String twoDaysAgo = formatDay(twoDaysAgoDate);

        stubHasNoCompletedOrders(shop.getId());

        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", yesterday, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1001", "1002"), false));
        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", twoDaysAgo, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1003"), false));
        stubEmptyOrderListQueriesForDays(1L, "token", 3, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);

        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1001"))
                .thenReturn(Optional.of(new WmoperOrderQueryOrderDetailResponse()));
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1002"))
                .thenReturn(Optional.of(new WmoperOrderQueryOrderDetailResponse()));
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1003"))
                .thenReturn(Optional.of(new WmoperOrderQueryOrderDetailResponse()));

        mtHistoricalOrderBackfillService.backfillAfterAuthorization(shop, "token");

        verify(mtOrderService, times(3)).syncHistoricalOrderDetail(eq(shop), any(WmoperOrderQueryOrderDetailResponse.class));
        verify(shopPeakWindowProfileService).refreshProfilesForShop(eq(1L), any());
        verify(mtShopService).updateHistoricalOrderBackfillState(eq(1L), eq(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS), any());
    }

    @Test
    void shouldReturnUnlockResultWhenManualBackfillGeneratesUsableProfile() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");
        shop.setHistoricalOrderBackfillStatus(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_INIT);
        String yesterday = formatDay(dayAgo(1));

        stubLookupAvailability(false, true);
        when(mtShopService.tryMarkHistoricalOrderBackfillProcessingIfRetryable(1L)).thenReturn(true);
        stubHasNoCompletedOrders(shop.getId());
        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", yesterday, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1001"), false));
        stubEmptyOrderListQueriesForDays(1L, "token", 2, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1001"))
                .thenReturn(Optional.of(new WmoperOrderQueryOrderDetailResponse()));

        MtHistoricalOrderBackfillService.BackfillUnlockResult result =
                mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                        shop,
                        "token",
                        MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK
                );

        assertTrue(result.completed());
        assertTrue(result.autoTimeModeAvailable());
        assertEquals(MtHistoricalOrderBackfillService.UNLOCK_SUCCESS_MESSAGE, result.message());
        verify(mtOrderService).syncHistoricalOrderDetail(eq(shop), any(WmoperOrderQueryOrderDetailResponse.class));
        verify(shopPeakWindowProfileService).refreshProfilesForShop(eq(1L), any());
        verify(mtShopService).updateHistoricalOrderBackfillState(eq(1L), eq(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS), any());
    }

    @Test
    void shouldReturnInsufficientResultWhenManualBackfillCannotGenerateUsableProfile() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setHistoricalOrderBackfillStatus(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_INIT);

        stubLookupAvailability(false, false);
        when(mtShopService.tryMarkHistoricalOrderBackfillProcessingIfRetryable(1L)).thenReturn(true);
        stubHasNoCompletedOrders(shop.getId());
        stubEmptyOrderListQueriesForDays(1L, "token", 1, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);

        MtHistoricalOrderBackfillService.BackfillUnlockResult result =
                mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                        shop,
                        "token",
                        MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK
                );

        assertTrue(result.completed());
        assertFalse(result.autoTimeModeAvailable());
        assertEquals(MtHistoricalOrderBackfillService.UNLOCK_INSUFFICIENT_MESSAGE, result.message());
        verify(mtShopService).updateHistoricalOrderBackfillState(eq(1L), eq(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS), any());
    }

    @Test
    void shouldSkipManualBackfillWhenShopAlreadyHasUsableProfile() {
        MtShop shop = new MtShop();
        shop.setId(1L);

        stubLookupAvailability(true);

        MtHistoricalOrderBackfillService.BackfillUnlockResult result =
                mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                        shop,
                        "token",
                        MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK
                );

        assertFalse(result.completed());
        assertTrue(result.autoTimeModeAvailable());
        verify(mtShopService, never()).tryMarkHistoricalOrderBackfillProcessingIfRetryable(any());
        verify(mtOrderOpenApiClient, never()).queryOrderIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldSkipManualBackfillWhenClaimBackfillStateLockTimesOut() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setHistoricalOrderBackfillStatus(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_INIT);

        stubLookupAvailability(false);
        when(mtShopService.tryMarkHistoricalOrderBackfillProcessingIfRetryable(1L))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout exceeded"));
        when(mtShopService.findById(1L)).thenReturn(shop);

        MtHistoricalOrderBackfillService.BackfillUnlockResult result =
                mtHistoricalOrderBackfillService.tryBackfillRecentOrdersAndRefreshPeakProfile(
                        shop,
                        "token",
                        MtHistoricalOrderBackfillService.TRIGGER_MANUAL_UNLOCK
                );

        assertFalse(result.completed());
        assertFalse(result.autoTimeModeAvailable());
        assertEquals(MtHistoricalOrderBackfillService.UNLOCK_INSUFFICIENT_MESSAGE, result.message());
        verify(mtOrderOpenApiClient, never()).queryOrderIds(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldContinuePagingWhenOrderListHasMore() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");
        String yesterday = formatDay(dayAgo(1));

        stubHasNoCompletedOrders(shop.getId());

        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", yesterday, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1001"), true));
        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", yesterday, 8, 2, 20))
                .thenReturn(MtOrderOpenApiClient.OrderListQueryResult.empty());
        stubEmptyOrderListQueriesForDays(1L, "token", 2, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1001"))
                .thenReturn(Optional.of(new WmoperOrderQueryOrderDetailResponse()));

        mtHistoricalOrderBackfillService.backfillAfterAuthorization(shop, "token");

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mtOrderOpenApiClient, times(2)).queryOrderIds(eq(1L), eq("token"), eq(yesterday), eq(8), pageCaptor.capture(), eq(20));
        assertEquals(List.of(1, 2), pageCaptor.getAllValues());
    }

    @Test
    void shouldSkipDayWhenOrdersAlreadyExistLocallyBySendTime() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");

        LocalDate yesterdayDate = dayAgo(1);
        LocalDate twoDaysAgoDate = dayAgo(2);
        String twoDaysAgo = twoDaysAgoDate.format(DAY_INT_FORMATTER);

        when(mtOrderService.hasOrderSentOnDay(1L, yesterdayDate)).thenReturn(true);
        IntStream.rangeClosed(2, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS)
                .forEach(offset -> when(mtOrderService.hasOrderSentOnDay(1L, dayAgo(offset))).thenReturn(false));

        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", twoDaysAgo, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1002"), false));
        stubEmptyOrderListQueriesForDays(1L, "token", 3, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1002"))
                .thenReturn(Optional.of(new WmoperOrderQueryOrderDetailResponse()));

        mtHistoricalOrderBackfillService.backfillAfterAuthorization(shop, "token");

        verify(mtOrderOpenApiClient, times(0)).queryOrderIds(1L, "token", yesterdayDate.format(DAY_INT_FORMATTER), 8, 1, 20);
        verify(mtOrderOpenApiClient).queryOrderIds(1L, "token", twoDaysAgo, 8, 1, 20);
        verify(mtOrderService).syncHistoricalOrderDetail(eq(shop), any(WmoperOrderQueryOrderDetailResponse.class));
    }

    @Test
    void shouldMarkFailedStateWhenOrderBackfillThrows() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");

        String yesterday = formatDay(dayAgo(1));

        stubHasNoCompletedOrders(shop.getId());

        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", yesterday, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1001"), false));
        stubEmptyOrderListQueriesForDays(1L, "token", 2, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1001"))
                .thenThrow(new IllegalStateException("boom"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> mtHistoricalOrderBackfillService.backfillAfterAuthorization(shop, "token"));

        verify(mtShopService).updateHistoricalOrderBackfillState(eq(1L), eq(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_FAILED), any());
    }

    @Test
    void shouldRetryHistoricalOrderPersistenceWhenDeadlockHappens() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");
        String yesterday = formatDay(dayAgo(1));
        WmoperOrderQueryOrderDetailResponse detail = new WmoperOrderQueryOrderDetailResponse();

        stubHasNoCompletedOrders(shop.getId());
        when(mtOrderOpenApiClient.queryOrderIds(1L, "token", yesterday, 8, 1, 20))
                .thenReturn(new MtOrderOpenApiClient.OrderListQueryResult(List.of("1001"), false));
        stubEmptyOrderListQueriesForDays(1L, "token", 2, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS);
        when(mtOrderOpenApiClient.queryOrderDetail(1L, "token", "1001"))
                .thenReturn(Optional.of(detail));
        doThrow(new CannotAcquireLockException("deadlock"))
                .doNothing()
                .when(mtOrderService)
                .syncHistoricalOrderDetail(shop, detail);

        mtHistoricalOrderBackfillService.backfillAfterAuthorization(shop, "token");

        verify(mtOrderService, times(2)).syncHistoricalOrderDetail(shop, detail);
        verify(mtShopService).updateHistoricalOrderBackfillState(eq(1L), eq(MtShopService.HISTORICAL_ORDER_BACKFILL_STATUS_SUCCESS), any());
    }

    private void stubHasNoCompletedOrders(Long shopId) {
        IntStream.rangeClosed(1, MtHistoricalOrderBackfillService.AUTH_LOOKBACK_DAYS)
                .forEach(offset -> when(mtOrderService.hasOrderSentOnDay(shopId, dayAgo(offset))).thenReturn(false));
    }

    private void stubEmptyOrderListQueriesForDays(Long shopId, String accessToken, int startOffset, int endOffset) {
        IntStream.rangeClosed(startOffset, endOffset)
                .mapToObj(offset -> Map.entry(offset, formatDay(dayAgo(offset))))
                .forEach(entry -> when(mtOrderOpenApiClient.queryOrderIds(shopId, accessToken, entry.getValue(), 8, 1, 20))
                        .thenReturn(MtOrderOpenApiClient.OrderListQueryResult.empty()));
    }

    private void stubLookupAvailability(boolean... availability) {
        ShopPeakWindowProfileLookupResult[] results = new ShopPeakWindowProfileLookupResult[availability.length];
        for (int i = 0; i < availability.length; i++) {
            ShopPeakWindowProfileLookupResult result = new ShopPeakWindowProfileLookupResult();
            result.setProfileStatus(availability[i]
                    ? ShopPeakWindowProfileLookupResult.STATUS_USABLE
                    : ShopPeakWindowProfileLookupResult.STATUS_UNAVAILABLE);
            result.setUnavailableReason(availability[i] ? null : "SAMPLE_INSUFFICIENT");
            results[i] = result;
        }
        when(shopPeakWindowProfileService.lookup(eq(1L), any())).thenReturn(results[0], java.util.Arrays.copyOfRange(results, 1, results.length));
    }

    private LocalDate dayAgo(int offset) {
        return LocalDate.now(DEFAULT_ZONE_ID).minusDays(offset);
    }

    private String formatDay(LocalDate day) {
        return day.format(DAY_INT_FORMATTER);
    }
}
