package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.util.CommentImageUrlUtil;
import com.gogoga.backend.vo.MtH5CommentReplyLogVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * H5 评价自动回复日志服务。
 */
@Service
@RequiredArgsConstructor
public class MtH5CommentReplyLogService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_IMAGE_COUNT = 20;
    private static final String DEFAULT_ANALYSIS_TEXT = "AI 分析结果";

    private final MtCommentMapper mtCommentMapper;

    public PageInfo<MtH5CommentReplyLogVO> listLogs(MtShop shop, Integer pageNum, Integer pageSize) {
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<MtComment> comments = mtCommentMapper.selectLogsByShopId(shop.getId());
        PageInfo<MtComment> commentPageInfo = new PageInfo<>(comments);

        List<MtH5CommentReplyLogVO> rows = comments.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        PageInfo<MtH5CommentReplyLogVO> pageInfo = new PageInfo<>();
        pageInfo.setList(rows);
        pageInfo.setTotal(commentPageInfo.getTotal());
        pageInfo.setPageNum(commentPageInfo.getPageNum());
        pageInfo.setPageSize(commentPageInfo.getPageSize());
        pageInfo.setPages(commentPageInfo.getPages());
        pageInfo.setIsFirstPage(commentPageInfo.isIsFirstPage());
        pageInfo.setIsLastPage(commentPageInfo.isIsLastPage());
        pageInfo.setHasPreviousPage(commentPageInfo.isHasPreviousPage());
        pageInfo.setHasNextPage(commentPageInfo.isHasNextPage());
        return pageInfo;
    }

    private MtH5CommentReplyLogVO toVO(MtComment comment) {
        MtH5CommentReplyLogVO vo = new MtH5CommentReplyLogVO();
        vo.setId(comment.getId());
        vo.setCommentId(comment.getCommentId());
        vo.setCommentTime(comment.getCommentTime());
        vo.setReplyTime(comment.getAutoReplyAt());
        vo.setSentiment(resolveSentiment(comment));
        vo.setScore(CommentReplySentimentEnum.resolveProductPackingScore(comment.getProductScore(), comment.getPackingScore()));
        vo.setCustomerMessage(StringUtils.trimToNull(comment.getCommentContent()));
        vo.setAddComment(StringUtils.trimToNull(comment.getAddComment()));
        vo.setAddCommentTime(StringUtils.trimToNull(comment.getAddCommentTime()));
        vo.setImageUrls(CommentImageUrlUtil.parseImageUrls(comment.getCommentPictures(), MAX_IMAGE_COUNT));
        vo.setAiReplyText(comment.getAutoReplyContent());
        vo.setAnalysisText(StringUtils.defaultIfBlank(comment.getAnalysisText(), DEFAULT_ANALYSIS_TEXT));
        vo.setAutoReplyStatus(comment.getAutoReplyStatus());
        return vo;
    }

    private String resolveSentiment(MtComment comment) {
        String storedSentiment = StringUtils.trimToNull(comment.getAutoReplySentiment());
        if (storedSentiment != null) {
            try {
                CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.valueOf(storedSentiment);
                if (sentiment == CommentReplySentimentEnum.GOOD
                        || sentiment == CommentReplySentimentEnum.NEUTRAL
                        || sentiment == CommentReplySentimentEnum.BAD) {
                    return sentiment.name();
                }
            } catch (IllegalArgumentException ignored) {
                // 历史脏数据按评分兜底。
            }
        }
        CommentReplySentimentEnum fromScore = CommentReplySentimentEnum.fromProductAndPackingScore(
                comment.getProductScore(),
                comment.getPackingScore()
        );
        if (fromScore == CommentReplySentimentEnum.GOOD || fromScore == CommentReplySentimentEnum.BAD) {
            return fromScore.name();
        }
        return CommentReplySentimentEnum.NEUTRAL.name();
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
