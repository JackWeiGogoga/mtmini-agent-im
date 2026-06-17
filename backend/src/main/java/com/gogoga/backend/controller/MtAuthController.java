package com.gogoga.backend.controller;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.param.MtH5DirectLoginParam;
import com.gogoga.backend.service.MtAuthService;
import com.gogoga.backend.service.MtH5AuthService;
import com.gogoga.backend.vo.MtH5LoginVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 美团授权回调控制器
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MtAuthController {

    private final MtAuthService mtAuthService;
    private final MtH5AuthService mtH5AuthService;
    private final MtProperties mtProperties;

    /**
     * 美团授权回调
     *
     * @param code       授权码
     * @param developerId 开发者 ID
     * @param businessId  业务 ID
     * @param sign        签名
     * @param response    响应对象
     */
    @RequestMapping("/api/callbacks/meituan/auth")
    public void authNotify(String code, String developerId, String businessId, String sign, HttpServletResponse response) {
        log.info("收到美团授权回调，code={}, developerId={}, businessId={}, sign={}", code, developerId, businessId, sign);
        try {
            if (!mtH5AuthService.verifyAuthNotifySign(code, developerId, businessId, sign)) {
                log.warn("美团授权回调验签失败，developerId={}, businessId={}", developerId, businessId);
            } else if (StringUtils.isNotBlank(code)) {
                mtAuthService.auth(code);
            }
        } catch (Exception e) {
            log.error("处理美团授权回调失败，code={}", code, e);
        }

        try {
            response.sendRedirect(mtProperties.getCallbackSuccessUrl());
        } catch (IOException e) {
            log.error("美团授权回调重定向失败", e);
        }
    }

    /**
     * 美团 H5 直登
     *
     * @param param 直登参数
     * @return H5 登录结果
     */
    @PostMapping("/api/mt/auth/login")
    public ResultVO<MtH5LoginVO> login(@Valid @RequestBody MtH5DirectLoginParam param) {
        return ResultVO.success(mtH5AuthService.login(param));
    }
}
