package com.gogoga.backend.client;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.client.result.PeerBidPriceOpenApiResult;
import com.gogoga.backend.client.result.PromotionBidOpenApiResult;
import com.gogoga.backend.client.result.PromotionPlanStatusOpenApiResult;
import com.gogoga.backend.service.MtWaimaiAdDecryptService;
import com.gogoga.backend.service.promotion.PromotionExecutionWindow;
import com.meituan.sdk.CommonRequest;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanBid.AdUpdatePlanBidResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanStatus.AdUpdatePlanStatusRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanStatus.AdUpdatePlanStatusResponse;
import com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.PeerAdBidpriceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionOpenApiClientTest {

    private static final String MT_SUCCESS_CODE = "OP_SUCCESS";

    @Mock
    private MeituanClient meituanClient;

    @Mock
    private MtWaimaiAdDecryptService mtWaimaiAdDecryptService;

    @Test
    void shouldQueryPlanStatusAndConvertBudgetFieldsToYuan() throws Exception {
        MtPromotionOpenApiClient client = createClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setPoiId("poi-001");
        candidate.setAccessToken("token");
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 13, 10, 0);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 13, 10, 0),
                LocalDateTime.of(2026, 4, 13, 22, 0)
        );

        doReturn(buildStatusResponse(0L, 400L, 10000L, "1500-cipher"))
                .when(meituanClient).commonInvokeApi(any(CommonRequest.class), any(Type.class));
        when(mtWaimaiAdDecryptService.decryptValue(eq(1001L), eq("token"), eq("1500-cipher"), eq("推广预算已用额度"), any()))
                .thenReturn("1500");

        PromotionPlanStatusOpenApiResult result = client.queryPlanStatus(candidate, slotTime, window, "WINDOW_STATUS_CHECK");

        assertTrue(result.isSuccess());
        assertEquals(Integer.valueOf(MtPromotionOpenApiClient.STATUS_OPEN), result.getStatus());
        assertEquals(Integer.valueOf(400), result.getBid());
        assertEquals(new BigDecimal("100.00"), result.getBudget());
        assertEquals(new BigDecimal("15.00"), result.getBudgetUsed());
        assertEquals(new BigDecimal("85.00"), result.getAvailableBudget());
        assertTrue(result.getPayloadJson().contains("\"budget\":100.00"));
        assertTrue(result.getPayloadJson().contains("\"budgetUsed\":15.00"));
        assertTrue(result.getPayloadJson().contains("\"availableBudget\":85.00"));
        verify(mtWaimaiAdDecryptService).decryptValue(eq(1001L), eq("token"), eq("1500-cipher"), eq("推广预算已用额度"), any());
    }

    @Test
    void shouldReturnErrorWhenAccessTokenMissing() {
        MtPromotionOpenApiClient client = createClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);

        PromotionPlanStatusOpenApiResult result = client.queryPlanStatus(candidate, LocalDateTime.of(2026, 4, 13, 10, 0), null, "WINDOW_STATUS_CHECK");

        assertTrue(!result.isSuccess());
        assertEquals("查询推广计划状态缺少 accessToken", result.getErrorMsg());
        assertNull(result.getBudget());
        assertNull(result.getBudgetUsed());
        assertNull(result.getAvailableBudget());
    }

    @Test
    void shouldPersistStructuredGatewayFailureFieldsForStatusQuery() throws Exception {
        MtPromotionOpenApiClient client = createClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setPoiId("poi-001");
        candidate.setAccessToken("expired-token");
        MeituanResponse<Map<String, Object>> response = new MeituanResponse<>();
        response.setCode("OP_UNIAUTH_FAILED");
        response.setMsg("鉴权失败");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errMsg", "令牌已过期, 请使用refresh_token刷新 鉴权失败");
        response.setData(data);
        doReturn(response).when(meituanClient).commonInvokeApi(any(CommonRequest.class), any(Type.class));

        PromotionPlanStatusOpenApiResult result = client.queryPlanStatus(
                candidate,
                LocalDateTime.of(2026, 5, 8, 11, 0),
                null,
                "WINDOW_STATUS_CHECK"
        );

        assertTrue(!result.isSuccess());
        assertTrue(result.isAuthFailure());
        assertEquals("OP_UNIAUTH_FAILED", result.getResponseCode());
        assertEquals("查询美团推广计划状态失败：鉴权失败", result.getErrorMsg());
        assertTrue(result.getPayloadJson().contains("\"responseCode\":\"OP_UNIAUTH_FAILED\""));
        assertTrue(result.getPayloadJson().contains("\"responseMsg\":\"鉴权失败\""));
        assertTrue(result.getPayloadJson().contains("\"authFailure\":true"));
        assertTrue(result.getPayloadJson().contains("\"responseData\""));
        assertTrue(result.getPayloadJson().contains("令牌已过期"));
    }

    @Test
    void shouldMarkAuthFailureWhenStatusBusinessMessageContainsAuthFailure() throws Exception {
        MtPromotionOpenApiClient client = createClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setPoiId("poi-001");
        candidate.setAccessToken("expired-token");

        Map<String, Object> businessResult = new LinkedHashMap<>();
        businessResult.put("success", false);
        businessResult.put("code", "BUSINESS_ERROR");
        businessResult.put("message", "授权关系不存在，鉴权失败");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", businessResult);
        MeituanResponse<Map<String, Object>> response = new MeituanResponse<>();
        response.setCode(MT_SUCCESS_CODE);
        response.setMsg("success");
        response.setData(payload);
        doReturn(response).when(meituanClient).commonInvokeApi(any(CommonRequest.class), any(Type.class));

        PromotionPlanStatusOpenApiResult result = client.queryPlanStatus(
                candidate,
                LocalDateTime.of(2026, 5, 8, 11, 0),
                null,
                "WINDOW_STATUS_CHECK"
        );

        assertTrue(!result.isSuccess());
        assertTrue(result.isAuthFailure());
        assertEquals("授权关系不存在，鉴权失败", result.getErrorMsg());
        assertTrue(result.getPayloadJson().contains("\"authFailure\":true"));
    }

    @Test
    void shouldQueryPeerAverageBidAndDecryptToYuan() throws Exception {
        MtPromotionOpenApiClient client = createClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setPoiId("poi-001");
        candidate.setAccessToken("token");
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 13, 10, 0);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 13, 10, 0),
                LocalDateTime.of(2026, 4, 13, 22, 0)
        );

        when(meituanClient.invokeApi(any(com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.PeerAdBidpriceRequest.class), eq("token")))
                .thenReturn(buildPeerBidResponse("66-cipher"));
        when(mtWaimaiAdDecryptService.decryptValue(eq(1001L), eq("token"), eq("66-cipher"), eq("附近商家平均出价"), any()))
                .thenReturn("66");

        PeerBidPriceOpenApiResult result = client.queryPeerAverageBid(candidate, slotTime, window, "INITIAL_BID_FROM_PEER_AVG");

        assertTrue(result.isSuccess());
        assertEquals(new BigDecimal("0.66"), result.getAverageBidAmount());
        assertTrue(result.getPayloadJson().contains("\"averageBidAmount\":0.66"));
        verify(mtWaimaiAdDecryptService).decryptValue(eq(1001L), eq("token"), eq("66-cipher"), eq("附近商家平均出价"), any());
    }

    @Test
    void shouldRejectBidHigherThanFourYuan() throws Exception {
        MtPromotionOpenApiClient client = createClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setPoiId("poi-001");
        candidate.setAccessToken("token");
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 13, 10, 0);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 13, 10, 0),
                LocalDateTime.of(2026, 4, 13, 22, 0)
        );

        PromotionBidOpenApiResult result = client.updatePlanBid(candidate, 401, slotTime, window, "SLOT_BID_UPDATE");

        assertTrue(!result.isSuccess());
        assertEquals("更新推广计划出价不能超过4元", result.getErrorMsg());
        assertEquals(Integer.valueOf(400), result.getBid());
        assertTrue(result.getPayloadJson().contains("\"bid\":400"));
        verify(meituanClient, never()).invokeApi(any(com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanBid.AdUpdatePlanBidRequest.class), eq("token"));
    }

    @Test
    void shouldAppendMeituanResponseMsgWhenUpdateBidFails() throws Exception {
        MtPromotionOpenApiClient client = createWriteClient();
        MtPromotionAutoBidCandidate candidate = new MtPromotionAutoBidCandidate();
        candidate.setShopId(1001L);
        candidate.setPoiId("poi-001");
        candidate.setAccessToken("token");
        LocalDateTime slotTime = LocalDateTime.of(2026, 4, 13, 10, 0);
        PromotionExecutionWindow window = PromotionExecutionWindow.of(
                LocalDateTime.of(2026, 4, 13, 10, 0),
                LocalDateTime.of(2026, 4, 13, 22, 0)
        );
        MeituanResponse<AdUpdatePlanBidResponse> response = new MeituanResponse<>();
        response.setCode("1");
        response.setMsg("出价不能高于3.0元");

        when(meituanClient.invokeApi(any(com.meituan.sdk.model.wmoperNg.waimaiad.adUpdatePlanBid.AdUpdatePlanBidRequest.class), eq("token")))
                .thenReturn(response);

        PromotionBidOpenApiResult result = client.updatePlanBid(candidate, 400, slotTime, window, "SLOT_BID_UPDATE");

        assertTrue(!result.isSuccess());
        assertEquals("更新美团推广计划出价失败：出价不能高于3.0元", result.getErrorMsg());
        assertTrue(result.getPayloadJson().contains("\"responseMsg\":\"出价不能高于3.0元\""));
    }

    @Test
    void shouldPersistStructuredGatewayFailureFieldsForStatusUpdate() throws Exception {
        MtPromotionOpenApiClient client = createWriteClient();
        MeituanResponse<AdUpdatePlanStatusResponse> response = new MeituanResponse<>();
        response.setCode("OP_UNIAUTH_FAILED");
        response.setMsg("鉴权失败");

        when(meituanClient.invokeApi(any(AdUpdatePlanStatusRequest.class), eq("expired-token")))
                .thenReturn(response);

        com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult result = client.updatePlanStatus(
                1001L,
                "poi-001",
                "expired-token",
                MtPromotionOpenApiClient.STATUS_CLOSE,
                LocalDateTime.of(2026, 5, 28, 15, 0),
                null,
                "PROMOTION_RIGHT_EXPIRED"
        );

        assertTrue(!result.isSuccess());
        assertTrue(result.isAuthFailure());
        assertEquals("OP_UNIAUTH_FAILED", result.getResponseCode());
        assertEquals("鉴权失败", result.getResponseMsg());
        assertEquals("更新美团推广计划状态失败：鉴权失败（code=OP_UNIAUTH_FAILED）", result.getErrorMsg());
        assertTrue(result.getPayloadJson().contains("\"responseCode\":\"OP_UNIAUTH_FAILED\""));
        assertTrue(result.getPayloadJson().contains("\"responseMsg\":\"鉴权失败\""));
        assertTrue(result.getPayloadJson().contains("\"authFailure\":true"));
    }

    @Test
    void shouldMarkAuthFailureWhenStatusUpdateBusinessMessageContainsAuthFailure() throws Exception {
        MtPromotionOpenApiClient client = createWriteClient();
        AdUpdatePlanStatusResponse payload = new AdUpdatePlanStatusResponse();
        payload.setSuccess(false);
        payload.setCode("BUSINESS_ERROR");
        payload.setMessage("授权关系不存在，鉴权失败");
        MeituanResponse<AdUpdatePlanStatusResponse> response = new MeituanResponse<>();
        response.setCode(MT_SUCCESS_CODE);
        response.setMsg("success");
        response.setData(payload);

        when(meituanClient.invokeApi(any(AdUpdatePlanStatusRequest.class), eq("expired-token")))
                .thenReturn(response);

        com.gogoga.backend.service.promotion.PromotionPlanStatusUpdateResult result = client.updatePlanStatus(
                1001L,
                "poi-001",
                "expired-token",
                MtPromotionOpenApiClient.STATUS_CLOSE,
                LocalDateTime.of(2026, 5, 28, 15, 0),
                null,
                "PROMOTION_RIGHT_EXPIRED"
        );

        assertTrue(!result.isSuccess());
        assertTrue(result.isAuthFailure());
        assertEquals("BUSINESS_ERROR", result.getResponseCode());
        assertEquals("授权关系不存在，鉴权失败", result.getResponseMsg());
        assertTrue(result.getErrorMsg().contains("授权关系不存在，鉴权失败"));
        assertTrue(result.getPayloadJson().contains("\"authFailure\":true"));
    }

    private MeituanResponse<Map<String, Object>> buildStatusResponse(Long status, Long bid, Long budget, String budgetUsed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("code", "0");
        result.put("message", "success");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("status", status);
        payload.put("bid", bid);
        payload.put("budget", budget);
        payload.put("budgetUsed", budgetUsed);

        MeituanResponse<Map<String, Object>> response = new MeituanResponse<>();
        response.setCode(MT_SUCCESS_CODE);
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MeituanResponse<PeerAdBidpriceResponse> buildPeerBidResponse(String averagePrice) {
        com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.Result result =
                new com.meituan.sdk.model.wmoperNg.waimaiad.peerAdBidprice.Result();
        result.setSuccess(true);
        result.setCode("0");
        result.setMessage("success");

        PeerAdBidpriceResponse payload = new PeerAdBidpriceResponse();
        payload.setResult(result);
        payload.setAveragePrice(averagePrice);

        MeituanResponse<PeerAdBidpriceResponse> response = new MeituanResponse<>();
        response.setCode(MT_SUCCESS_CODE);
        response.setMsg("success");
        response.setData(payload);
        return response;
    }

    private MtPromotionOpenApiClient createClient() {
        return new MtPromotionOpenApiClient(meituanClient, mtWaimaiAdDecryptService, new MtProperties());
    }

    private MtPromotionOpenApiClient createWriteClient() {
        MtProperties properties = new MtProperties();
        properties.getPromotion().setWriteDryRun(false);
        return new MtPromotionOpenApiClient(meituanClient, mtWaimaiAdDecryptService, properties);
    }
}
