package com.gogoga.backend.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * H5 算力充值创建结果
 *
 * @author system
 * @since 2026-03-23
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MtH5PowerRechargeCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 充值单号
     */
    private String rechargeNo;

    /**
     * 是否 mock 支付
     */
    private Boolean mockPayEnabled;

    /**
     * 微信 H5 拉起地址
     */
    private String mwebUrl;
}
