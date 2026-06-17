package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPowerConsume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店算力消费 Mapper
 *
 * @author system
 * @since 2026-03-23
 */
@Mapper
public interface MtPowerConsumeMapper {

    /**
     * 按主键查询
     *
     * @param id 主键 ID
     * @return 消费实体
     */
    MtPowerConsume selectById(@Param("id") Long id);

    /**
     * 按店铺和请求号查询
     *
     * @param shopId 店铺 ID
     * @param requestNo 请求号
     * @return 消费实体
     */
    MtPowerConsume selectByShopIdAndRequestNo(@Param("shopId") Long shopId, @Param("requestNo") String requestNo);

    /**
     * 汇总指定时间范围内的算力消耗
     *
     * @param shopId 店铺 ID
     * @param startTime 起始时间
     * @param endTime 结束时间
     * @return 算力消耗总量
     */
    Integer sumPowerAmountByShopIdAndCreateTimeBetween(
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<MtPowerConsume> selectByShopId(@Param("shopId") Long shopId);

    List<MtPowerConsume> selectByShopIdAndCreateTimeBetween(
            @Param("shopId") Long shopId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    List<MtPowerConsume> selectByShopIdAndSourceId(
            @Param("shopId") Long shopId,
            @Param("sourceId") String sourceId
    );

    /**
     * 新增消费流水
     *
     * @param consume 消费实体
     * @return 影响行数
     */
    int insert(MtPowerConsume consume);
}
