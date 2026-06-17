package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 美团 H5 首页门店信息
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtH5HomeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 门店名称
     */
    private String shopName;

    /**
     * 美团商家后台可见门店 ID
     */
    private String poiId;

    /**
     * 美团内部门店 ID
     */
    private String epoiId;
}
