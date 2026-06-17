package com.gogoga.backend.controller;

import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.service.MtPowerRechargeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付回调控制器
 *
 * @author system
 * @since 2026-03-23
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callbacks/wechat")
public class MtPaymentNotifyController {

    private final MtPowerRechargeService mtPowerRechargeService;

    /**
     * 微信支付通知
     *
     * @param request HTTP 请求
     * @param body 请求体
     * @return 处理结果
     */
    @PostMapping("/pay")
    public ResponseEntity<Map<String, String>> wechatNotify(HttpServletRequest request, @RequestBody(required = false) String body) {
        Map<String, String> result = new HashMap<>();
        try {
            mtPowerRechargeService.handleWechatNotify(request, body);
            result.put("code", "SUCCESS");
            result.put("message", "成功");
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            log.error("处理微信支付通知失败", e);
            result.put("code", "FAIL");
            result.put("message", e.getMessage());
            HttpStatus status = e.getCode() != null && e.getCode() >= 400 && e.getCode() < 500
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(status).body(result);
        } catch (Exception e) {
            log.error("处理微信支付通知失败", e);
            result.put("code", "FAIL");
            result.put("message", "处理微信支付通知失败");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
