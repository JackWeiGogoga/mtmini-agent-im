package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.constant.YouzanConstant;
import com.gogoga.backend.entity.youzan.YzAppAuth;
import com.gogoga.backend.entity.youzan.YzAppOrder;
import com.gogoga.backend.entity.youzan.YzCallbackEvent;
import com.gogoga.backend.entity.youzan.YzShop;
import com.gogoga.backend.mapper.YzAppAuthMapper;
import com.gogoga.backend.mapper.YzAppOrderMapper;
import com.gogoga.backend.mapper.YzCallbackEventMapper;
import com.gogoga.backend.mapper.YzShopMapper;
import com.gogoga.backend.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 有赞回调处理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YouzanCallbackService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();

    private final YouzanMessageDecryptService youzanMessageDecryptService;
    private final YouzanTokenService youzanTokenService;
    private final YzCallbackEventMapper yzCallbackEventMapper;
    private final YzShopMapper yzShopMapper;
    private final YzAppOrderMapper yzAppOrderMapper;
    private final YzAppAuthMapper yzAppAuthMapper;

    public void handleCallback(HttpServletRequest request) {
        String rawMessage = extractRawQueryParam(request.getQueryString(), "message");
        String code = StringUtils.trimToNull(request.getParameter("code"));
        YzCallbackEvent event = createEvent(request, rawMessage, code);
        yzCallbackEventMapper.insert(event);

        if (StringUtils.isNotBlank(rawMessage)) {
            handleMessage(event, rawMessage);
            return;
        }
        if (StringUtils.isNotBlank(code)) {
            handleCode(event, code);
            return;
        }
        markUnsupported(event, "unsupported Youzan callback payload");
    }

    private void handleMessage(YzCallbackEvent event, String rawMessage) {
        try {
            String payloadJson = youzanMessageDecryptService.decrypt(rawMessage);
            JsonNode payload = JsonUtil.readTree(payloadJson);
            if (payload == null || !payload.isObject()) {
                throw new IllegalArgumentException("decrypted message is not a JSON object");
            }
            String type = text(payload, "type");
            event.setEventType(StringUtils.defaultIfBlank(type, YouzanConstant.EVENT_TYPE_UNSUPPORTED));
            event.setDecodedPayloadJson(payloadJson);
            yzCallbackEventMapper.updateById(event);

            if (StringUtils.equals(type, YouzanConstant.EVENT_TYPE_APP_SUBSCRIBE)) {
                saveSubscribeMessage(event, payload, payloadJson);
                markSuccess(event);
                return;
            }
            if (StringUtils.equals(type, YouzanConstant.EVENT_TYPE_APP_AUTH)) {
                saveAuthMessage(event, payload, payloadJson);
                markSuccess(event);
                return;
            }
            markUnsupported(event, "unsupported Youzan message type: " + type);
        } catch (Exception e) {
            log.warn("处理有赞 message 回调失败，eventId={}", event.getId(), e);
            markFailed(event, StringUtils.left(e.getMessage(), 1000));
        }
    }

    private void handleCode(YzCallbackEvent event, String code) {
        event.setEventType(YouzanConstant.EVENT_TYPE_CODE);
        yzCallbackEventMapper.updateById(event);
        try {
            youzanTokenService.exchangeCode(code);
            markSuccess(event);
        } catch (Exception e) {
            log.warn("处理有赞授权 code 回调失败，eventId={}", event.getId(), e);
            markFailed(event, StringUtils.left(e.getMessage(), 1000));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSubscribeMessage(YzCallbackEvent event, JsonNode payload, String payloadJson) {
        YzShop shop = upsertShop(payload);
        YzAppOrder existing = yzAppOrderMapper.selectByOrderNo(text(payload, "orderNo"));
        LocalDateTime now = LocalDateTime.now();
        YzAppOrder target = existing == null ? new YzAppOrder() : existing;
        if (existing == null) {
            target.setCreateTime(now);
            target.setUseStatus(YouzanConstant.ORDER_USE_STATUS_UNUSED);
        }
        target.setOrderNo(text(payload, "orderNo"));
        target.setYzShopId(shop == null ? null : shop.getId());
        target.setKdtId(longValue(payload, "kdtId"));
        target.setRootKdtId(longValue(payload, "rootKdtId"));
        target.setAppId(longValue(payload, "appId"));
        target.setBuyerId(longValue(payload, "buyerId"));
        target.setBuyerPhone(text(payload, "buyerPhone"));
        target.setOpenId(text(payload, "openId"));
        target.setShopName(text(payload, "shopName"));
        target.setShopDisplayNo(text(payload, "shopDisplayNo", "shopDisPlayNo"));
        target.setShopType(text(payload, "shopType"));
        target.setShopRole(text(payload, "shopRole"));
        target.setSkuVersionText(text(payload, "skuVersionText"));
        target.setSkuIntervalText(text(payload, "skuIntervalText"));
        target.setPrice(intValue(payload, "price"));
        target.setStatus(intValue(payload, "status"));
        target.setPayTime(longValue(payload, "payTime"));
        target.setPayTimeAt(toDateTime(target.getPayTime()));
        target.setEnv(text(payload, "env"));
        target.setRawPayloadJson(payloadJson);
        target.setCallbackEventId(event.getId());
        target.setUpdateTime(now);
        if (existing == null) {
            yzAppOrderMapper.insert(target);
        } else {
            yzAppOrderMapper.updateById(target);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAuthMessage(YzCallbackEvent event, JsonNode payload, String payloadJson) {
        YzShop shop = upsertShop(payload);
        String orderNo = text(payload, "orderNo");
        Long kdtId = longValue(payload, "kdtId");
        Long appId = longValue(payload, "appId");
        YzAppAuth existing = yzAppAuthMapper.selectByNaturalKey(orderNo, kdtId, appId);
        LocalDateTime now = LocalDateTime.now();
        YzAppAuth target = existing == null ? new YzAppAuth() : existing;
        if (existing == null) {
            target.setCreateTime(now);
        }
        target.setOrderNo(orderNo);
        target.setYzShopId(shop == null ? null : shop.getId());
        target.setKdtId(kdtId);
        target.setAppId(appId);
        target.setBuyerId(longValue(payload, "buyerId"));
        target.setBuyerPhone(text(payload, "buyerPhone"));
        target.setOpenId(text(payload, "openId"));
        target.setShopDisplayNo(text(payload, "shopDisplayNo", "shopDisPlayNo"));
        target.setShopType(text(payload, "shopType"));
        target.setShopRole(text(payload, "shopRole"));
        target.setEffectTime(longValue(payload, "effectTime"));
        target.setEffectTimeAt(toDateTime(target.getEffectTime()));
        target.setExpireTime(longValue(payload, "expireTime"));
        target.setExpireTimeAt(toDateTime(target.getExpireTime()));
        target.setPayTime(longValue(payload, "payTime"));
        target.setPayTimeAt(toDateTime(target.getPayTime()));
        target.setEnv(text(payload, "env"));
        target.setRawPayloadJson(payloadJson);
        target.setCallbackEventId(event.getId());
        target.setUpdateTime(now);
        if (existing == null) {
            yzAppAuthMapper.insert(target);
        } else {
            yzAppAuthMapper.updateById(target);
        }

        YzAppOrder order = yzAppOrderMapper.selectByOrderNo(orderNo);
        if (order != null && order.getYzShopId() == null && shop != null) {
            order.setYzShopId(shop.getId());
            order.setUpdateTime(now);
            yzAppOrderMapper.updateById(order);
        }
    }

    private YzShop upsertShop(JsonNode payload) {
        Long kdtId = longValue(payload, "kdtId");
        if (kdtId == null) {
            return null;
        }
        YzShop existing = yzShopMapper.selectByKdtId(kdtId);
        LocalDateTime now = LocalDateTime.now();
        YzShop target = existing == null ? new YzShop() : existing;
        if (existing == null) {
            target.setKdtId(kdtId);
            target.setCreateTime(now);
        }
        target.setRootKdtId(firstNonNull(longValue(payload, "rootKdtId"), target.getRootKdtId()));
        target.setAppId(firstNonNull(longValue(payload, "appId"), target.getAppId()));
        target.setShopName(firstNonBlank(text(payload, "shopName"), target.getShopName()));
        target.setShopDisplayNo(firstNonBlank(text(payload, "shopDisplayNo", "shopDisPlayNo"), target.getShopDisplayNo()));
        target.setShopType(firstNonBlank(text(payload, "shopType"), target.getShopType()));
        target.setShopRole(firstNonBlank(text(payload, "shopRole"), target.getShopRole()));
        target.setBuyerId(firstNonNull(longValue(payload, "buyerId"), target.getBuyerId()));
        target.setBuyerPhone(firstNonBlank(text(payload, "buyerPhone"), target.getBuyerPhone()));
        target.setOpenId(firstNonBlank(text(payload, "openId"), target.getOpenId()));
        target.setEnv(firstNonBlank(text(payload, "env"), target.getEnv()));
        target.setUpdateTime(now);
        if (existing == null) {
            yzShopMapper.insert(target);
        } else {
            yzShopMapper.updateById(target);
        }
        youzanTokenService.linkShopByKdtId(kdtId, target.getId());
        return target;
    }

    private YzCallbackEvent createEvent(HttpServletRequest request, String rawMessage, String code) {
        LocalDateTime now = LocalDateTime.now();
        YzCallbackEvent event = new YzCallbackEvent();
        event.setRequestUri(request.getRequestURI());
        event.setRawQueryString(request.getQueryString());
        event.setMessageCiphertext(rawMessage);
        event.setAuthCode(code);
        event.setProcessStatus(YouzanConstant.PROCESS_STATUS_INIT);
        event.setClientIp(resolveClientIp(request));
        event.setUserAgent(StringUtils.left(request.getHeader("User-Agent"), 500));
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    private void markSuccess(YzCallbackEvent event) {
        event.setProcessStatus(YouzanConstant.PROCESS_STATUS_SUCCESS);
        event.setErrorMsg(null);
        event.setUpdateTime(LocalDateTime.now());
        yzCallbackEventMapper.updateById(event);
    }

    private void markFailed(YzCallbackEvent event, String errorMsg) {
        event.setProcessStatus(YouzanConstant.PROCESS_STATUS_FAILED);
        event.setErrorMsg(StringUtils.defaultIfBlank(errorMsg, "处理有赞回调失败"));
        event.setUpdateTime(LocalDateTime.now());
        yzCallbackEventMapper.updateById(event);
    }

    private void markUnsupported(YzCallbackEvent event, String errorMsg) {
        event.setEventType(YouzanConstant.EVENT_TYPE_UNSUPPORTED);
        event.setProcessStatus(YouzanConstant.PROCESS_STATUS_UNSUPPORTED);
        event.setErrorMsg(StringUtils.left(errorMsg, 1000));
        event.setUpdateTime(LocalDateTime.now());
        yzCallbackEventMapper.updateById(event);
    }

    private String extractRawQueryParam(String rawQuery, String name) {
        if (StringUtils.isBlank(rawQuery) || StringUtils.isBlank(name)) {
            return null;
        }
        String prefix = name + "=";
        String[] parts = rawQuery.split("&");
        for (String part : parts) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            return StringUtils.left(forwarded.split(",")[0].trim(), 128);
        }
        return StringUtils.left(request.getRemoteAddr(), 128);
    }

    private LocalDateTime toDateTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), DEFAULT_ZONE_ID);
    }

    private String text(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private Long longValue(JsonNode node, String name) {
        return longValue(node, new String[]{name});
    }

    private Long longValue(JsonNode node, String... names) {
        String value = text(node, names);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(JsonNode node, String name) {
        Long value = longValue(node, name);
        return value == null ? null : value.intValue();
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.isNotBlank(first) ? first : fallback;
    }

    private <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }
}
