package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.CommentProcessingLogMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.param.CommentProcessingLogQueryParam;
import com.gogoga.backend.util.CommentImageUrlUtil;
import com.gogoga.backend.vo.CommentProcessingLogVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 管理后台评价处理日志查询服务。
 */
@Service
@RequiredArgsConstructor
public class CommentProcessingLogService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_IMAGE_COUNT = 20;

    private final CommentProcessingLogMapper commentProcessingLogMapper;
    private final MtCommentMapper mtCommentMapper;
    private final AdminDataScopeService adminDataScopeService;

    public PageInfo<CommentProcessingLogVO> list(CommentProcessingLogQueryParam query) {
        CommentProcessingLogQueryParam normalized = normalize(query);
        PageHelper.startPage(normalized.getPageNum(), normalized.getPageSize());
        return new PageInfo<>(commentProcessingLogMapper.selectForAdmin(normalized, resolveVisibleUserIds()));
    }

    public CommentProcessingLogVO getDetail(Long id) {
        if (id == null) {
            throw new BusinessException(400, "评价日志ID不能为空");
        }
        CommentProcessingLogVO detail = commentProcessingLogMapper.selectByIdForAdmin(id, resolveVisibleUserIds());
        if (detail == null) {
            throw new BusinessException(404, "评价处理日志不存在或无权访问");
        }
        MtComment comment = mtCommentMapper.selectById(id);
        if (comment != null) {
            detail.setImageUrls(CommentImageUrlUtil.parseImageUrls(comment.getCommentPictures(), MAX_IMAGE_COUNT));
        }
        return detail;
    }

    private CommentProcessingLogQueryParam normalize(CommentProcessingLogQueryParam source) {
        CommentProcessingLogQueryParam result = source == null ? new CommentProcessingLogQueryParam() : source;
        result.setPageNum(result.getPageNum() == null || result.getPageNum() < 1 ? 1 : result.getPageNum());
        int pageSize = result.getPageSize() == null || result.getPageSize() < 1 ? DEFAULT_PAGE_SIZE : result.getPageSize();
        result.setPageSize(Math.min(pageSize, MAX_PAGE_SIZE));
        result.setCommentTimeFrom(StringUtils.trimToNull(result.getCommentTimeFrom()));
        result.setCommentTimeTo(StringUtils.trimToNull(result.getCommentTimeTo()));
        result.setPoiId(StringUtils.trimToNull(result.getPoiId()));
        result.setShopKeyword(StringUtils.trimToNull(result.getShopKeyword()));
        result.setKeyword(StringUtils.trimToNull(result.getKeyword()));
        result.setSentiment(upper(result.getSentiment()));
        result.setAutoReplyStatus(upper(result.getAutoReplyStatus()));
        result.setComplaintStatus(upper(result.getComplaintStatus()));
        result.setImFollowUpStatus(upper(result.getImFollowUpStatus()));
        return result;
    }

    private List<String> resolveVisibleUserIds() {
        if (adminDataScopeService.getCurrentScopeType() == AdminDataScopeTypeEnum.ALL) {
            return null;
        }
        List<String> ids = new ArrayList<>(adminDataScopeService.getCurrentVisibleUserIds());
        return ids.isEmpty() ? List.of("__NO_VISIBLE_USER__") : ids;
    }

    private String upper(String value) {
        String normalized = StringUtils.trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
