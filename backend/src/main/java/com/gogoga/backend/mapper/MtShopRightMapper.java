package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtShopRight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店权益 Mapper
 *
 * @author system
 * @since 2026-03-25
 */
@Mapper
public interface MtShopRightMapper {

    /**
     * 按主键查询
     *
     * @param id 主键 ID
     * @return 门店权益
     */
    MtShopRight selectById(@Param("id") Long id);

    /**
     * 按店铺和权益编码查询
     *
     * @param shopId 店铺 ID
     * @param serviceCode 权益编码
     * @return 门店权益
     */
    MtShopRight selectByShopIdAndServiceCode(@Param("shopId") Long shopId, @Param("serviceCode") String serviceCode);

    /**
     * 按店铺查询全部权益
     *
     * @param shopId 店铺 ID
     * @return 权益列表
     */
    List<MtShopRight> selectByShopId(@Param("shopId") Long shopId);

    /**
     * 判断门店是否有有效推广功能权益。
     *
     * @param shopId 店铺 ID
     * @param now 当前时间
     * @return 是否有有效权益
     */
    boolean existsActivePromotionRight(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

    /**
     * 新增权益
     *
     * @param right 权益实体
     * @return 影响行数
     */
    int insert(MtShopRight right);

    /**
     * 按主键更新权益
     *
     * @param right 权益实体
     * @return 影响行数
     */
    int updateById(MtShopRight right);
}
