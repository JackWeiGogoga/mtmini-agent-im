package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;

/**
 * 评价图片多模态识别服务。
 */
public interface CommentImageVisionService {

    CommentImageSummaryResultDTO resolveForReply(MtShop shop, MtComment comment, boolean chargePower);
}
