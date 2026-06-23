package com.gogoga.backend.controller;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.param.CommentReplyTestParam;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.service.CommentReplyKnowledgeService;
import com.gogoga.backend.service.CosStorageService;
import com.gogoga.backend.service.MtCommentReplyContentService;
import com.gogoga.backend.service.UserShopBindingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMerchantKnowledgeControllerTest {

    @Mock
    private UserShopBindingService userShopBindingService;
    @Mock
    private CommentReplyKnowledgeService commentReplyKnowledgeService;
    @Mock
    private CommentReplyKnowledgeDocumentService commentReplyKnowledgeDocumentService;
    @Mock
    private MtCommentReplyContentService mtCommentReplyContentService;
    @Mock
    private CosStorageService cosStorageService;

    @Test
    void testReplyPassesImageUrlsAndSimulatedSummaryToReplyService() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(userShopBindingService.requireCurrentUserVisibleShop(100L, null)).thenReturn(shop);
        when(mtCommentReplyContentService.previewReply(eq(shop), any())).thenReturn(new CommentReplyContentResultDTO());
        AdminMerchantKnowledgeController controller = new AdminMerchantKnowledgeController(
                userShopBindingService,
                commentReplyKnowledgeService,
                commentReplyKnowledgeDocumentService,
                mtCommentReplyContentService,
                cosStorageService,
                new MtProperties()
        );

        CommentReplyTestParam param = new CommentReplyTestParam();
        param.setFoodCommentScore(5);
        param.setCommentContent("很好吃");
        param.setCommentPictures(List.of("https://example.com/a.jpg", "invalid", "https://example.com/b.jpg"));
        param.setImageSummaryText("图片显示包装完整。");
        controller.testReply(100L, param);

        ArgumentCaptor<MtComment> commentCaptor = ArgumentCaptor.forClass(MtComment.class);
        verify(mtCommentReplyContentService).previewReply(eq(shop), commentCaptor.capture());
        assertEquals("https://example.com/a.jpg,https://example.com/b.jpg", commentCaptor.getValue().getCommentPictures());
        assertEquals("图片显示包装完整。", commentCaptor.getValue().getImageSummaryText());
    }
}
