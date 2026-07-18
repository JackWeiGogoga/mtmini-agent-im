package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.CommentReplySensitiveWordsProperties;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentReplyGeneration;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.PowerInsufficientException;
import com.gogoga.backend.mapper.MtCommentReplyGenerationMapper;
import com.gogoga.backend.service.CommentImageVisionService;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CommentReplyOfficialTemplateService;
import com.gogoga.backend.service.CommentReplySensitiveWordService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtPowerAccountService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.SystemPromptService;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtCommentReplyContentServiceImplTest {

    @Mock
    private CommentReplyOfficialTemplateService officialTemplateService;
    @Mock
    private CommentImageVisionService commentImageVisionService;
    @Mock
    private CommentReplyEmbeddingService commentReplyEmbeddingService;
    @Mock
    private KnowledgeVectorStore knowledgeVectorStore;
    @Mock
    private OpenAiCompatibleChatService openAiCompatibleChatService;
    @Mock
    private MtPowerAccountService mtPowerAccountService;
    @Mock
    private MtPowerConsumeService mtPowerConsumeService;
    @Mock
    private MtCommentReplyGenerationMapper mtCommentReplyGenerationMapper;
    @Mock
    private SystemPromptService systemPromptService;

    private MtProperties mtProperties;
    private CommentReplySensitiveWordsProperties sensitiveWordsProperties;
    private MtCommentReplyContentServiceImpl service;
    private CommentReplySensitiveWordService commentReplySensitiveWordService;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        sensitiveWordsProperties = new CommentReplySensitiveWordsProperties();
        mtProperties.getComment().setDefaultReply("感谢评价");
        mtProperties.getComment().getKnowledge().setModelApiKey("key");
        addSensitiveWord("OFF_PLATFORM", "站外导流违规词", "HIGH", "微信");
        commentReplySensitiveWordService = new CommentReplySensitiveWordServiceImpl(mtProperties, sensitiveWordsProperties);
        service = new MtCommentReplyContentServiceImpl(
                mtProperties,
                officialTemplateService,
                commentImageVisionService,
                commentReplyEmbeddingService,
                knowledgeVectorStore,
                openAiCompatibleChatService,
                mtPowerAccountService,
                mtPowerConsumeService,
                mtCommentReplyGenerationMapper,
                systemPromptService,
                commentReplySensitiveWordService
        );
        lenient().doAnswer(invocation -> {
            MtCommentReplyGeneration generation = invocation.getArgument(0);
            generation.setId(99L);
            return 1;
        }).when(mtCommentReplyGenerationMapper).insert(any(MtCommentReplyGeneration.class));
        lenient().when(commentImageVisionService.resolveForReply(any(), any(), eq(true))).thenReturn(emptyImageSummary());
        lenient().when(systemPromptService.resolve(any(), any())).thenAnswer(invocation -> {
            ResolvedSystemPromptDTO result = new ResolvedSystemPromptDTO();
            result.setPromptKey(invocation.getArgument(0));
            result.setContent(invocation.getArgument(1));
            result.setVersion(1);
            result.setSource("TEST");
            return result;
        });
        lenient().when(systemPromptService.render(any(), any(), any())).thenAnswer(invocation -> {
            ResolvedSystemPromptDTO result = new ResolvedSystemPromptDTO();
            result.setPromptKey(invocation.getArgument(0));
            result.setContent(invocation.getArgument(2));
            result.setVersion(1);
            result.setSource("TEST");
            return result;
        });
    }

    @Test
    void noValidCommentScoreSkipsReplyWithoutGeneration() {
        MtComment comment = comment(6);
        comment.setProductScore(6);
        comment.setPackingScore(null);

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment);

        assertFalse(result.isShouldReply());
        assertEquals("COMMENT_SCORE_INVALID", result.getSkipReason());
        verify(mtCommentReplyGenerationMapper, never()).insert(any());
    }

    @Test
    void insufficientBalanceSkipsReplyWithoutModelOrOfficialFallback() {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(10000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(1));

        assertFalse(result.isShouldReply());
        assertEquals(PowerInsufficientException.REASON_CODE, result.getSkipReason());
        verify(commentReplyEmbeddingService, never()).embed(any());
        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any(), any());
        verify(officialTemplateService, never()).selectRandomEnabledTemplate(any());
        verify(mtCommentReplyGenerationMapper, never()).insert(any());
    }

    @Test
    void consumeRaceWithInsufficientPowerSkipsReplyWithoutOfficialFallback() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(false);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(chatCompletion("感谢认可，我们会继续保持。"));
        doThrow(new PowerInsufficientException()).when(mtPowerConsumeService).consume(any());

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(5));

        assertFalse(result.isShouldReply());
        assertEquals(PowerInsufficientException.REASON_CODE, result.getSkipReason());
        verify(officialTemplateService, never()).selectRandomEnabledTemplate(any());
        verify(mtCommentReplyGenerationMapper, never()).insert(any());
    }

    @Test
    void noShopKnowledgeStillUsesChatModelWithoutEmbeddingSearch() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(false);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(chatCompletion("感谢认可，我们会继续保持。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(5));

        assertEquals("LLM", result.getReplySource());
        assertEquals("感谢认可，我们会继续保持。", result.getReplyContent());
        assertNull(result.getKnowledgeHitJson());
        verify(commentReplyEmbeddingService, never()).embed(any());
        verify(knowledgeVectorStore, never()).searchTopK(any(), any(), any());
        verify(officialTemplateService, never()).selectRandomEnabledTemplate(any());
    }

    @Test
    void chatSystemPromptContainsMeituanComplianceRules() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(false);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(chatCompletion("感谢认可，我们会继续保持。"));

        service.generateReply(shop(), comment(5));

        ArgumentCaptor<ChatCompletionCreateParams> paramsCaptor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(openAiCompatibleChatService).createChatCompletion(any(), any(), any(), paramsCaptor.capture());
        String paramsText = paramsCaptor.getValue().toString();
        assertTrue(paramsText.contains("美团平台商家回复规范"));
        assertTrue(paramsText.contains("不引导用户添加微信"));
        assertTrue(paramsText.contains("不承诺好评返现"));
    }

    @Test
    void ragSuccessConsumesPowerAndRecordsGeneration() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});
        when(knowledgeVectorStore.searchTopK(eq(100L), any(), any())).thenReturn(List.of(hit()));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(chatCompletion("感谢反馈，我们会继续改进餐品和服务。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(3));

        assertEquals("SHOP_RAG", result.getReplySource());
        assertEquals("感谢反馈，我们会继续改进餐品和服务。", result.getReplyContent());
        ArgumentCaptor<PowerConsumeRequestDTO> consumeCaptor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(mtPowerConsumeService).consume(consumeCaptor.capture());
        assertEquals("COMMENT_RAG_REPLY:542543250001", consumeCaptor.getValue().getRequestNo());
        verify(mtCommentReplyGenerationMapper).insert(any(MtCommentReplyGeneration.class));
    }

    @Test
    void lowSimilarityKnowledgeDoesNotEnterPromptContext() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});
        when(knowledgeVectorStore.searchTopK(eq(100L), any(), any())).thenReturn(List.of(lowSimilarityHit()));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(chatCompletion("感谢反馈，我们会认真改进。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(3));

        assertEquals("LLM", result.getReplySource());
        assertNull(result.getKnowledgeHitJson());
        ArgumentCaptor<MtCommentReplyGeneration> generationCaptor = ArgumentCaptor.forClass(MtCommentReplyGeneration.class);
        verify(mtCommentReplyGenerationMapper).insert(generationCaptor.capture());
        assertEquals("LLM", generationCaptor.getValue().getReplySource());
        assertNull(generationCaptor.getValue().getKnowledgeHitJson());
    }

    @Test
    void ragQueryIncludesImageSummaryWhenAvailable() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        CommentImageSummaryResultDTO imageSummary = new CommentImageSummaryResultDTO();
        imageSummary.setStatus("SUCCESS");
        imageSummary.setSummaryText("图片显示包装有汤汁外漏。");
        imageSummary.setPowerAmount(12);
        when(commentImageVisionService.resolveForReply(any(), any(), eq(true))).thenReturn(imageSummary);
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});
        when(knowledgeVectorStore.searchTopK(eq(100L), any(), any())).thenReturn(List.of(hit()));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(chatCompletion("感谢反馈，我们会改进包装。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(3));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(commentReplyEmbeddingService).embed(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("评价图片摘要：图片显示包装有汤汁外漏。"));
        assertEquals("SUCCESS", result.getImageSummaryStatus());
        assertEquals("图片显示包装有汤汁外漏。", result.getImageSummaryText());
        assertEquals(12, result.getImagePowerAmount());
    }

    @Test
    void sensitiveReplyRewritesOnceAndUsesRewrittenReply() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(false);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(chatCompletion("请加微信处理。"))
                .thenReturn(chatCompletion("非常抱歉给您带来不好的体验，我们会认真改进服务。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(1));

        assertEquals("非常抱歉给您带来不好的体验，我们会认真改进服务。", result.getReplyContent());
        assertEquals("LLM", result.getReplySource());
        ArgumentCaptor<MtCommentReplyGeneration> generationCaptor = ArgumentCaptor.forClass(MtCommentReplyGeneration.class);
        verify(mtCommentReplyGenerationMapper).insert(generationCaptor.capture());
        assertEquals(1, generationCaptor.getValue().getSensitiveRewriteCount());
        assertEquals(false, generationCaptor.getValue().getSensitiveFallbackUsed());
        assertTrue(generationCaptor.getValue().getSensitiveHitJson().contains("站外导流违规词"));
        ArgumentCaptor<ChatCompletionCreateParams> paramsCaptor = ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(openAiCompatibleChatService, times(2)).createChatCompletion(any(), any(), any(), paramsCaptor.capture());
        String rewriteParamsText = paramsCaptor.getAllValues().get(1).toString();
        assertTrue(rewriteParamsText.contains("美团平台商家回复规范"));
        assertTrue(rewriteParamsText.contains("不要解释敏感词规则"));
        assertTrue(rewriteParamsText.contains("不要复述被命中的词"));
    }

    @Test
    void sensitiveReplyFallsBackToSafeOfficialTemplateWhenRewriteStillHits() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(false);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(chatCompletion("请加微信处理。"))
                .thenReturn(chatCompletion("还是加微信处理。"));
        when(officialTemplateService.listEnabledTemplates(any())).thenReturn(List.of(official("感谢反馈，我们会认真改进。")));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(1));

        assertEquals("感谢反馈，我们会认真改进。", result.getReplyContent());
        assertEquals("FALLBACK_OFFICIAL", result.getReplySource());
        ArgumentCaptor<MtCommentReplyGeneration> generationCaptor = ArgumentCaptor.forClass(MtCommentReplyGeneration.class);
        verify(mtCommentReplyGenerationMapper).insert(generationCaptor.capture());
        assertEquals(1, generationCaptor.getValue().getSensitiveRewriteCount());
        assertEquals(true, generationCaptor.getValue().getSensitiveFallbackUsed());
        assertEquals("SENSITIVE_WORD_FALLBACK", generationCaptor.getValue().getFallbackReason());
    }

    @Test
    void sensitiveReplyFallsBackToDefaultReplyWhenOfficialTemplatesUnavailable() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(false);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any()))
                .thenReturn(chatCompletion("请加微信处理。"))
                .thenReturn(chatCompletion("还是加微信处理。"));
        when(officialTemplateService.listEnabledTemplates(any())).thenReturn(List.of());

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(1));

        assertEquals("感谢评价", result.getReplyContent());
        assertEquals("FALLBACK_OFFICIAL", result.getReplySource());
    }

    @Test
    void sensitiveKnowledgeHitIsFilteredFromRagContext() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});
        when(knowledgeVectorStore.searchTopK(eq(100L), any(), any())).thenReturn(List.of(sensitiveHit()));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any(), any())).thenReturn(chatCompletion("感谢反馈，我们会认真改进。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(3));

        assertEquals("LLM", result.getReplySource());
        assertNull(result.getKnowledgeHitJson());
        ArgumentCaptor<MtCommentReplyGeneration> generationCaptor = ArgumentCaptor.forClass(MtCommentReplyGeneration.class);
        verify(mtCommentReplyGenerationMapper).insert(generationCaptor.capture());
        assertNull(generationCaptor.getValue().getKnowledgeHitJson());
    }

    @Test
    void ragFailureFallsBackToOfficialTemplate() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(commentReplyEmbeddingService.embed(any())).thenThrow(new RuntimeException("embedding down"));
        when(officialTemplateService.selectRandomEnabledTemplate(any())).thenReturn(official("官方兜底"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(4));

        assertEquals("FALLBACK_OFFICIAL", result.getReplySource());
        assertEquals("官方兜底", result.getReplyContent());
        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any(), any());
    }

    private MtShop shop() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        return shop;
    }

    private MtComment comment(Integer score) {
        MtComment comment = new MtComment();
        comment.setId(9L);
        comment.setCommentId(542543250001L);
        comment.setFoodCommentScore(score);
        comment.setProductScore(score);
        comment.setPackingScore(score);
        comment.setCommentContent("味道不错");
        return comment;
    }

    private MtReplyKnowledgeItem official(String answer) {
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setId(1L);
        item.setAnswer(answer);
        return item;
    }

    private KnowledgeSearchHitDTO hit() {
        MtReplyKnowledgeChunk chunk = new MtReplyKnowledgeChunk();
        chunk.setId(3L);
        chunk.setSourceType("QA");
        chunk.setSourceId(2L);
        chunk.setSentiment("NEUTRAL");
        chunk.setChunkText("问题：出餐慢\n回答：说明会优化出餐速度");
        KnowledgeSearchHitDTO hit = new KnowledgeSearchHitDTO();
        hit.setChunk(chunk);
        hit.setCosineScore(0.9D);
        hit.setScore(0.98D);
        return hit;
    }

    private KnowledgeSearchHitDTO lowSimilarityHit() {
        KnowledgeSearchHitDTO hit = hit();
        hit.setCosineScore(0.3D);
        hit.setScore(0.38D);
        return hit;
    }

    private KnowledgeSearchHitDTO sensitiveHit() {
        KnowledgeSearchHitDTO hit = hit();
        hit.getChunk().setChunkText("问题：配送慢\n回答：请加微信处理");
        hit.setCosineScore(0.95D);
        hit.setScore(1.0D);
        return hit;
    }

    private void addSensitiveWord(String code, String name, String severity, String word) {
        CommentReplySensitiveWordsProperties.Category category = new CommentReplySensitiveWordsProperties.Category();
        category.setCode(code);
        category.setName(name);
        category.setSeverity(severity);
        CommentReplySensitiveWordsProperties.Word item = new CommentReplySensitiveWordsProperties.Word();
        item.setText(word);
        category.getWords().add(item);
        sensitiveWordsProperties.getCategories().add(category);
    }

    private CommentImageSummaryResultDTO emptyImageSummary() {
        CommentImageSummaryResultDTO result = new CommentImageSummaryResultDTO();
        result.setStatus("NONE");
        result.setImageCount(0);
        return result;
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
