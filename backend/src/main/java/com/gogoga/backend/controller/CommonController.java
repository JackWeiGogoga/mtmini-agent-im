package com.gogoga.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gogoga.backend.vo.ResultVO;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用数据控制器
 *
 * @author JackWei
 * @since 2026-01-21
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

    // 当前激活的 Profile（环境）
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 获取当前环境信息
     *
     * @return 环境信息，包含环境名称、是否线上等
     */
    @GetMapping("/env")
    public ResultVO<Map<String, Object>> getEnvInfo() {
        Map<String, Object> envInfo = new HashMap<>();
        envInfo.put("profile", activeProfile);
        envInfo.put("label", getEnvLabel(activeProfile));
        return ResultVO.success(envInfo);
    }

    /**
     * 根据 profile 获取环境显示标签
     *
     * @param profile 环境标识
     * @return 环境显示标签
     */
    private String getEnvLabel(String profile) {
        String normalizedProfile = profile == null ? "" : profile.toLowerCase();
        switch (normalizedProfile) {
            case "dev":
                return "开发";
            case "test":
                return "测试";
            case "staging":
            case "pre":
                return "预发";
            case "prod":
            case "production":
                return "生产";
            default:
                return profile == null ? "" : profile.toUpperCase();
        }
    }
}
