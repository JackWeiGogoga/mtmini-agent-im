package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentImMessageGenerationResultDTO;
import com.gogoga.backend.entity.mt.MtComment;

/**
 * 差评匿名群聊消息生成服务。
 */
public interface CommentImMessageGenerationService {

    CommentImMessageGenerationResultDTO generate(MtComment comment);
}
