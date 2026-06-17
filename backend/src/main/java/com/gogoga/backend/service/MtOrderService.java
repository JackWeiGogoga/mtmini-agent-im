package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gogoga.backend.entity.mt.MtOrder;
import com.gogoga.backend.entity.mt.MtOrderDetail;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtOrderStatusEnum;
import com.gogoga.backend.mapper.MtOrderDetailMapper;
import com.gogoga.backend.mapper.MtOrderMapper;
import com.gogoga.backend.service.promotion.PromotionRecentOrderStatisticService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 美团订单服务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtOrderService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private final MtOrderMapper mtOrderMapper;
    private final MtOrderDetailMapper mtOrderDetailMapper;
    private final MtShopService mtShopService;
    private final MtShopPoiIdBackfillService mtShopPoiIdBackfillService;
    private final ObjectMapper objectMapper;
    private PromotionRecentOrderStatisticService promotionRecentOrderStatisticService;

    @Autowired(required = false)
    public void setPromotionRecentOrderStatisticService(PromotionRecentOrderStatisticService promotionRecentOrderStatisticService) {
        this.promotionRecentOrderStatisticService = promotionRecentOrderStatisticService;
    }

    /**
     * 处理订单通知
     *
     * @param request 请求对象
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderNotify(HttpServletRequest request) {
        handleOrderNotify(request.getParameterMap());
    }

    /**
     * 处理订单通知
     *
     * @param parameterMap 请求参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleOrderNotify(Map<String, String[]> parameterMap) {
        String ePoiId = firstValue(parameterMap, "ePoiId", "epoiId");
        String message = firstValue(parameterMap, "message");
        if (StringUtils.isBlank(ePoiId) || StringUtils.isBlank(message)) {
            log.warn("美团订单通知缺少必要参数，ePoiId={}, keys={}", ePoiId, parameterMap.keySet());
            return;
        }

        MtShop shop = mtShopService.findByEpoiId(ePoiId);
        if (shop == null) {
            log.warn("美团订单通知未找到店铺，ePoiId={}", ePoiId);
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message);
            saveOrderPayload(shop, ePoiId, root, true);
        } catch (IOException e) {
            log.error("解析美团订单通知失败，ePoiId={}, message={}", ePoiId, message, e);
        }
    }

    /**
     * 同步授权后回捞到的历史订单详情
     *
     * @param shop 店铺
     * @param orderDetailPayload 订单详情载荷
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncHistoricalOrderDetail(MtShop shop, Object orderDetailPayload) {
        if (shop == null || orderDetailPayload == null) {
            log.warn("同步历史订单详情跳过，原因=上下文不完整, shopId={}, payloadPresent={}",
                    shop == null ? null : shop.getId(), orderDetailPayload != null);
            return;
        }
        JsonNode root = objectMapper.valueToTree(orderDetailPayload);
        saveOrderPayload(shop, shop.getEpoiId(), root, false);
    }

    public boolean hasOrderSentOnDay(Long shopId, LocalDate day) {
        if (shopId == null || day == null) {
            return false;
        }
        long dayStart = day.atStartOfDay(DEFAULT_ZONE_ID).toEpochSecond();
        long nextDayStart = day.plusDays(1).atStartOfDay(DEFAULT_ZONE_ID).toEpochSecond();
        Integer count = mtOrderMapper.countOrdersByShopIdAndOrderSendTimeBetween(shopId, dayStart, nextDayStart);
        return count != null && count > 0;
    }

    private void saveOrderPayload(MtShop shop, String ePoiId, JsonNode root, boolean tryBackfillPoiId) {
        String orderId = text(root, "orderId", "order_id");
        if (tryBackfillPoiId && StringUtils.isNotBlank(orderId)) {
            try {
                shop = mtShopPoiIdBackfillService.tryBackfillFromOrderCallback(shop, orderId);
            } catch (Exception e) {
                log.error("订单回调补齐门店 poiId 失败，shopId={}, ePoiId={}, orderId={}",
                        shop == null ? null : shop.getId(), ePoiId, orderId, e);
            }
        }

        MtOrder order = buildOrder(root, ePoiId, shop);
        if (StringUtils.isBlank(order.getOrderId())) {
            log.warn("订单载荷缺少 orderId，shopId={}, ePoiId={}", shop == null ? null : shop.getId(), ePoiId);
            return;
        }

        MtOrder existing = mtOrderMapper.selectByOrderIdForUpdate(order.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        MtOrder persistedOrder = saveOrUpdateOrder(order, existing, now);
        invalidateRecentOrderStatistic(persistedOrder);

        JsonNode details;
        try {
            details = parseArrayField(root, "wmAppOrderFoods", "detail");
        } catch (IOException e) {
            log.warn("解析订单明细字段失败，orderId={}, ePoiId={}", persistedOrder.getOrderId(), ePoiId, e);
            return;
        }
        if (details == null) {
            log.warn("订单载荷缺少可解析的订单明细，orderId={}, ePoiId={}", persistedOrder.getOrderId(), ePoiId);
            return;
        }
        if (details.isEmpty()) {
            log.warn("订单载荷订单明细为空，orderId={}, ePoiId={}", persistedOrder.getOrderId(), ePoiId);
            return;
        }

        mtOrderDetailMapper.deleteByMtOrderId(persistedOrder.getOrderId());
        for (JsonNode detailNode : details) {
            MtOrderDetail detail = buildOrderDetail(detailNode, persistedOrder.getOrderId(), now);
            mtOrderDetailMapper.insert(detail);
        }
    }

    private void invalidateRecentOrderStatistic(MtOrder order) {
        if (promotionRecentOrderStatisticService == null
                || order == null
                || order.getShopId() == null
                || order.getOrderSendTime() == null) {
            return;
        }
        LocalDate orderDate = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(order.getOrderSendTime()),
                DEFAULT_ZONE_ID
        ).toLocalDate();
        // Today's statistic intentionally excludes today; future business dates can include this order date.
        promotionRecentOrderStatisticService.invalidateByShopIdAndBizDateOnOrAfter(order.getShopId(), orderDate.plusDays(1));
    }

    private MtOrder buildOrder(JsonNode root, String ePoiId, MtShop shop) {
        MtOrder order = new MtOrder();
        order.setOrderId(text(root, "orderId", "order_id"));
        order.setShopId(shop.getId());
        order.setEpoiId(firstNonBlank(ePoiId, text(root, "ePoiId", "epoiId")));
        order.setPoiId(firstNonBlank(shop.getPoiId(), text(root, "poiId", "poi_id")));
        order.setActivity(jsonField(root, "activity"));
        order.setCaution(text(root, "caution"));
        order.setDaySeq(text(root, "daySeq", "day_seq"));
        order.setDeliveryTime(longValue(root, "deliveryTime", "delivery_time"));
        order.setLogisticsCode(text(root, "logisticsCode", "logistics_code"));
        order.setOriginalPrice(decimal(root, "originalPrice", "original_price"));
        order.setPayType(integer(root, "payType", "pay_type"));
        order.setPickType(integer(root, "pickType", "pick_type"));
        order.setRecipientAddress(firstNonBlank(
                text(root, "wx_poi_address"),
                text(root, "recipientAddress", "recipient_address")
        ));
        order.setRecipientName(text(root, "recipientName", "recipient_name"));
        order.setRecipientPhone(text(root, "recipientPhone", "recipient_phone"));
        order.setShipperPhone(text(root, "shipperPhone", "shipper_phone"));
        order.setShippingFee(decimal(root, "shippingFee", "shipping_fee"));
        order.setStatus(integer(root, "status"));
        order.setTotal(decimal(root, "total"));
        order.setPoiReceiveDetail(jsonField(root, "poiReceiveDetail", "poi_receive_detail"));
        order.setLatitude(text(root, "latitude"));
        order.setLongitude(text(root, "longitude"));
        order.setOrderSendTime(longValue(root, "orderSendTime", "order_send_time"));
        order.setOrderReceiveTime(longValue(root, "orderReceiveTime", "order_receive_time"));
        order.setOrderConfirmTime(longValue(root, "orderConfirmTime", "order_confirm_time"));
        order.setOrderCancelTime(longValue(root, "orderCancelTime", "order_cancel_time"));
        order.setOrderCompletedTime(longValue(root, "orderCompletedTime", "order_completed_time"));
        order.setLogisticsSendTime(longValue(root, "logisticsSendTime", "logistics_send_time"));
        order.setLogisticsConfirmTime(longValue(root, "logisticsConfirmTime", "logistics_confirm_time"));
        order.setLogisticsCancelTime(longValue(root, "logisticsCancelTime", "logistics_cancel_time"));
        order.setLogisticsFetchTime(longValue(root, "logisticsFetchTime", "logistics_fetch_time"));
        order.setLogisticsCompletedTime(longValue(root, "logisticsCompletedTime", "logistics_completed_time"));
        order.setUserIdView(text(root, "userId_view", "userIdView", "useridView"));
        order.setIsBlack(integer(root, "isBlack", "is_black"));
        order.setIncome(resolveIncome(root));
        return order;
    }

    private MtOrder saveOrUpdateOrder(MtOrder incoming, MtOrder existing, LocalDateTime now) {
        try {
            if (existing == null) {
                incoming.setCreateTime(now);
                incoming.setUpdateTime(now);
                mtOrderMapper.insert(incoming);
                return incoming;
            }
            mergeIncomingIntoExisting(existing, incoming, now);
            mtOrderMapper.updateById(existing);
            return existing;
        } catch (DuplicateKeyException e) {
            MtOrder latest = mtOrderMapper.selectByOrderIdForUpdate(incoming.getOrderId());
            if (latest == null) {
                throw e;
            }
            mergeIncomingIntoExisting(latest, incoming, now);
            mtOrderMapper.updateById(latest);
            return latest;
        }
    }

    private void mergeIncomingIntoExisting(MtOrder existing, MtOrder incoming, LocalDateTime now) {
        existing.setShopId(firstNonNull(incoming.getShopId(), existing.getShopId()));
        existing.setEpoiId(firstNonBlank(incoming.getEpoiId(), existing.getEpoiId()));
        existing.setPoiId(firstNonBlank(incoming.getPoiId(), existing.getPoiId()));
        existing.setActivity(firstNonBlank(incoming.getActivity(), existing.getActivity()));
        existing.setCaution(firstNonBlank(incoming.getCaution(), existing.getCaution()));
        existing.setDaySeq(firstNonBlank(incoming.getDaySeq(), existing.getDaySeq()));
        existing.setDeliveryTime(firstNonNull(incoming.getDeliveryTime(), existing.getDeliveryTime()));
        existing.setLogisticsCode(firstNonBlank(incoming.getLogisticsCode(), existing.getLogisticsCode()));
        existing.setOriginalPrice(firstNonNull(incoming.getOriginalPrice(), existing.getOriginalPrice()));
        existing.setPayType(firstNonNull(incoming.getPayType(), existing.getPayType()));
        existing.setPickType(firstNonNull(incoming.getPickType(), existing.getPickType()));
        existing.setRecipientAddress(firstNonBlank(incoming.getRecipientAddress(), existing.getRecipientAddress()));
        existing.setRecipientName(firstNonBlank(incoming.getRecipientName(), existing.getRecipientName()));
        existing.setRecipientPhone(firstNonBlank(incoming.getRecipientPhone(), existing.getRecipientPhone()));
        existing.setShipperPhone(firstNonBlank(incoming.getShipperPhone(), existing.getShipperPhone()));
        existing.setShippingFee(firstNonNull(incoming.getShippingFee(), existing.getShippingFee()));
        existing.setStatus(mergeStatus(existing.getStatus(), incoming.getStatus()));
        existing.setTotal(firstNonNull(incoming.getTotal(), existing.getTotal()));
        existing.setPoiReceiveDetail(firstNonBlank(incoming.getPoiReceiveDetail(), existing.getPoiReceiveDetail()));
        existing.setLatitude(firstNonBlank(incoming.getLatitude(), existing.getLatitude()));
        existing.setLongitude(firstNonBlank(incoming.getLongitude(), existing.getLongitude()));
        existing.setOrderSendTime(maxNullable(existing.getOrderSendTime(), incoming.getOrderSendTime()));
        existing.setOrderReceiveTime(maxNullable(existing.getOrderReceiveTime(), incoming.getOrderReceiveTime()));
        existing.setOrderConfirmTime(maxNullable(existing.getOrderConfirmTime(), incoming.getOrderConfirmTime()));
        existing.setOrderCancelTime(maxNullable(existing.getOrderCancelTime(), incoming.getOrderCancelTime()));
        existing.setOrderCompletedTime(maxNullable(existing.getOrderCompletedTime(), incoming.getOrderCompletedTime()));
        existing.setLogisticsSendTime(maxNullable(existing.getLogisticsSendTime(), incoming.getLogisticsSendTime()));
        existing.setLogisticsConfirmTime(maxNullable(existing.getLogisticsConfirmTime(), incoming.getLogisticsConfirmTime()));
        existing.setLogisticsCancelTime(maxNullable(existing.getLogisticsCancelTime(), incoming.getLogisticsCancelTime()));
        existing.setLogisticsFetchTime(maxNullable(existing.getLogisticsFetchTime(), incoming.getLogisticsFetchTime()));
        existing.setLogisticsCompletedTime(maxNullable(existing.getLogisticsCompletedTime(), incoming.getLogisticsCompletedTime()));
        existing.setUserIdView(firstNonBlank(incoming.getUserIdView(), existing.getUserIdView()));
        existing.setIsBlack(firstNonNull(incoming.getIsBlack(), existing.getIsBlack()));
        existing.setIncome(firstNonNull(incoming.getIncome(), existing.getIncome()));
        existing.setUpdateTime(now);
    }

    private Integer mergeStatus(Integer existingStatus, Integer incomingStatus) {
        if (existingStatus == null) {
            return incomingStatus;
        }
        if (incomingStatus == null) {
            return existingStatus;
        }
        MtOrderStatusEnum existingEnum = MtOrderStatusEnum.fromCode(existingStatus);
        MtOrderStatusEnum incomingEnum = MtOrderStatusEnum.fromCode(incomingStatus);
        if (existingEnum == null || incomingEnum == null) {
            return Math.max(existingStatus, incomingStatus);
        }
        return Math.max(existingEnum.getCode(), incomingEnum.getCode());
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private Long maxNullable(Long first, Long second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return Math.max(first, second);
    }

    private MtOrderDetail buildOrderDetail(JsonNode node, String orderId, LocalDateTime now) {
        MtOrderDetail detail = new MtOrderDetail();
        detail.setAppFoodCode(text(node, "appFoodCode", "app_food_code"));
        detail.setFoodName(text(node, "foodName", "food_name"));
        detail.setSkuId(text(node, "skuId", "sku_id"));
        detail.setQuantity(integer(node, "quantity"));
        detail.setPrice(decimal(node, "price"));
        detail.setBoxNum(integer(node, "boxNum", "box_num"));
        detail.setBoxPrice(decimal(node, "boxPrice", "box_price"));
        detail.setUnit(text(node, "unit"));
        detail.setFoodProperty(text(node, "foodProperty", "food_property"));
        detail.setSpec(text(node, "spec"));
        detail.setMtSkuId(text(node, "mtSkuId", "mt_sku_id"));
        detail.setMtSpuId(text(node, "mtSpuId", "mt_spu_id"));
        detail.setMtTagId(text(node, "mtTagId", "mt_tag_id"));
        detail.setTagName(text(node, "tagName", "tag_name"));
        detail.setMtOrderId(orderId);
        detail.setCreateTime(now);
        detail.setUpdateTime(now);
        return detail;
    }

    private BigDecimal resolveIncome(JsonNode root) {
        JsonNode poiReceiveDetail = parseObjectField(root, "poiReceiveDetail", "poi_receive_detail");
        if (poiReceiveDetail == null) {
            return null;
        }

        Long incomeCent = longValue(poiReceiveDetail, "wmPoiReceiveCent");
        if (incomeCent == null) {
            return null;
        }
        return BigDecimal.valueOf(incomeCent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private String firstValue(Map<String, String[]> parameterMap, String... keys) {
        for (String key : keys) {
            String[] values = parameterMap.get(key);
            if (ArrayUtils.isNotEmpty(values) && StringUtils.isNotBlank(values[0])) {
                return values[0];
            }
        }
        return null;
    }

    private JsonNode parseArrayField(JsonNode root, String... fieldNames) throws IOException {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isArray()) {
                return node;
            }
            if (node.isTextual() && StringUtils.isNotBlank(node.asText())) {
                JsonNode parsed = objectMapper.readTree(node.asText());
                if (parsed.isArray()) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private JsonNode parseObjectField(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node == null || node.isNull()) {
                continue;
            }
            if (node.isObject()) {
                return node;
            }
            if (node.isTextual() && StringUtils.isNotBlank(node.asText())) {
                try {
                    JsonNode parsed = objectMapper.readTree(node.asText());
                    if (parsed.isObject()) {
                        return parsed;
                    }
                } catch (IOException e) {
                    log.warn("解析 JSON 对象字段失败，field={}", fieldName, e);
                    return null;
                }
            }
        }
        return null;
    }

    private String text(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                String value = node.asText();
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private Integer integer(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                if (node.isInt() || node.isLong() || node.isNumber()) {
                    return node.asInt();
                }
                if (StringUtils.isNotBlank(node.asText())) {
                    return Integer.valueOf(node.asText());
                }
            }
        }
        return null;
    }

    private Long longValue(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                if (node.isLong() || node.isInt() || node.isNumber()) {
                    return node.asLong();
                }
                if (StringUtils.isNotBlank(node.asText())) {
                    return Long.valueOf(node.asText());
                }
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                if (node.isNumber()) {
                    return node.decimalValue();
                }
                if (StringUtils.isNotBlank(node.asText())) {
                    return new BigDecimal(node.asText());
                }
            }
        }
        return null;
    }

    private String jsonField(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && !node.isNull()) {
                try {
                    if (node.isTextual()) {
                        return node.asText();
                    }
                    return objectMapper.writeValueAsString(node);
                } catch (IOException e) {
                    log.warn("序列化 JSON 字段失败，field={}", fieldName, e);
                    return node.toString();
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
