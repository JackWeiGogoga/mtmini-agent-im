package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.CommentReplySensitiveWordHitDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentComplaint;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentComplaintMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.CommentReplySensitiveWordService;
import com.gogoga.backend.service.CommentImageVisionService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.ShopOperationSettingsService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.model.wmoperNg.comment.commentComplainReport.CommentComplainReportRequest;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtCommentComplaintServiceImplTest {

    @Mock
    private MtCommentMapper mtCommentMapper;
    @Mock
    private MtCommentComplaintMapper mtCommentComplaintMapper;
    @Mock
    private MtShopMapper mtShopMapper;
    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;
    @Mock
    private MeituanClient meituanClient;
    @Mock
    private SystemPromptService systemPromptService;
    @Mock
    private OpenAiCompatibleChatService openAiCompatibleChatService;
    @Mock
    private CommentReplySensitiveWordService commentReplySensitiveWordService;
    @Mock
    private CommentImageVisionService commentImageVisionService;
    @Mock
    private MtPowerConsumeService mtPowerConsumeService;
    @Mock
    private ShopOperationSettingsService shopOperationSettingsService;

    private MtProperties mtProperties;
    private MtCommentComplaintServiceImpl service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        service = new MtCommentComplaintServiceImpl(
                mtProperties,
                new PowerProperties(),
                mtCommentMapper,
                mtCommentComplaintMapper,
                mtShopMapper,
                mtAccessTokenMapper,
                meituanClient,
                systemPromptService,
                openAiCompatibleChatService,
                commentReplySensitiveWordService,
                commentImageVisionService,
                mtPowerConsumeService,
                shopOperationSettingsService
        );
    }

    @Test
    void executeGeneratesAndSubmitsComplaint() throws Exception {
        stubBaseContext();
        when(systemPromptService.render(anyString(), anyMap(), anyString()))
                .thenAnswer(invocation -> resolved(invocation.getArgument(0), invocation.getArgument(2)));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any()))
                .thenReturn(chatCompletion("{\"reportTypeCode\":31,\"reason\":\"配送超时\"}"))
                .thenReturn(chatCompletion("顾客评价主要受配送超时影响，商家餐品和包装无明显问题，请平台核实配送履约情况。"));
        MeituanResponse<String> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        response.setData("ok");
        when(meituanClient.invokeApi(any(CommentComplainReportRequest.class), eq("token"))).thenReturn(response);

        service.executeCommentAutoComplaintTask(payload());

        ArgumentCaptor<CommentComplainReportRequest> requestCaptor = ArgumentCaptor.forClass(CommentComplainReportRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals("542543250001", requestCaptor.getValue().getCommentId());
        assertEquals(31, requestCaptor.getValue().getReportTypeCode());
        verify(mtPowerConsumeService).consume(any(PowerConsumeRequestDTO.class));
        verify(mtCommentComplaintMapper).markSuccess(eq(9L), any(), any(), any(), any(), any());
        verify(mtCommentMapper).markComplaintSuccess(eq(1L), eq(31), any(), any(), any());
    }

    @Test
    void invalidReportTypeDefaultsTo47() throws Exception {
        stubBaseContext();
        when(systemPromptService.render(anyString(), anyMap(), anyString()))
                .thenAnswer(invocation -> resolved(invocation.getArgument(0), invocation.getArgument(2)));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any()))
                .thenReturn(chatCompletion("{\"reportTypeCode\":999,\"reason\":\"不确定\"}"))
                .thenReturn(chatCompletion("顾客评价内容缺少明确事实依据，请平台按无意义评价核实处理。"));
        MeituanResponse<String> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        when(meituanClient.invokeApi(any(CommentComplainReportRequest.class), eq("token"))).thenReturn(response);

        service.executeCommentAutoComplaintTask(payload());

        ArgumentCaptor<CommentComplainReportRequest> requestCaptor = ArgumentCaptor.forClass(CommentComplainReportRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals(47, requestCaptor.getValue().getReportTypeCode());
    }

    @Test
    void nonJsonReportTypeDefaultsTo47() throws Exception {
        stubBaseContext();
        when(systemPromptService.render(anyString(), anyMap(), anyString()))
                .thenAnswer(invocation -> resolved(invocation.getArgument(0), invocation.getArgument(2)));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any()))
                .thenReturn(chatCompletion("无法判断"))
                .thenReturn(chatCompletion("顾客评价内容缺少明确事实依据，请平台按无意义评价核实处理。"));
        MeituanResponse<String> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        when(meituanClient.invokeApi(any(CommentComplainReportRequest.class), eq("token"))).thenReturn(response);

        service.executeCommentAutoComplaintTask(payload());

        ArgumentCaptor<CommentComplainReportRequest> requestCaptor = ArgumentCaptor.forClass(CommentComplainReportRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals(47, requestCaptor.getValue().getReportTypeCode());
    }

    @Test
    void dryRunDoesNotSubmitToMeituan() throws Exception {
        mtProperties.getComment().setComplaintDryRun(true);
        stubBaseContext();
        when(systemPromptService.render(anyString(), anyMap(), anyString()))
                .thenAnswer(invocation -> resolved(invocation.getArgument(0), invocation.getArgument(2)));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any()))
                .thenReturn(chatCompletion("{\"reportTypeCode\":47,\"reason\":\"内容无意义\"}"))
                .thenReturn(chatCompletion("顾客评价内容较少，缺少明确问题指向，请平台核实是否属于无意义评价。"));

        assertThrows(MtAsyncTaskDryRunException.class, () -> service.executeCommentAutoComplaintTask(payload()));

        verify(meituanClient, never()).invokeApi(any(CommentComplainReportRequest.class), anyString());
        verify(mtCommentComplaintMapper).markGenerated(any(MtCommentComplaint.class));
    }

    @Test
    void successRecordSkipsWithoutModelOrMeituanCall() throws Exception {
        MtComment comment = comment();
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);
        MtCommentComplaint existing = record();
        existing.setStatus("SUCCESS");
        when(mtCommentComplaintMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(existing);
        MtShop shop = shop();
        when(mtShopMapper.selectById(100L)).thenReturn(shop);

        service.executeCommentAutoComplaintTask(payload());

        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any());
        verify(meituanClient, never()).invokeApi(any(CommentComplainReportRequest.class), anyString());
    }

    @Test
    void invalidCommentIsMarkedSkippedWithoutModelOrMeituanCall() throws Exception {
        MtComment comment = comment();
        comment.setValid(0);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);
        when(mtShopMapper.selectById(100L)).thenReturn(shop());
        when(mtCommentComplaintMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(record());

        service.executeCommentAutoComplaintTask(payload());

        verify(mtCommentComplaintMapper).markFailed(
                eq(9L), eq("SKIPPED"), eq("COMMENT_AUTO_COMPLAINT_INVALID"), any(), any());
        verify(mtCommentMapper).markComplaintFailed(
                eq(1L), eq("SKIPPED"), eq("COMMENT_AUTO_COMPLAINT_INVALID"), any());
        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any());
        verify(meituanClient, never()).invokeApi(any(CommentComplainReportRequest.class), anyString());
    }

    @Test
    void unsupportedSensitiveTypeIsMarkedSkippedWithoutModelOrMeituanCall() throws Exception {
        MtComment comment = comment();
        comment.setCommentType(3);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);
        when(mtShopMapper.selectById(100L)).thenReturn(shop());
        when(mtCommentComplaintMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(record());

        service.executeCommentAutoComplaintTask(payload());

        verify(mtCommentComplaintMapper).markFailed(
                eq(9L), eq("SKIPPED"), eq("COMMENT_AUTO_COMPLAINT_TYPE_UNSUPPORTED"), any(), any());
        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any());
        verify(meituanClient, never()).invokeApi(any(CommentComplainReportRequest.class), anyString());
    }

    @Test
    void generatedRecordReusesContentWithoutModelOrDuplicatePower() throws Exception {
        MtComment comment = comment();
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);
        when(mtShopMapper.selectById(100L)).thenReturn(shop());
        MtCommentComplaint existing = record();
        existing.setStatus("GENERATED");
        existing.setReportTypeCode(47);
        existing.setReportContent("顾客评价内容较少，缺少明确问题指向，请平台按无意义评价核实处理。");
        when(mtCommentComplaintMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(existing);
        when(shopOperationSettingsService.isAutoReplyEnabled(100L)).thenReturn(true);
        when(shopOperationSettingsService.isNegativeCommentAppealEnabled(100L)).thenReturn(true);
        when(commentReplySensitiveWordService.detect(anyString())).thenReturn(new CommentReplySensitiveWordHitDTO());
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectByShopId(100L)).thenReturn(token);
        MeituanResponse<String> response = new MeituanResponse<>();
        response.setCode("OP_SUCCESS");
        when(meituanClient.invokeApi(any(CommentComplainReportRequest.class), eq("token"))).thenReturn(response);

        service.executeCommentAutoComplaintTask(payload());

        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any());
        verify(mtPowerConsumeService, never()).consume(any());
        ArgumentCaptor<CommentComplainReportRequest> requestCaptor = ArgumentCaptor.forClass(CommentComplainReportRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        assertEquals(47, requestCaptor.getValue().getReportTypeCode());
    }

    private void stubBaseContext() {
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment());
        when(mtShopMapper.selectById(100L)).thenReturn(shop());
        MtCommentComplaint record = record();
        when(mtCommentComplaintMapper.selectByShopIdAndCommentId(100L, 542543250001L)).thenReturn(record);
        when(shopOperationSettingsService.isAutoReplyEnabled(100L)).thenReturn(true);
        when(shopOperationSettingsService.isNegativeCommentAppealEnabled(100L)).thenReturn(true);
        when(commentReplySensitiveWordService.detect(anyString())).thenReturn(new CommentReplySensitiveWordHitDTO());
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectByShopId(100L)).thenReturn(token);
    }

    private MtCommentAutoComplaintTaskPayload payload() {
        MtCommentAutoComplaintTaskPayload payload = new MtCommentAutoComplaintTaskPayload();
        payload.setShopId(100L);
        payload.setCommentId(542543250001L);
        return payload;
    }

    private MtComment comment() {
        MtComment comment = new MtComment();
        comment.setId(1L);
        comment.setShopId(100L);
        comment.setCommentId(542543250001L);
        comment.setValid(1);
        comment.setCommentType(4);
        comment.setProductScore(2);
        comment.setPackingScore(5);
        comment.setDeliveryCommentScore(1);
        comment.setCommentContent("太慢了");
        comment.setOverDeliveryTimeDesc("配送超时15分钟");
        return comment;
    }

    private MtShop shop() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        return shop;
    }

    private MtCommentComplaint record() {
        MtCommentComplaint record = new MtCommentComplaint();
        record.setId(9L);
        record.setShopId(100L);
        record.setCommentId(542543250001L);
        record.setStatus("PENDING");
        return record;
    }

    private ResolvedSystemPromptDTO resolved(String key, String content) {
        ResolvedSystemPromptDTO prompt = new ResolvedSystemPromptDTO();
        prompt.setPromptKey(key);
        prompt.setContent(content);
        prompt.setVersion(1);
        prompt.setSource("TEST");
        return prompt;
    }

    private ChatCompletion chatCompletion(String content) {
        ChatCompletionMessage message = ChatCompletionMessage.builder()
                .role(JsonValue.from("assistant"))
                .content(content)
                .refusal(Optional.empty())
                .build();
        return ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(1L)
                .model("deepseek-v4-flash")
                .object_(JsonValue.from("chat.completion"))
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0L)
                        .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                        .logprobs(Optional.empty())
                        .message(message)
                        .build())
                .build();
    }
}
