package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 美团 H5 直登参数
 *
 * @author system
 * @since 2026-03-24
 */
@Data
public class MtH5DirectLoginParam {

    /**
     * 美团门店 epoiId
     */
    @NotBlank(message = "wmPoiId不能为空")
    private String wmPoiId;

    /**
     * 平台启动票据，当前阶段仅透传保留
     */
    private String bizToken;

    /**
     * 平台标识
     */
    private String platform;

    /**
     * 应用标识
     */
    private String appKey;
}
