package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.param.AdminCommentReplyOfficialKnowledgeParam;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;
import com.gogoga.backend.vo.CommentReplyOfficialTemplateImportVO;
import org.springframework.web.multipart.MultipartFile;

public interface AdminCommentReplyKnowledgeService {

    PageInfo<CommentReplyKnowledgeItemVO> listOfficialTemplates(String sentiment, Integer pageNum, Integer pageSize);

    CommentReplyKnowledgeItemVO createOfficialTemplate(AdminCommentReplyOfficialKnowledgeParam param);

    CommentReplyKnowledgeItemVO updateOfficialTemplate(Long id, AdminCommentReplyOfficialKnowledgeParam param);

    void deleteOfficialTemplate(Long id);

    CommentReplyOfficialTemplateImportVO importOfficialTemplates(MultipartFile file);
}
