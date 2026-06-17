package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.UserBehaviorEvent;
import com.gogoga.backend.vo.UserBehaviorAnalyticsRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserBehaviorEventMapper {

    int insert(UserBehaviorEvent event);

    List<UserBehaviorAnalyticsRowVO> selectAnalyticsRows(
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("keyword") String keyword,
            @Param("visibilityMode") String visibilityMode,
            @Param("currentUserId") String currentUserId,
            @Param("visibleUserIds") List<String> visibleUserIds,
            @Param("sortColumn") String sortColumn,
            @Param("sortDirection") String sortDirection
    );
}
