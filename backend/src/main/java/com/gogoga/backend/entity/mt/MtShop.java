package com.gogoga.backend.entity.mt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 美团店铺实体
 *
 * @author system
 * @since 2026-03-22
 */
@Data
public class MtShop {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 店铺名称
     */
    private String name;

    /**
     * 联系人
     */
    private String contactName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 减配送金额
     */
    private BigDecimal lessDeliveryPrice;

    /**
     * 会员价
     */
    private BigDecimal memberPrice;

    /**
     * 新客价
     */
    private BigDecimal newPersonPrice;

    /**
     * 平台扣点
     */
    private BigDecimal reduction;

    /**
     * 一级分类
     */
    private Long category1;

    /**
     * 二级分类
     */
    private Long category2;

    /**
     * 三级分类
     */
    private Long category3;

    /**
     * 地址
     */
    private String address;

    /**
     * 纬度
     */
    private String latitude;

    /**
     * 经度
     */
    private String longitude;

    /**
     * 系统绑定美团店铺 ID
     */
    private String epoiId;

    /**
     * 美团店铺 ID
     */
    private String poiId;

    /**
     * 美团店铺名称
     */
    private String poiName;

    /**
     * 配送费
     */
    private BigDecimal sendPrice;

    /**
     * 保底金额
     */
    private BigDecimal guaranteedPrice;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 店铺公告
     */
    private String promotionInfo;

    /**
     * 门头海报
     */
    private String doorHeadPosters;

    /**
     * 推广图
     */
    private String promoteImg;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 门头海报云 code
     */
    private String doorHeadPostersCode;

    /**
     * 推广图云 code
     */
    private String promoteImgCode;

    /**
     * 小程序店铺 ID
     */
    private String miniPoiId;

    /**
     * 标签名称
     */
    private String laberName;

    /**
     * 店铺 logo
     */
    private String logo;

    /**
     * 店铺电话
     */
    private String phone;

    /**
     * 营业时间
     */
    private String shippingTime;

    /**
     * 推广广告明细历史补偿状态
     */
    private String promotionAdBackfillStatus;

    /**
     * 推广广告明细历史补偿最近一次处理时间
     */
    private LocalDateTime promotionAdBackfillAt;

    /**
     * 历史订单补数状态
     */
    private String historicalOrderBackfillStatus;

    /**
     * 历史订单补数最近一次处理时间
     */
    private LocalDateTime historicalOrderBackfillAt;
}
