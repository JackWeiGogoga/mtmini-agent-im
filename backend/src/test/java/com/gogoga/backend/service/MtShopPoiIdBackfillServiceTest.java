package com.gogoga.backend.service;

import com.gogoga.backend.client.MtOrderOpenApiClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtShopPoiIdBackfillServiceTest {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_INT_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    @Mock
    private MtOrderOpenApiClient mtOrderOpenApiClient;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;

    @InjectMocks
    private MtShopPoiIdBackfillService mtShopPoiIdBackfillService;

    @Test
    void shouldBackfillPoiIdAfterAuthorizationWhenOrderFoundWithinLookbackWindow() {
        MtShop shop = buildShop(null);
        MtShop updatedShop = buildShop("poi-001");
        String yesterday = LocalDate.now(DEFAULT_ZONE_ID).minusDays(1).format(DAY_INT_FORMATTER);
        String twoDaysAgo = LocalDate.now(DEFAULT_ZONE_ID).minusDays(2).format(DAY_INT_FORMATTER);

        when(mtOrderOpenApiClient.queryFirstOrderId(1L, "token", yesterday, -1, 1, 1)).thenReturn(Optional.empty());
        when(mtOrderOpenApiClient.queryFirstOrderId(1L, "token", twoDaysAgo, -1, 1, 1)).thenReturn(Optional.of("order-001"));
        when(mtOrderOpenApiClient.queryPoiIdByOrderId(1L, "token", "order-001")).thenReturn(Optional.of("poi-001"));
        when(mtShopService.updatePoiIdIfMissing(shop, "poi-001")).thenReturn(updatedShop);

        MtShop result = mtShopPoiIdBackfillService.tryBackfillAfterAuthorization(shop, "token");

        assertSame(updatedShop, result);
        ArgumentCaptor<String> dayCaptor = ArgumentCaptor.forClass(String.class);
        verify(mtOrderOpenApiClient, times(2)).queryFirstOrderId(eq(1L), eq("token"), dayCaptor.capture(), eq(-1), eq(1), eq(1));
        assertEquals(yesterday, dayCaptor.getAllValues().get(0));
        assertEquals(twoDaysAgo, dayCaptor.getAllValues().get(1));
        verify(mtOrderOpenApiClient).queryPoiIdByOrderId(1L, "token", "order-001");
        verify(mtShopService).updatePoiIdIfMissing(shop, "poi-001");
    }

    @Test
    void shouldStopAfterLookbackWindowMissWhenAuthorizationBackfillHasNoOrderSource() {
        MtShop shop = buildShop(null);
        when(mtOrderOpenApiClient.queryFirstOrderId(eq(1L), eq("token"), org.mockito.ArgumentMatchers.anyString(), anyInt(), anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        MtShop result = mtShopPoiIdBackfillService.tryBackfillAfterAuthorization(shop, "token");

        assertSame(shop, result);
        verify(mtOrderOpenApiClient, times(MtShopPoiIdBackfillService.AUTH_LOOKBACK_DAYS))
                .queryFirstOrderId(eq(1L), eq("token"), org.mockito.ArgumentMatchers.anyString(), eq(-1), eq(1), eq(1));
        verify(mtOrderOpenApiClient, never()).queryPoiIdByOrderId(eq(1L), eq("token"), org.mockito.ArgumentMatchers.anyString());
        verify(mtShopService, never()).updatePoiIdIfMissing(eq(shop), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldSkipAuthorizationBackfillWhenPoiIdAlreadyExists() {
        MtShop shop = buildShop("poi-existing");

        MtShop result = mtShopPoiIdBackfillService.tryBackfillAfterAuthorization(shop, "token");

        assertSame(shop, result);
        verify(mtOrderOpenApiClient, never()).queryFirstOrderId(eq(1L), eq("token"), org.mockito.ArgumentMatchers.anyString(), anyInt(), anyInt(), anyInt());
        verify(mtShopService, never()).updatePoiIdIfMissing(eq(shop), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldBackfillPoiIdFromOrderCallback() {
        MtShop shop = buildShop(null);
        MtShop updatedShop = buildShop("poi-009");
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setShopId(shop.getId());
        accessToken.setAccessToken("token");

        when(mtAccessTokenMapper.selectByShopId(shop.getId())).thenReturn(accessToken);
        when(mtOrderOpenApiClient.queryPoiIdByOrderId(1L, "token", "order-009")).thenReturn(Optional.of("poi-009"));
        when(mtShopService.updatePoiIdIfMissing(shop, "poi-009")).thenReturn(updatedShop);

        MtShop result = mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-009");

        assertSame(updatedShop, result);
        verify(mtAccessTokenMapper).selectByShopId(shop.getId());
        verify(mtOrderOpenApiClient).queryPoiIdByOrderId(1L, "token", "order-009");
        verify(mtShopService).updatePoiIdIfMissing(shop, "poi-009");
    }

    @Test
    void shouldTolerateOrderDetailLookupFailureDuringOrderCallbackBackfill() {
        MtShop shop = buildShop(null);
        MtAccessToken accessToken = new MtAccessToken();
        accessToken.setShopId(shop.getId());
        accessToken.setAccessToken("token");

        when(mtAccessTokenMapper.selectByShopId(shop.getId())).thenReturn(accessToken);
        when(mtOrderOpenApiClient.queryPoiIdByOrderId(1L, "token", "order-010")).thenReturn(Optional.empty());

        MtShop result = mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-010");

        assertSame(shop, result);
        assertNull(result.getPoiId());
        verify(mtShopService, never()).updatePoiIdIfMissing(eq(shop), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldSkipOrderCallbackBackfillWhenPoiIdAlreadyExists() {
        MtShop shop = buildShop("poi-existing");

        MtShop result = mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, "order-011");

        assertSame(shop, result);
        verify(mtAccessTokenMapper, never()).selectByShopId(shop.getId());
        verify(mtOrderOpenApiClient, never()).queryPoiIdByOrderId(eq(1L), eq("token"), org.mockito.ArgumentMatchers.anyString());
    }

    private MtShop buildShop(String poiId) {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setEpoiId("epoi-001");
        shop.setPoiId(poiId);
        return shop;
    }
}
