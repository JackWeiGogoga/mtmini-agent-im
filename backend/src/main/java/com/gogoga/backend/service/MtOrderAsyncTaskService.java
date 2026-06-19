package com.gogoga.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.service.async.MtAsyncTaskEnqueueResult;
import com.gogoga.backend.service.async.MtOrderNotifyTaskPayload;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 美团订单回调异步任务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtOrderAsyncTaskService {

    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtOrderService mtOrderService;
    private final MtProperties mtProperties;

    public void acceptOrderNotify(Map<String, String[]> parameterMap) {
        if (!mtProperties.getAsyncTask().isEnabled()) {
            TransientDatabaseRetrySupport.run(
                    "ORDER_NOTIFY_SYNC",
                    extractOrderId(firstValue(parameterMap, "message")),
                    () -> mtOrderService.handleOrderNotify(parameterMap)
            );
            return;
        }

        MtOrderNotifyTaskPayload payload = buildPayload(parameterMap);
        if (payload == null || StringUtils.isBlank(payload.getEPoiId()) || StringUtils.isBlank(payload.getMessage())) {
            TransientDatabaseRetrySupport.run(
                    "ORDER_NOTIFY_SYNC_FALLBACK",
                    extractOrderId(firstValue(parameterMap, "message")),
                    () -> mtOrderService.handleOrderNotify(parameterMap)
            );
            return;
        }

        String bizKey = buildBizKey(payload);
        String scopeKey = StringUtils.defaultIfBlank(payload.getOrderId(), payload.getEPoiId());
        MtAsyncTaskEnqueueResult enqueueResult = mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.ORDER_NOTIFY,
                bizKey,
                scopeKey,
                payload,
                LocalDateTime.now(),
                mtProperties.getAsyncTask().getOrderNotify().getMaxRetryCount()
        );
        log.info("订单回调异步任务入队完成，orderId={}, ePoiId={}, msgId={}, taskId={}, created={}",
                payload.getOrderId(), payload.getEPoiId(), payload.getMsgId(),
                enqueueResult.getTaskId(), enqueueResult.isCreated());
    }

    public void executeOrderNotifyTask(MtOrderNotifyTaskPayload payload) {
        if (payload == null || StringUtils.isBlank(payload.getEPoiId()) || StringUtils.isBlank(payload.getMessage())) {
            throw new IllegalArgumentException("ORDER_NOTIFY_TASK_PAYLOAD_INVALID");
        }
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("ePoiId", new String[]{payload.getEPoiId()});
        parameterMap.put("message", new String[]{payload.getMessage()});
        TransientDatabaseRetrySupport.run(
                "ORDER_NOTIFY_ASYNC",
                payload.getOrderId(),
                () -> mtOrderService.handleOrderNotify(parameterMap)
        );
    }

    private MtOrderNotifyTaskPayload buildPayload(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return null;
        }
        MtOrderNotifyTaskPayload payload = new MtOrderNotifyTaskPayload();
        payload.setMsgId(firstValue(parameterMap, "msgId"));
        payload.setEPoiId(firstValue(parameterMap, "ePoiId", "epoiId"));
        payload.setMessage(firstValue(parameterMap, "message"));
        payload.setOrderId(extractOrderId(payload.getMessage()));
        return payload;
    }

    private String buildBizKey(MtOrderNotifyTaskPayload payload) {
        if (StringUtils.isNotBlank(payload.getMsgId())) {
            return payload.getMsgId();
        }
        String fallbackSource = String.join("#",
                StringUtils.defaultString(payload.getEPoiId()),
                StringUtils.defaultString(payload.getOrderId()),
                StringUtils.defaultString(payload.getMessage()));
        return "fallback#" + UUID.nameUUIDFromBytes(fallbackSource.getBytes(StandardCharsets.UTF_8));
    }

    private String extractOrderId(String message) {
        JsonNode root = JsonUtil.readTree(message);
        if (root == null) {
            return null;
        }
        JsonNode orderIdNode = root.get("orderId");
        if (orderIdNode == null || orderIdNode.isNull() || StringUtils.isBlank(orderIdNode.asText())) {
            orderIdNode = root.get("order_id");
        }
        return orderIdNode == null || orderIdNode.isNull() ? null : StringUtils.trimToNull(orderIdNode.asText());
    }

    private String firstValue(Map<String, String[]> parameterMap, String... keys) {
        if (parameterMap == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String[] values = parameterMap.get(key);
            if (values == null || values.length == 0) {
                continue;
            }
            for (String value : values) {
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
            }
        }
        return null;
    }
}
