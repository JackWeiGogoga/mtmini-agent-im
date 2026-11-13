package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.entity.sms.SmsAppExpiryCandidate;
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
     * 游标查询当前仍有效的应用按时长权益及其门店最大到期时间。
     */
    List<SmsAppExpiryCandidate> selectActiveAppExpiryCandidatesByCursor(
            @Param("now") LocalDateTime now,
            @Param("lastShopId") Long lastShopId,
            @Param("limit") int limit
    );

    /**
     * 查询门店应用相关按时长权益的最大到期时间（包含已经到期的记录）。
     */
    LocalDateTime selectMaxAppRightExpireAt(@Param("shopId") Long shopId);

    /**
     * 判断门店是否仍存在任意有效应用权益。
     */
    boolean existsActiveAppRight(@Param("shopId") Long shopId, @Param("now") LocalDateTime now);

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
