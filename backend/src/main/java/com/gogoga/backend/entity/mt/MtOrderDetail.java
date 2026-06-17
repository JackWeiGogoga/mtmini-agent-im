package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 美团订单明细实体
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtOrderDetail {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 菜品 code
     */
    private String appFoodCode;

    /**
     * 菜品名称
     */
    private String foodName;

    /**
     * sku 编码
     */
    private String skuId;

    /**
     * 商品数量
     */
    private Integer quantity;

    /**
     * 商品单价
     */
    private BigDecimal price;

    /**
     * 餐盒数量
     */
    private Integer boxNum;

    /**
     * 餐盒价格
     */
    private BigDecimal boxPrice;

    /**
     * 单位
     */
    private String unit;

    /**
     * 菜品属性
     */
    private String foodProperty;

    /**
     * 菜品规格
     */
    private String spec;

    /**
     * 美团 sku ID
     */
    private String mtSkuId;

    /**
     * 美团 spu ID
     */
    private String mtSpuId;

    /**
     * 美团 tag ID
     */
    private String mtTagId;

    /**
     * tag 名称
     */
    private String tagName;

    /**
     * 关联订单 ID
     */
    private String mtOrderId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
