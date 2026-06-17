package com.gogoga.backend.controller;

import com.gogoga.backend.service.YouzanCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 有赞回调控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callbacks/youzan")
public class YouzanCallbackController {

    private final YouzanCallbackService youzanCallbackService;

    @GetMapping("/app")
    public String appCallback(HttpServletRequest request) {
        youzanCallbackService.handleCallback(request);
        return "success";
    }
}
