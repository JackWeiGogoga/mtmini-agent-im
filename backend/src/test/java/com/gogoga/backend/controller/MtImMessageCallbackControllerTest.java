package com.gogoga.backend.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gogoga.backend.service.MtImMessageIngestionService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MtImMessageCallbackControllerTest {

    @Test
    void acceptsFormProtocolAndReturnsSuccessOnlyAfterIngestion() throws Exception {
        MtImMessageIngestionService ingestionService = mock(MtImMessageIngestionService.class);
        when(ingestionService.ingest(argThat(params -> params != null
                && "210069".equals(params.get("msgType"))
                && "callback-1".equals(params.get("msgId"))
                && "opaque-message".equals(params.get("message")))))
                .thenReturn(new MtImMessageIngestionService.IngestionResult(true, false, 9L, null));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new MtImMessageCallbackController(ingestionService))
                .build();

        mockMvc.perform(post("/api/callbacks/meituan/imMsg")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("msgType", "210069")
                        .param("msgId", "callback-1")
                        .param("message", "opaque-message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"));

        verify(ingestionService).ingest(argThat(params -> params != null
                && "callback-1".equals(params.get("msgId"))));
    }

    @Test
    void returnsNonZeroWhenVerificationFailsOrPersistenceThrows() throws Exception {
        MtImMessageIngestionService ingestionService = mock(MtImMessageIngestionService.class);
        when(ingestionService.ingest(argThat(params -> params != null
                && "invalid".equals(params.get("msgId")))))
                .thenReturn(new MtImMessageIngestionService.IngestionResult(
                        false, false, null, "CALLBACK_SIGN_INVALID"));
        String secretFailureDetail = "cipher-and-secret-payload";
        when(ingestionService.ingest(argThat(params -> params != null
                && "db-error".equals(params.get("msgId")))))
                .thenThrow(new IllegalStateException(secretFailureDetail));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new MtImMessageCallbackController(ingestionService))
                .build();

        mockMvc.perform(post("/api/callbacks/meituan/imMsg")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("msgId", "invalid"))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("CALLBACK_SIGN_INVALID"));
        Logger controllerLogger = (Logger) LoggerFactory.getLogger(MtImMessageCallbackController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        controllerLogger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/callbacks/meituan/imMsg")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("msgId", "db-error"))
                    .andExpect(jsonPath("$.code").value(2))
                    .andExpect(jsonPath("$.message").value("persist_failed"))
                    .andExpect(content().string(not(containsString(secretFailureDetail))));
        } finally {
            controllerLogger.detachAppender(appender);
        }
        org.junit.jupiter.api.Assertions.assertTrue(appender.list.stream()
                .noneMatch(event -> event.getFormattedMessage().contains(secretFailureDetail)));
    }
}
