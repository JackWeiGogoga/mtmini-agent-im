package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtReplyKnowledgeItemMapper;
import com.gogoga.backend.service.CommentReplyOfficialTemplateService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class CommentReplyOfficialTemplateServiceImpl implements CommentReplyOfficialTemplateService {

    private final MtReplyKnowledgeItemMapper mtReplyKnowledgeItemMapper;
    private final MtProperties mtProperties;

    @Override
    public MtReplyKnowledgeItem selectRandomEnabledTemplate(CommentReplySentimentEnum sentiment) {
        if (sentiment == null || sentiment == CommentReplySentimentEnum.UNKNOWN) {
            return null;
        }
        List<MtReplyKnowledgeItem> templates = mtReplyKnowledgeItemMapper.selectOfficialTemplates(sentiment.name());
        if (templates == null || templates.isEmpty()) {
            return null;
        }
        return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
    }

    @Override
    public String selectRandomReplyOrDefault(CommentReplySentimentEnum sentiment) {
        MtReplyKnowledgeItem template = selectRandomEnabledTemplate(sentiment);
        if (template != null && StringUtils.isNotBlank(template.getAnswer())) {
            return StringUtils.trim(template.getAnswer());
        }
        return StringUtils.defaultIfBlank(mtProperties.getComment().getDefaultReply(), "很好");
    }
}
