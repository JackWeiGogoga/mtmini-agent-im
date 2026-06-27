package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.service.async.MtCommentAutoReplyTaskPayload;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.comment.queryCommentList.QueryCommentListRequest;
import com.meituan.sdk.model.wmoperNg.comment.queryCommentList.QueryCommentListResponse;
import com.meituan.sdk.model.wmoperNg.comment.wmoperCommentAddReply.WmoperCommentAddReplyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtCommentAutoReplyServiceTest {

    @Mock
    private MtAccessTokenMapper mtAccessTokenMapper;
    @Mock
    private MtShopMapper mtShopMapper;
    @Mock
    private MtCommentMapper mtCommentMapper;
    @Mock
    private MeituanClient meituanClient;
    @Mock
    private MtAsyncTaskService mtAsyncTaskService;
    @Mock
    private ShopOperationSettingsService shopOperationSettingsService;
    @Mock
    private MtCommentReplyContentService mtCommentReplyContentService;
    @Mock
    private CommentLogAnalysisService commentLogAnalysisService;

    private final MtProperties mtProperties = new MtProperties();

    @InjectMocks
    private MtCommentAutoReplyService service;

    @BeforeEach
    void setUp() {
        lenient().when(shopOperationSettingsService.isAutoReplyEnabled(any(Long.class))).thenReturn(true);
    }

    @Test
    void syncAndReplyShopSavesCommentAndEnqueuesReplyTask() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        shop.setPoiId("poi-1");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);

        QueryCommentListResponse comment = new QueryCommentListResponse();
        comment.setCommentId(542543250001L);
        comment.setCommentContent("味道不错");
        comment.setReplyStatus(0);
        MeituanResponse<List<QueryCommentListResponse>> queryResponse = new MeituanResponse<>();
        queryResponse.setCode("OP_SUCCESS");
        queryResponse.setData(List.of(comment));
        MeituanResponse<List<QueryCommentListResponse>> emptyResponse = new MeituanResponse<>();
        emptyResponse.setCode("OP_SUCCESS");
        emptyResponse.setData(List.of());
        when(meituanClient.invokeApi(any(QueryCommentListRequest.class), eq("token")))
                .thenReturn(queryResponse)
                .thenReturn(emptyResponse);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(null);
        MtComment pending = new MtComment();
        pending.setId(9L);
        pending.setCommentId(542543250001L);
        when(mtCommentMapper.selectPendingAutoReplyByShopId(100L)).thenReturn(List.of(pending));

        service.syncAndReplyShopForAuthBackfill(token, LocalDate.of(2026, 6, 19));

        ArgumentCaptor<MtComment> commentCaptor = ArgumentCaptor.forClass(MtComment.class);
        verify(mtCommentMapper).insert(commentCaptor.capture());
        MtComment saved = commentCaptor.getValue();
        assertEquals(100L, saved.getShopId());
        assertEquals("4211", saved.getEpoiId());
        assertEquals(542543250001L, saved.getCommentId());
        assertEquals("20260612", saved.getQueryStartTime());
        assertEquals("20260619", saved.getQueryEndTime());
        assertEquals("PENDING", saved.getAutoReplyStatus());
        assertNotNull(saved.getRawPayloadJson());

        ArgumentCaptor<MtCommentAutoReplyTaskPayload> payloadCaptor =
                ArgumentCaptor.forClass(MtCommentAutoReplyTaskPayload.class);
        verify(mtAsyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.COMMENT_AUTO_REPLY),
                eq("comment:542543250001"),
                eq("100"),
                payloadCaptor.capture(),
                any(LocalDateTime.class),
                eq(3)
        );
        assertEquals(100L, payloadCaptor.getValue().getShopId());
        assertEquals(542543250001L, payloadCaptor.getValue().getCommentId());
        verify(meituanClient, never()).invokeApi(any(WmoperCommentAddReplyRequest.class), eq("token"));
        verify(mtCommentMapper, never()).markAutoReplySuccessWithGeneration(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void scheduledSyncUsesPreviousOneDayWindow() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        token.setOpBizCode("op-100");
        when(mtAccessTokenMapper.selectCommentAutoReplyCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);

        MeituanResponse<List<QueryCommentListResponse>> emptyResponse = new MeituanResponse<>();
        emptyResponse.setCode("OP_SUCCESS");
        emptyResponse.setData(List.of());
        when(meituanClient.invokeApi(any(QueryCommentListRequest.class), eq("token"))).thenReturn(emptyResponse);
        when(mtCommentMapper.selectPendingAutoReplyByShopId(100L)).thenReturn(List.of());

        service.syncAndReplyRecentUnrepliedComments();

        ArgumentCaptor<QueryCommentListRequest> requestCaptor = ArgumentCaptor.forClass(QueryCommentListRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(1).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                requestCaptor.getValue().getStartTime());
        assertEquals(today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                requestCaptor.getValue().getEndTime());
    }

    @Test
    void scheduledSyncSkipsShopWhenAutoReplyDisabled() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        token.setOpBizCode("op-100");
        when(mtAccessTokenMapper.selectCommentAutoReplyCandidates(any(LocalDateTime.class))).thenReturn(List.of(token));

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(shopOperationSettingsService.isAutoReplyEnabled(100L)).thenReturn(false);

        service.syncAndReplyRecentUnrepliedComments();

        verify(meituanClient, never()).invokeApi(any(QueryCommentListRequest.class), eq("token"));
        verify(mtCommentMapper, never()).selectPendingAutoReplyByShopId(100L);
    }

    @Test
    void authBackfillUsesConfiguredSevenDayWindow() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectByShopId(100L)).thenReturn(token);

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);

        MeituanResponse<List<QueryCommentListResponse>> emptyResponse = new MeituanResponse<>();
        emptyResponse.setCode("OP_SUCCESS");
        emptyResponse.setData(List.of());
        when(meituanClient.invokeApi(any(QueryCommentListRequest.class), eq("token"))).thenReturn(emptyResponse);
        when(mtCommentMapper.selectPendingAutoReplyByShopId(100L)).thenReturn(List.of());

        MtAuthPostProcessTaskPayload payload = new MtAuthPostProcessTaskPayload();
        payload.setShopId(100L);

        service.executeAuthCommentBackfillTask(payload);

        ArgumentCaptor<QueryCommentListRequest> requestCaptor = ArgumentCaptor.forClass(QueryCommentListRequest.class);
        verify(meituanClient).invokeApi(requestCaptor.capture(), eq("token"));
        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(7).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                requestCaptor.getValue().getStartTime());
        assertEquals(today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                requestCaptor.getValue().getEndTime());
    }

    @Test
    void executeCommentAutoReplyTaskRepliesSingleComment() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtComment comment = new MtComment();
        comment.setId(9L);
        comment.setCommentId(542543250001L);
        comment.setReplyStatus(0);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);

        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectByShopId(100L)).thenReturn(token);

        MeituanResponse<String> replyResponse = new MeituanResponse<>();
        replyResponse.setCode("OP_SUCCESS");
        replyResponse.setData("ok");
        when(meituanClient.invokeApi(any(WmoperCommentAddReplyRequest.class), eq("token"))).thenReturn(replyResponse);
        CommentReplyContentResultDTO replyResult = new CommentReplyContentResultDTO();
        replyResult.setShouldReply(true);
        replyResult.setReplyContent("很好");
        replyResult.setReplySource("OFFICIAL_RANDOM");
        replyResult.setSentiment("GOOD");
        replyResult.setGenerationId(1L);
        replyResult.setReplyDurationMs(123);
        when(mtCommentReplyContentService.generateReply(shop, comment)).thenReturn(replyResult);

        MtCommentAutoReplyTaskPayload payload = new MtCommentAutoReplyTaskPayload();
        payload.setShopId(100L);
        payload.setCommentId(542543250001L);

        service.executeCommentAutoReplyTask(payload);

        ArgumentCaptor<WmoperCommentAddReplyRequest> replyCaptor =
                ArgumentCaptor.forClass(WmoperCommentAddReplyRequest.class);
        verify(meituanClient).invokeApi(replyCaptor.capture(), eq("token"));
        assertEquals(542543250001L, replyCaptor.getValue().getCommentId());
        assertEquals("4211", replyCaptor.getValue().getEPoiId());
        assertEquals("很好", replyCaptor.getValue().getReply());
        verify(mtCommentMapper).markAutoReplySuccessWithGeneration(
                eq(9L),
                eq("很好"),
                any(LocalDateTime.class),
                eq("OFFICIAL_RANDOM"),
                eq("GOOD"),
                eq(1L),
                any(),
                any(),
                any(),
                eq(123),
                any(LocalDateTime.class)
        );
    }

    @Test
    void executeCommentAutoReplyTaskEnqueuesComplaintForBadCommentWhenEnabled() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtComment comment = new MtComment();
        comment.setId(9L);
        comment.setShopId(100L);
        comment.setCommentId(542543250001L);
        comment.setReplyStatus(0);
        comment.setProductScore(2);
        comment.setPackingScore(5);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(shopOperationSettingsService.isNegativeCommentAppealEnabled(100L)).thenReturn(true);

        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectByShopId(100L)).thenReturn(token);

        MeituanResponse<String> replyResponse = new MeituanResponse<>();
        replyResponse.setCode("OP_SUCCESS");
        replyResponse.setData("ok");
        when(meituanClient.invokeApi(any(WmoperCommentAddReplyRequest.class), eq("token"))).thenReturn(replyResponse);
        CommentReplyContentResultDTO replyResult = new CommentReplyContentResultDTO();
        replyResult.setShouldReply(true);
        replyResult.setReplyContent("抱歉给您带来不好的体验，我们会持续改进。");
        replyResult.setReplySource("LLM");
        replyResult.setSentiment("BAD");
        replyResult.setReplyDurationMs(123);
        when(mtCommentReplyContentService.generateReply(shop, comment)).thenReturn(replyResult);

        MtCommentAutoReplyTaskPayload payload = new MtCommentAutoReplyTaskPayload();
        payload.setShopId(100L);
        payload.setCommentId(542543250001L);

        service.executeCommentAutoReplyTask(payload);

        verify(mtAsyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.COMMENT_AUTO_COMPLAINT),
                eq("comment-complaint:542543250001"),
                eq("100"),
                any(),
                any(LocalDateTime.class),
                eq(3)
        );
    }

    @Test
    void executeCommentAutoReplyTaskSkipsWhenContentServiceDeclinesReply() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties,
                mtAsyncTaskService,
                shopOperationSettingsService,
                mtCommentReplyContentService,
                commentLogAnalysisService
        );
        MtComment comment = new MtComment();
        comment.setId(9L);
        comment.setCommentId(542543250001L);
        comment.setReplyStatus(0);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(comment);

        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setEpoiId("4211");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);

        MtAccessToken token = new MtAccessToken();
        token.setShopId(100L);
        token.setAccessToken("token");
        when(mtAccessTokenMapper.selectByShopId(100L)).thenReturn(token);

        CommentReplyContentResultDTO replyResult = new CommentReplyContentResultDTO();
        replyResult.setShouldReply(false);
        replyResult.setSkipReason("PRODUCT_PACKING_SCORE_INVALID");
        when(mtCommentReplyContentService.generateReply(shop, comment)).thenReturn(replyResult);

        MtCommentAutoReplyTaskPayload payload = new MtCommentAutoReplyTaskPayload();
        payload.setShopId(100L);
        payload.setCommentId(542543250001L);

        service.executeCommentAutoReplyTask(payload);

        verify(meituanClient, never()).invokeApi(any(WmoperCommentAddReplyRequest.class), eq("token"));
        verify(mtCommentMapper).markAutoReplySkipped(eq(9L), eq("PRODUCT_PACKING_SCORE_INVALID"), any(LocalDateTime.class));
    }
}
