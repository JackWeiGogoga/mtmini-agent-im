package com.gogoga.backend.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.service.MtImMessageCryptoService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MtImMessageCallbackControllerTest {

    @Test
    void receiveImMessageLogsFormParametersAndReturnsSuccess() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MtImMessageCallbackController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MtImMessageCryptoService cryptoService = cryptoService();
            MockMvc mockMvc = MockMvcBuilders
                    .standaloneSetup(new MtImMessageCallbackController(cryptoService))
                    .build();
            String encryptedMsgContent = cryptoService.encrypt("用户回调消息");
            String message = "{\"biz_type\":\"1\",\"push_content\":"
                    + "\"{\\\"msg_id\\\":1000,\\\"msg_content\\\":"
                    + "\\\"" + encryptedMsgContent + "\\\"}\"}";

            mockMvc.perform(post("/api/callbacks/meituan/imMsg")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("msgType", "210069")
                            .param("developerId", "123456")
                            .param("businessId", "2")
                            .param("sign", "callback-sign")
                            .param("msgId", "3702923312382104528")
                            .param("timestamp", "1711592316026")
                            .param("opBizCode", "1711591987")
                            .param("message", message))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("success"));

            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(logMessage -> logMessage.contains("msgType=[210069]")
                            && logMessage.contains("developerId=[123456]")
                            && logMessage.contains("message=[" + message + "]")
                            && logMessage.contains("decryptedMsgContent=用户回调消息")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private MtImMessageCryptoService cryptoService() {
        MtProperties properties = new MtProperties();
        properties.getComment().getImFollowUp().setImSecret("1234567890abcdef");
        return new MtImMessageCryptoService(properties);
    }
}
