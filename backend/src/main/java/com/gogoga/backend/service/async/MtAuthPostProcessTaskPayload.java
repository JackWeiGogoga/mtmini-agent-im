package com.gogoga.backend.service.async;

import lombok.Data;

/**
 * 授权后补偿异步任务载荷。
 */
@Data
public class MtAuthPostProcessTaskPayload {

    private Long shopId;
}
