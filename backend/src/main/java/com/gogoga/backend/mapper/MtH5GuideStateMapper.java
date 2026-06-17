package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtH5GuideState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * H5 引导状态 Mapper
 *
 * @author system
 * @since 2026-05-12
 */
@Mapper
public interface MtH5GuideStateMapper {

    MtH5GuideState selectByShopIdAndGuideCode(
            @Param("shopId") Long shopId,
            @Param("guideCode") String guideCode
    );

    int upsertCompleted(
            @Param("shopId") Long shopId,
            @Param("guideCode") String guideCode,
            @Param("completedAt") LocalDateTime completedAt
    );
}
