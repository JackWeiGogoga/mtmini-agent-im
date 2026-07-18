package com.gogoga.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.service.MtImMessageCryptoService;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 美团 IM 消息回调控制器。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callbacks/meituan")
public class MtImMessageCallbackController {

    private final MtImMessageCryptoService cryptoService;

    /**
     * 接收美团 IM 两方会话消息。
     *
     * @param parameters 美团推送的表单参数
     * @return 美团规范的成功响应
     */
    @PostMapping(
            value = "/imMsg",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Map<String, Object> receiveImMessage(@RequestParam MultiValueMap<String, String> parameters) {
        String decryptedMsgContent = null;
        try {
            decryptedMsgContent = decryptMsgContent(parameters.getFirst("message"));
        } catch (RuntimeException e) {
            log.warn("美团 IM 消息回调解密失败，msgId={}, error={}",
                    parameters.getFirst("msgId"), e.getMessage());
        }
        log.info("收到美团 IM 消息回调，parameters={}, decryptedMsgContent={}",
                parameters, decryptedMsgContent);
        return Map.of("code", 0, "message", "success");
    }

    private String decryptMsgContent(String message) {
        if (StringUtils.isBlank(message)) {
            return null;
        }
        JsonNode messageNode = JsonUtil.toBean(message, JsonNode.class);
        if (messageNode == null || !messageNode.isObject()) {
            throw new IllegalArgumentException("MT_IM_CALLBACK_MESSAGE_INVALID");
        }

        JsonNode pushContentNode = messageNode.get("push_content");
        if (pushContentNode != null && pushContentNode.isTextual()) {
            pushContentNode = JsonUtil.toBean(pushContentNode.asText(), JsonNode.class);
        }
        if (pushContentNode == null || !pushContentNode.isObject()) {
            throw new IllegalArgumentException("MT_IM_CALLBACK_PUSH_CONTENT_INVALID");
        }

        String encryptedMsgContent = pushContentNode.path("msg_content").asText(null);
        if (StringUtils.isBlank(encryptedMsgContent)) {
            return null;
        }
        return cryptoService.decrypt(encryptedMsgContent);
    }
}
