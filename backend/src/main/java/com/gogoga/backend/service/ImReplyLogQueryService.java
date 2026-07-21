package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtImConversation;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtImConversationMapper;
import com.gogoga.backend.mapper.MtImReplyQueryMapper;
import com.gogoga.backend.param.ImAutoReplyLogQueryParam;
import com.gogoga.backend.param.ImConversationQueryParam;
import com.gogoga.backend.util.CustomerPhoneExtractor;
import com.gogoga.backend.vo.ImAutoReplyLogVO;
import com.gogoga.backend.vo.ImConversationHistoryPageVO;
import com.gogoga.backend.vo.ImConversationListVO;
import com.gogoga.backend.vo.ImConversationMessageVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 管理后台、总控台和 H5 共用的 IM 回复日志只读查询。
 */
@Service
@RequiredArgsConstructor
public class ImReplyLogQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final MtImReplyQueryMapper queryMapper;
    private final MtImConversationMapper conversationMapper;

    public PageInfo<ImAutoReplyLogVO> listReplyLogs(ImAutoReplyLogQueryParam param, Long forcedShopId) {
        ImAutoReplyLogQueryParam query = param == null ? new ImAutoReplyLogQueryParam() : param;
        if (forcedShopId != null) {
            query.setShopId(forcedShopId);
            query.setShopKeyword(null);
        }
        query.setShopKeyword(StringUtils.trimToNull(query.getShopKeyword()));
        query.setStatus(StringUtils.upperCase(StringUtils.trimToNull(query.getStatus())));
        PageHelper.startPage(normalizePageNum(query.getPageNum()), normalizePageSize(query.getPageSize()));
        List<ImAutoReplyLogVO> rows = queryMapper.selectReplyLogs(query);
        // 查询时实时识别，历史日志不用回刷；正则调整后旧数据也会跟着变。
        for (ImAutoReplyLogVO row : rows) {
            row.setUserPhones(CustomerPhoneExtractor.extract(row.getUserContent()));
        }
        return new PageInfo<>(rows);
    }

    public PageInfo<ImConversationListVO> listConversations(ImConversationQueryParam param, Long forcedShopId) {
        ImConversationQueryParam query = param == null ? new ImConversationQueryParam() : param;
        if (forcedShopId != null) {
            query.setShopId(forcedShopId);
            query.setShopKeyword(null);
        }
        query.setShopKeyword(StringUtils.trimToNull(query.getShopKeyword()));
        PageHelper.startPage(normalizePageNum(query.getPageNum()), normalizePageSize(query.getPageSize()));
        return new PageInfo<>(queryMapper.selectConversations(query));
    }

    public ImConversationHistoryPageVO history(Long conversationId,
                                                Long requiredShopId,
                                                LocalDateTime cursorTime,
                                                Long cursorId,
                                                Integer pageSize) {
        if (conversationId == null) {
            throw new BusinessException(400, "会话ID不能为空");
        }
        if ((cursorTime == null) != (cursorId == null)) {
            throw new BusinessException(400, "历史消息游标参数不完整");
        }
        MtImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (requiredShopId != null && !requiredShopId.equals(conversation.getShopId())) {
            throw new BusinessException(403, "无权查看该会话");
        }
        int limit = normalizePageSize(pageSize);
        List<ImConversationMessageVO> newestFirst = new ArrayList<>(queryMapper.selectHistoryBefore(
                conversationId, cursorTime, cursorId, limit + 1));
        boolean hasMore = newestFirst.size() > limit;
        if (hasMore) {
            newestFirst.remove(newestFirst.size() - 1);
        }
        Collections.reverse(newestFirst);

        ImConversationHistoryPageVO result = new ImConversationHistoryPageVO();
        result.setConversationId(conversationId);
        result.setList(newestFirst);
        result.setHasMore(hasMore);
        if (hasMore && !newestFirst.isEmpty()) {
            ImConversationMessageVO oldest = newestFirst.get(0);
            result.setNextCursorTime(oldest.getEventTime());
            result.setNextCursorId(oldest.getId());
        }
        return result;
    }

    public Long requireConversationShop(Long conversationId) {
        MtImConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(404, "会话不存在");
        }
        return conversation.getShopId();
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
