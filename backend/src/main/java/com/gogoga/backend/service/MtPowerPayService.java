package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerPaymentCreateResultDTO;
import com.gogoga.backend.dto.PowerPaymentQueryResultDTO;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.PowerRechargeStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.util.WechatPayCryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 算力微信支付服务
 *
 * @author system
 * @since 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPowerPayService {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String WECHAT_API_BASE_URL = "https://api.mch.weixin.qq.com";
    private static final String PAY_CHANNEL_WECHAT_H5 = "WECHAT_H5";
    private static final long NOTIFY_TIMESTAMP_TOLERANCE_SECONDS = 300L;

    private final PowerProperties powerProperties;
    private final OkHttpClient okHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * 创建微信支付订单
     *
     * @param recharge 充值单
     * @param shop 店铺信息
     * @param clientIp 客户端 IP
     * @return 支付结果
     */
    public PowerPaymentCreateResultDTO createRechargeOrder(MtPowerRecharge recharge, MtShop shop, String clientIp) {
        PowerPaymentCreateResultDTO result = new PowerPaymentCreateResultDTO();
        result.setPayChannel(PAY_CHANNEL_WECHAT_H5);
        result.setPaymentOrderNo(recharge.getPaymentOrderNo());

        if (powerProperties.isWechatPayMockEffective()) {
            result.setMockEnabled(true);
            Map<String, Object> payload = new HashMap<>();
            payload.put("rechargeNo", recharge.getRechargeNo());
            payload.put("paymentOrderNo", recharge.getPaymentOrderNo());
            payload.put("mock", true);
            result.setRawPayload(JsonUtil.toJson(payload));
            return result;
        }

        ensureWechatPayConfigured();

        String requestBody = buildH5OrderRequest(recharge, shop, clientIp);
        String canonicalUrl = "/v3/pay/transactions/h5";
        Request request = new Request.Builder()
                .url(WECHAT_API_BASE_URL + canonicalUrl)
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", buildAuthorization("POST", canonicalUrl, requestBody))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.error("微信 H5 下单失败，rechargeNo={}, code={}, body={}", recharge.getRechargeNo(), response.code(), responseBody);
                throw new BusinessException(500, "微信支付下单失败");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String mwebUrl = text(root, "h5_url");
            if (StringUtils.isBlank(mwebUrl)) {
                throw new BusinessException(500, "微信支付下单结果缺少 h5_url");
            }

            result.setMockEnabled(false);
            result.setMwebUrl(mwebUrl);
            result.setRawPayload(responseBody);
            return result;
        } catch (IOException e) {
            throw new BusinessException(500, "调用微信支付下单失败", e);
        }
    }

    /**
     * 查询支付订单状态
     *
     * @param recharge 充值单
     * @return 查询结果
     */
    public PowerPaymentQueryResultDTO queryRechargeOrder(MtPowerRecharge recharge) {
        PowerPaymentQueryResultDTO result = new PowerPaymentQueryResultDTO();
        result.setRechargeNo(recharge.getRechargeNo());
        result.setPaymentOrderNo(recharge.getPaymentOrderNo());

        if (powerProperties.isWechatPayMockEffective()) {
            result.setPayStatus(recharge.getPayStatus());
            result.setSuccess(PowerRechargeStatusEnum.isSuccess(recharge.getPayStatus()));
            result.setFinished(isFinishedStatus(recharge.getPayStatus()));
            result.setTransactionId(recharge.getTransactionId());
            result.setRawPayload(recharge.getNotifyPayload());
            return result;
        }

        ensureWechatPayConfigured();
        String canonicalUrl = "/v3/pay/transactions/out-trade-no/" + recharge.getPaymentOrderNo()
                + "?mchid=" + powerProperties.getWechatPay().getMchId();
        Request request = new Request.Builder()
                .url(WECHAT_API_BASE_URL + canonicalUrl)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", buildAuthorization("GET", canonicalUrl, ""))
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                log.warn("微信支付查单失败，rechargeNo={}, code={}, body={}", recharge.getRechargeNo(), response.code(), responseBody);
                throw new BusinessException(500, "微信支付查单失败");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String tradeState = text(root, "trade_state");
            JsonNode amount = root.get("amount");
            result.setAppId(text(root, "appid"));
            result.setMchId(text(root, "mchid"));
            result.setTradeType(text(root, "trade_type"));
            result.setTradeState(tradeState);
            result.setPaymentOrderNo(firstNonBlank(text(root, "out_trade_no"), recharge.getPaymentOrderNo()));
            result.setTransactionId(text(root, "transaction_id"));
            fillAmountFields(result, amount);
            result.setPayStatus(toRechargeStatus(tradeState));
            result.setSuccess(PowerRechargeStatusEnum.isSuccess(result.getPayStatus()));
            result.setFinished(isFinishedStatus(result.getPayStatus()));
            result.setRawPayload(responseBody);
            return result;
        } catch (IOException e) {
            throw new BusinessException(500, "调用微信支付查单失败", e);
        }
    }

    /**
     * 解析微信回调
     *
     * @param request 请求对象
     * @param body 请求体
     * @return 解析结果
     */
    public PowerPaymentQueryResultDTO parseNotify(HttpServletRequest request, String body) {
        if (powerProperties.isWechatPayMockEffective()) {
            JsonNode root = JsonUtil.readTree(body);
            if (root == null) {
                throw new BusinessException(400, "mock 支付通知体不能为空");
            }

            PowerPaymentQueryResultDTO result = new PowerPaymentQueryResultDTO();
            result.setRechargeNo(text(root, "rechargeNo"));
            result.setPaymentOrderNo(text(root, "paymentOrderNo"));
            result.setTransactionId(firstNonBlank(text(root, "transactionId"), "MOCK-" + UUID.randomUUID().toString().replace("-", "")));
            result.setPayStatus(Boolean.parseBoolean(firstNonBlank(text(root, "success"), "true"))
                    ? PowerRechargeStatusEnum.SUCCESS.getCode()
                    : PowerRechargeStatusEnum.FAILED.getCode());
            result.setSuccess(PowerRechargeStatusEnum.isSuccess(result.getPayStatus()));
            result.setFinished(true);
            result.setRawPayload(body);
            return result;
        }

        ensureWechatPayConfigured();
        verifyNotifySignatureIfNecessary(request, body);

        JsonNode root = JsonUtil.readTree(body);
        if (root == null) {
            throw new BusinessException(400, "微信支付通知体不能为空");
        }
        if (!StringUtils.equals("TRANSACTION.SUCCESS", text(root, "event_type"))) {
            throw new BusinessException(400, "微信支付通知类型非法");
        }

        JsonNode resource = root.get("resource");
        if (resource == null || resource.isNull()) {
            throw new BusinessException(400, "微信支付通知缺少 resource");
        }
        if (!StringUtils.equals("encrypt-resource", text(root, "resource_type"))
                || !StringUtils.equals("transaction", text(resource, "original_type"))
                || !StringUtils.equals("AEAD_AES_256_GCM", text(resource, "algorithm"))) {
            throw new BusinessException(400, "微信支付通知资源类型非法");
        }

        String plainText = WechatPayCryptoUtil.decryptResource(
                powerProperties.getWechatPay().getApiV3Key(),
                text(resource, "associated_data"),
                text(resource, "nonce"),
                text(resource, "ciphertext")
        );
        JsonNode payload = JsonUtil.readTree(plainText);
        if (payload == null) {
            throw new BusinessException(400, "微信支付通知解密结果为空");
        }

        PowerPaymentQueryResultDTO result = new PowerPaymentQueryResultDTO();
        JsonNode amount = payload.get("amount");
        result.setAppId(text(payload, "appid"));
        result.setMchId(text(payload, "mchid"));
        result.setTradeType(text(payload, "trade_type"));
        result.setTradeState(text(payload, "trade_state"));
        result.setRechargeNo(text(payload, "attach"));
        result.setPaymentOrderNo(text(payload, "out_trade_no"));
        result.setTransactionId(text(payload, "transaction_id"));
        fillAmountFields(result, amount);
        result.setPayStatus(toRechargeStatus(result.getTradeState()));
        result.setSuccess(PowerRechargeStatusEnum.isSuccess(result.getPayStatus()));
        result.setFinished(isFinishedStatus(result.getPayStatus()));
        result.setRawPayload(plainText);
        return result;
    }

    private void verifyNotifySignatureIfNecessary(HttpServletRequest request, String body) {
        String publicKeyPath = powerProperties.getWechatPay().getPlatformPublicKeyPath();
        if (StringUtils.isBlank(publicKeyPath)) {
            throw new BusinessException(500, "微信支付平台公钥未配置，拒绝处理回调");
        }

        String timestamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signature = request.getHeader("Wechatpay-Signature");
        String serial = request.getHeader("Wechatpay-Serial");
        if (StringUtils.isAnyBlank(timestamp, nonce, signature, serial)) {
            throw new BusinessException(400, "微信支付回调缺少验签头信息");
        }
        validateNotifyTimestamp(timestamp);

        String content = timestamp + "\n" + nonce + "\n" + body + "\n";
        if (!WechatPayCryptoUtil.verify(content, signature, publicKeyPath)) {
            throw new BusinessException(400, "微信支付回调验签失败");
        }
    }

    private void validateNotifyTimestamp(String timestamp) {
        long notifyEpochSecond;
        try {
            notifyEpochSecond = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "微信支付回调时间戳非法", e);
        }
        long delta = Math.abs(Instant.now().getEpochSecond() - notifyEpochSecond);
        if (delta > NOTIFY_TIMESTAMP_TOLERANCE_SECONDS) {
            throw new BusinessException(400, "微信支付回调时间戳超出允许范围");
        }
    }

    private String buildH5OrderRequest(MtPowerRecharge recharge, MtShop shop, String clientIp) {
        Map<String, Object> body = new HashMap<>();
        body.put("appid", powerProperties.getWechatPay().getAppId());
        body.put("mchid", powerProperties.getWechatPay().getMchId());
        body.put("description", "算力充值-" + firstNonBlank(shop.getPoiName(), shop.getName(), String.valueOf(shop.getId())));
        body.put("out_trade_no", recharge.getPaymentOrderNo());
        body.put("notify_url", powerProperties.getWechatPay().getNotifyUrl());
        body.put("attach", recharge.getRechargeNo());

        Map<String, Object> amount = new HashMap<>();
        amount.put("total", recharge.getTotalAmountFen());
        amount.put("currency", "CNY");
        body.put("amount", amount);

        Map<String, Object> h5Info = new HashMap<>();
        h5Info.put("type", "Wap");
        // We keep legacy config keys for compatibility, but V3 H5下单 uses app_url/app_name.
        h5Info.put("app_url", powerProperties.getWechatPay().getWapUrl());
        h5Info.put("app_name", powerProperties.getWechatPay().getWapName());

        Map<String, Object> sceneInfo = new HashMap<>();
        sceneInfo.put("payer_client_ip", StringUtils.defaultIfBlank(clientIp, "127.0.0.1"));
        sceneInfo.put("h5_info", h5Info);
        body.put("scene_info", sceneInfo);
        return JsonUtil.toJson(body);
    }

    private String buildAuthorization(String method, String canonicalUrl, String body) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String message = method + "\n" + canonicalUrl + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = WechatPayCryptoUtil.sign(message, powerProperties.getWechatPay().getPrivateKeyPath());
        return "WECHATPAY2-SHA256-RSA2048 mchid=\""
                + powerProperties.getWechatPay().getMchId()
                + "\",nonce_str=\""
                + nonce
                + "\",signature=\""
                + signature
                + "\",timestamp=\""
                + timestamp
                + "\",serial_no=\""
                + powerProperties.getWechatPay().getMerchantSerialNumber()
                + "\"";
    }

    private void ensureWechatPayConfigured() {
        PowerProperties.WechatPay wechatPay = powerProperties.getWechatPay();
        if (StringUtils.isAnyBlank(
                wechatPay.getMchId(),
                wechatPay.getAppId(),
                wechatPay.getPrivateKeyPath(),
                wechatPay.getMerchantSerialNumber(),
                wechatPay.getApiV3Key(),
                wechatPay.getPlatformPublicKeyPath(),
                wechatPay.getNotifyUrl(),
                wechatPay.getWapName(),
                wechatPay.getWapUrl())) {
            throw new BusinessException(500, "微信支付配置不完整，请检查 power.wechat-pay 配置");
        }
    }

    private Integer toRechargeStatus(String tradeState) {
        if (StringUtils.equalsIgnoreCase(tradeState, "SUCCESS")) {
            return PowerRechargeStatusEnum.SUCCESS.getCode();
        }
        if (StringUtils.equalsAnyIgnoreCase(tradeState, "NOTPAY", "USERPAYING")) {
            return PowerRechargeStatusEnum.PAYING.getCode();
        }
        if (StringUtils.equalsIgnoreCase(tradeState, "CLOSED")) {
            return PowerRechargeStatusEnum.CLOSED.getCode();
        }
        if (StringUtils.equalsAnyIgnoreCase(tradeState, "PAYERROR", "REVOKED")) {
            return PowerRechargeStatusEnum.FAILED.getCode();
        }
        return PowerRechargeStatusEnum.CREATED.getCode();
    }

    private boolean isFinishedStatus(Integer payStatus) {
        return payStatus != null && (PowerRechargeStatusEnum.isSuccess(payStatus)
                || PowerRechargeStatusEnum.FAILED.getCode() == payStatus
                || PowerRechargeStatusEnum.CLOSED.getCode() == payStatus);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return StringUtils.isBlank(value) ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private void fillAmountFields(PowerPaymentQueryResultDTO result, JsonNode amount) {
        if (amount == null || amount.isNull()) {
            return;
        }
        result.setTotalAmountFen(firstNonNull(
                integer(amount, "total"),
                integer(amount, "total_amount")
        ));
        result.setPayerAmountFen(firstNonNull(
                integer(amount, "payer_total"),
                integer(amount, "payer_amount")
        ));
        result.setCurrency(firstNonBlank(text(amount, "currency"), text(amount, "total_currency")));
        result.setPayerCurrency(text(amount, "payer_currency"));
    }

    private Integer integer(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull() || !child.canConvertToInt()) {
            return null;
        }
        return child.asInt();
    }

    private Integer firstNonNull(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
