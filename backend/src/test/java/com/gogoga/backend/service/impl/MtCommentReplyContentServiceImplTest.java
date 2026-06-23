package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.dto.KnowledgeSearchHitDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentReplyGeneration;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtCommentReplyGenerationMapper;
import com.gogoga.backend.service.CommentImageVisionService;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CommentReplyOfficialTemplateService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtPowerAccountService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletion;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    private MtProperties mtProperties;
    private MtCommentReplyContentServiceImpl service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        mtProperties.getComment().setDefaultReply("感谢评价");
        mtProperties.getComment().getKnowledge().setModelApiKey("key");
        service = new MtCommentReplyContentServiceImpl(
                mtProperties,
                officialTemplateService,
                commentImageVisionService,
                commentReplyEmbeddingService,
                knowledgeVectorStore,
                openAiCompatibleChatService,
                mtPowerAccountService,
                mtPowerConsumeService,
                mtCommentReplyGenerationMapper
        );
        lenient().doAnswer(invocation -> {
            MtCommentReplyGeneration generation = invocation.getArgument(0);
            generation.setId(99L);
            return 1;
        }).when(mtCommentReplyGenerationMapper).insert(any(MtCommentReplyGeneration.class));
        lenient().when(commentImageVisionService.resolveForReply(any(), any(), eq(true))).thenReturn(emptyImageSummary());
    }

    @Test
    void invalidFoodCommentScoreSkipsReplyWithoutGeneration() {
        MtComment comment = comment(6);

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment);

        assertFalse(result.isShouldReply());
        assertEquals("FOOD_COMMENT_SCORE_INVALID", result.getSkipReason());
        verify(mtCommentReplyGenerationMapper, never()).insert(any());
    }

    @Test
    void insufficientBalanceFallsBackToOfficialWithoutModelCall() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(100000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(officialTemplateService.selectRandomEnabledTemplate(any())).thenReturn(official("官方差评回复"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(1));

        assertTrue(result.isShouldReply());
        assertEquals("官方差评回复", result.getReplyContent());
        assertEquals("FALLBACK_OFFICIAL", result.getReplySource());
        verify(commentReplyEmbeddingService, never()).embed(any());
        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any());
    }

    @Test
    void ragSuccessConsumesPowerAndRecordsGeneration() {
        when(knowledgeVectorStore.hasShopKnowledge(100L)).thenReturn(true);
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(200000);
        when(mtPowerAccountService.getAccount(100L)).thenReturn(account);
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});
        when(knowledgeVectorStore.searchTopK(eq(100L), any(), any())).thenReturn(List.of(hit()));
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any())).thenReturn(chatCompletion("感谢反馈，我们会继续改进餐品和服务。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(3));

        assertEquals("SHOP_RAG", result.getReplySource());
        assertEquals("感谢反馈，我们会继续改进餐品和服务。", result.getReplyContent());
        ArgumentCaptor<PowerConsumeRequestDTO> consumeCaptor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(mtPowerConsumeService).consume(consumeCaptor.capture());
        assertEquals("COMMENT_RAG_REPLY:542543250001", consumeCaptor.getValue().getRequestNo());
        verify(mtCommentReplyGenerationMapper).insert(any(MtCommentReplyGeneration.class));
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
        when(openAiCompatibleChatService.createChatCompletion(any(), any(), any())).thenReturn(chatCompletion("感谢反馈，我们会改进包装。"));

        CommentReplyContentResultDTO result = service.generateReply(shop(), comment(3));

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(commentReplyEmbeddingService).embed(queryCaptor.capture());
        assertTrue(queryCaptor.getValue().contains("评价图片摘要：图片显示包装有汤汁外漏。"));
        assertEquals("SUCCESS", result.getImageSummaryStatus());
        assertEquals("图片显示包装有汤汁外漏。", result.getImageSummaryText());
        assertEquals(12, result.getImagePowerAmount());
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
        verify(openAiCompatibleChatService, never()).createChatCompletion(any(), any(), any());
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
