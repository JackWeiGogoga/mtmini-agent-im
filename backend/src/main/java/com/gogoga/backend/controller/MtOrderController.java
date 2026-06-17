package com.gogoga.backend.controller;

import com.gogoga.backend.service.MtOrderAsyncTaskService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 美团订单通知控制器
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callbacks/meituan")
public class MtOrderController {

    private final MtOrderAsyncTaskService mtOrderAsyncTaskService;

    /**
     * 处理订单通知
     *
     * @param request 请求对象
     * @return 平台响应
     */
    @RequestMapping("/order")
    public Map<String, Object> orderNotify(HttpServletRequest request) {
        Map<String, Object> logParameters = buildLogParameters(request.getParameterMap());
        log.info("收到美团订单通知，parameters={}", logParameters);
        Map<String, Object> response = new HashMap<>();
        try {
            mtOrderAsyncTaskService.acceptOrderNotify(request.getParameterMap());
            response.put("code", 0);
            response.put("message", "success");
        } catch (Exception e) {
            log.error("处理美团订单通知异常，parameters={}", logParameters, e);
            response.put("code", 1);
            response.put("message", "fail");
        }
        return response;
    }

    private Map<String, Object> buildLogParameters(Map<String, String[]> parameterMap) {
        Map<String, Object> logParameters = new LinkedHashMap<>();
        parameterMap.forEach((key, value) -> logParameters.put(key, value == null ? null : Arrays.asList(value)));
        return logParameters;
    }
}
