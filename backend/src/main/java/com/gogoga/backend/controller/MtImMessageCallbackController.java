package com.gogoga.backend.controller;

import com.gogoga.backend.service.MtImMessageIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 美团 IM 消息回调控制器。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/callbacks/meituan")
public class MtImMessageCallbackController {

    private final MtImMessageIngestionService ingestionService;

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
        Map<String, String> singleValueParameters = new LinkedHashMap<>();
        parameters.forEach((key, values) -> singleValueParameters.put(key, values == null || values.isEmpty()
                ? null : values.get(0)));
        try {
            MtImMessageIngestionService.IngestionResult result = ingestionService.ingest(singleValueParameters);
            if (result.success()) {
                return Map.of("code", 0, "message", "success");
            }
            return Map.of("code", 1, "message", result.errorCode());
        } catch (RuntimeException e) {
            log.error("美团IM消息持久化失败，callbackMsgId={}, errorType={}",
                    parameters.getFirst("msgId"), e.getClass().getSimpleName());
            return Map.of("code", 2, "message", "persist_failed");
        }
    }
}
