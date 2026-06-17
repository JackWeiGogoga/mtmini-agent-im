package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 后台美团订阅订单版本筛选项
 */
@Data
public class AdminMtAppOrderVersionOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;

    private String versionName;
}
