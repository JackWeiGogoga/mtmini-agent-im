package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.SystemPromptUpdateParam;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.SystemPromptConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统提示词管理接口。
 */
@RestController
@RequestMapping("/api/system/prompts")
@RequiredArgsConstructor
@RequiresLogin
public class SystemPromptController {

    private final SystemPromptService systemPromptService;

    @GetMapping
    @RequiresPermission("system:prompt:list")
    public ResultVO<PageInfo<SystemPromptConfigVO>> list(
            @RequestParam(required = false) String promptType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(systemPromptService.list(promptType, enabled, keyword, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:prompt:query")
    public ResultVO<SystemPromptConfigVO> getById(@PathVariable Long id) {
        return ResultVO.success(systemPromptService.getById(id));
    }

    @PutMapping("/{id}")
    @RequiresPermission("system:prompt:edit")
    public ResultVO<SystemPromptConfigVO> update(@PathVariable Long id,
                                                 @Valid @RequestBody SystemPromptUpdateParam param) {
        return ResultVO.success(systemPromptService.update(id, param));
    }

    @PatchMapping("/{id}/enabled")
    @RequiresPermission("system:prompt:edit")
    public ResultVO<SystemPromptConfigVO> updateEnabled(@PathVariable Long id,
                                                       @RequestParam Boolean enabled) {
        return ResultVO.success(systemPromptService.updateEnabled(id, enabled));
    }

    @PostMapping("/{id}/restore-default")
    @RequiresPermission("system:prompt:edit")
    public ResultVO<SystemPromptConfigVO> restoreDefault(@PathVariable Long id) {
        return ResultVO.success(systemPromptService.restoreDefault(id));
    }
}
