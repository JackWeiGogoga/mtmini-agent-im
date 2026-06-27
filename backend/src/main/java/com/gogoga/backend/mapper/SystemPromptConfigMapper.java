package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.SystemPromptConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统提示词配置 Mapper。
 */
@Mapper
public interface SystemPromptConfigMapper {

    SystemPromptConfig selectById(@Param("id") Long id);

    SystemPromptConfig selectByPromptKey(@Param("promptKey") String promptKey);

    List<SystemPromptConfig> selectForAdmin(@Param("promptType") String promptType,
                                            @Param("enabled") Boolean enabled,
                                            @Param("keyword") String keyword);

    int updateContent(SystemPromptConfig config);

    int updateEnabled(@Param("id") Long id,
                      @Param("enabled") Boolean enabled,
                      @Param("updateBy") String updateBy,
                      @Param("updateTime") LocalDateTime updateTime);
}
