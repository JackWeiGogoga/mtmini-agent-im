package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.param.SystemPromptUpdateParam;
import com.gogoga.backend.vo.SystemPromptConfigVO;

import java.util.Map;

public interface SystemPromptService {

    PageInfo<SystemPromptConfigVO> list(String promptType, Boolean enabled, String keyword, Integer pageNum, Integer pageSize);

    SystemPromptConfigVO getById(Long id);

    SystemPromptConfigVO update(Long id, SystemPromptUpdateParam param);

    SystemPromptConfigVO updateEnabled(Long id, Boolean enabled);

    SystemPromptConfigVO restoreDefault(Long id);

    ResolvedSystemPromptDTO resolve(String promptKey, String fallbackContent);

    ResolvedSystemPromptDTO render(String promptKey, Map<String, ?> variables, String fallbackContent);
}
