package com.gogoga.backend.vo;

import lombok.Data;

/**
 * 美团 H5 登录结果
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtH5LoginVO {

    /**
     * 业务 token
     */
    private String token;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 门店信息
     */
    private MtH5ShopInfoVO shopInfo;

    @Data
    public static class MtH5ShopInfoVO {
        private String nickName;
        private String userName;
        private String poiId;
        private String epoiId;
        private String appKey;
        private String platform;
        private Integer version;
    }
}
