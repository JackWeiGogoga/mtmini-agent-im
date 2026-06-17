package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 美团订单回调异步任务载荷。
 */
@Data
public class MtOrderNotifyTaskPayload {

    private String msgId;

    private String orderId;

    private String ePoiId;

    private String message;
}
