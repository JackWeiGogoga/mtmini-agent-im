package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionAutoBidCandidate;
import com.gogoga.backend.entity.mt.MtPromotionRightExpiryShutdownCandidate;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 门店推广配置 Mapper
 *
 * @author system
 * @since 2026-03-24
 */
@Mapper
public interface MtPromotionSettingMapper {

    /**
     * 按主键查询
     *
     * @param id 主键 ID
     * @return 推广配置
     */
    MtPromotionSetting selectById(@Param("id") Long id);

    /**
     * 按店铺 ID 查询
     *
     * @param shopId 店铺 ID
     * @return 推广配置
     */
    MtPromotionSetting selectByShopId(@Param("shopId") Long shopId);

    List<MtPromotionSetting> selectByShopIds(@Param("shopIds") List<Long> shopIds);

    /**
     * 初始化默认配置
     *
     * @param shopId 店铺 ID
     * @param strategyConfigJson 默认策略配置
     * @param strategyConfigured 是否已配置默认策略
     * @param now 当前时间
     * @return 影响行数
     */
    int initDefaultIfAbsent(
            @Param("shopId") Long shopId,
            @Param("strategyConfigJson") String strategyConfigJson,
            @Param("strategyConfigured") Boolean strategyConfigured,
            @Param("now") LocalDateTime now
    );

    /**
     * 新增配置
     *
     * @param setting 配置实体
     * @return 影响行数
     */
    int insert(MtPromotionSetting setting);

    /**
     * 按主键更新
     *
     * @param setting 配置实体
     * @return 影响行数
     */
    int updateById(MtPromotionSetting setting);

    /**
     * 查询自动调价候选门店
     *
     * @param now 当前时间
     * @return 候选门店列表
     */
    List<MtPromotionAutoBidCandidate> selectAutoBidCandidates(@Param("now") LocalDateTime now);

    /**
     * 按游标查询自动调价候选门店。
     *
     * @param now 当前时间
     * @param lastShopId 上一页最后一个店铺 ID，可为空
     * @param limit 每页数量
     * @return 候选门店列表
     */
    List<MtPromotionAutoBidCandidate> selectAutoBidCandidatesByCursor(
            @Param("now") LocalDateTime now,
            @Param("lastShopId") Long lastShopId,
            @Param("limit") int limit
    );

    /**
     * 按游标查询功能权益过期后需要主动关停的门店。
     *
     * @param now 当前时间
     * @param lastShopId 上一页最后一个店铺 ID，可为空
     * @param limit 每页数量
     * @return 候选门店列表
     */
    List<MtPromotionRightExpiryShutdownCandidate> selectRightExpiryShutdownCandidatesByCursor(
            @Param("now") LocalDateTime now,
            @Param("lastShopId") Long lastShopId,
            @Param("limit") int limit
    );

    /**
     * 查询需要刷新高峰画像的门店
     *
     * @param now 当前时间
     * @return 门店 ID 列表
     */
    List<Long> selectPeakProfileRefreshShopIds(@Param("now") LocalDateTime now);
}
