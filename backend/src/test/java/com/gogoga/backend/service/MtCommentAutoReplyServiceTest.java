package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.comment.queryCommentList.QueryCommentListRequest;
import com.meituan.sdk.model.wmoperNg.comment.queryCommentList.QueryCommentListResponse;
import com.meituan.sdk.model.wmoperNg.comment.wmoperCommentAddReply.WmoperCommentAddReplyRequest;
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

    private final MtProperties mtProperties = new MtProperties();

    @InjectMocks
    private MtCommentAutoReplyService service;

    @Test
    void syncAndReplyShopSavesCommentAndReplies() throws MtSdkException {
        service = new MtCommentAutoReplyService(
                mtAccessTokenMapper,
                mtShopMapper,
                mtCommentMapper,
                meituanClient,
                mtProperties
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
        MeituanResponse<String> replyResponse = new MeituanResponse<>();
        replyResponse.setCode("OP_SUCCESS");
        replyResponse.setData("ok");

        when(meituanClient.invokeApi(any(QueryCommentListRequest.class), eq("token")))
                .thenReturn(queryResponse)
                .thenReturn(emptyResponse);
        when(mtCommentMapper.selectByCommentId(542543250001L)).thenReturn(null);
        MtComment pending = new MtComment();
        pending.setId(9L);
        pending.setCommentId(542543250001L);
        when(mtCommentMapper.selectPendingAutoReplyByShopId(100L)).thenReturn(List.of(pending));
        when(meituanClient.invokeApi(any(WmoperCommentAddReplyRequest.class), eq("token"))).thenReturn(replyResponse);

        service.syncAndReplyShop(token, LocalDate.of(2026, 6, 19));

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

        ArgumentCaptor<WmoperCommentAddReplyRequest> replyCaptor =
                ArgumentCaptor.forClass(WmoperCommentAddReplyRequest.class);
        verify(meituanClient).invokeApi(replyCaptor.capture(), eq("token"));
        assertEquals(542543250001L, replyCaptor.getValue().getCommentId());
        assertEquals("4211", replyCaptor.getValue().getEPoiId());
        assertEquals("很好", replyCaptor.getValue().getReply());
        verify(mtCommentMapper).markAutoReplySuccess(eq(9L), eq("很好"), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
