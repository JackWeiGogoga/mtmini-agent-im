package com.gogoga.backend.mapper;

import com.gogoga.backend.param.CommentProcessingLogQueryParam;
import com.gogoga.backend.vo.CommentProcessingLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理后台评价处理日志查询 Mapper。
 */
@Mapper
public interface CommentProcessingLogMapper {

    List<CommentProcessingLogVO> selectForAdmin(
            @Param("query") CommentProcessingLogQueryParam query,
            @Param("visibleUserIds") List<String> visibleUserIds
    );

    CommentProcessingLogVO selectByIdForAdmin(
            @Param("id") Long id,
            @Param("visibleUserIds") List<String> visibleUserIds
    );
}
