package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.entity.mt.MtPromotionLogReadState;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtPromotionBidRecordMapper;
import com.gogoga.backend.mapper.MtPromotionLogReadStateMapper;
import com.gogoga.backend.service.promotion.PromotionDecisionDetail;
import com.gogoga.backend.service.promotion.PromotionLogMessageBuilder;
import com.gogoga.backend.util.JsonUtil;
import com.gogoga.backend.vo.MtH5PromotionLogVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 推广日志服务
 *
 * @author system
 * @since 2026-03-26
 */
@Service
@RequiredArgsConstructor
public class MtPromotionLogService {

    private static final int DEFAULT_RETENTION_DAYS = 7;

    private final MtPromotionBidRecordMapper mtPromotionBidRecordMapper;
    private final MtPromotionLogReadStateMapper mtPromotionLogReadStateMapper;
    private final PromotionLogMessageBuilder promotionLogMessageBuilder;

    /**
     * 分页查询当前门店推广日志
     *
     * @param shop 当前门店
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 分页日志
     */
    public PageInfo<MtH5PromotionLogVO> listLogs(MtShop shop, Integer pageNum, Integer pageSize) {
        LocalDate bizDateFrom = LocalDate.now().minusDays(DEFAULT_RETENTION_DAYS - 1L);
        PageHelper.startPage(pageNum, pageSize);
        List<MtPromotionBidRecord> records = mtPromotionBidRecordMapper.selectRecentByShopId(shop.getId(), bizDateFrom);
        PageInfo<MtPromotionBidRecord> recordPageInfo = new PageInfo<>(records);

        List<MtH5PromotionLogVO> logVOList = records.stream()
                .map(this::convertToLogVO)
                .collect(Collectors.toList());

        PageInfo<MtH5PromotionLogVO> pageInfo = new PageInfo<>();
        pageInfo.setList(logVOList);
        pageInfo.setTotal(recordPageInfo.getTotal());
        pageInfo.setPageNum(recordPageInfo.getPageNum());
        pageInfo.setPageSize(recordPageInfo.getPageSize());
        pageInfo.setPages(recordPageInfo.getPages());
        pageInfo.setIsFirstPage(recordPageInfo.isIsFirstPage());
        pageInfo.setIsLastPage(recordPageInfo.isIsLastPage());
        pageInfo.setHasPreviousPage(recordPageInfo.isHasPreviousPage());
        pageInfo.setHasNextPage(recordPageInfo.isHasNextPage());
        return pageInfo;
    }

    public boolean hasUnreadLogs(MtShop shop) {
        if (shop == null || shop.getId() == null) {
            return false;
        }
        Long latestVisibleLogId = mtPromotionBidRecordMapper.selectLatestVisibleLogIdByShopId(shop.getId());
        if (latestVisibleLogId == null) {
            return false;
        }
        MtPromotionLogReadState readState = mtPromotionLogReadStateMapper.selectByShopId(shop.getId());
        Long lastReadRecordId = readState == null ? null : readState.getLastReadRecordId();
        return lastReadRecordId == null || latestVisibleLogId > lastReadRecordId;
    }

    public void markCurrentShopLogsRead(MtShop shop) {
        if (shop == null || shop.getId() == null) {
            return;
        }
        Long latestVisibleLogId = mtPromotionBidRecordMapper.selectLatestVisibleLogIdByShopId(shop.getId());
        if (latestVisibleLogId == null) {
            return;
        }
        mtPromotionLogReadStateMapper.upsertReadState(shop.getId(), latestVisibleLogId, java.time.LocalDateTime.now());
    }

    private MtH5PromotionLogVO convertToLogVO(MtPromotionBidRecord record) {
        MtH5PromotionLogVO vo = new MtH5PromotionLogVO();
        vo.setSlotTime(record.getSlotTime());
        vo.setStatus(record.getExecuteStatus());
        vo.setType(resolveType(record));
        vo.setPrice(resolvePrice(record));
        vo.setMessage(promotionLogMessageBuilder.buildPlainText(record));
        boolean markdownMissing = StringUtils.isBlank(record.getMarkdownMessage());
        vo.setMarkdownMessage(markdownMissing
                ? promotionLogMessageBuilder.buildFallbackMarkdown(record)
                : record.getMarkdownMessage());
        vo.setMarkdownStatus(resolveMarkdownStatus(record, markdownMissing));
        return vo;
    }

    private String resolveMarkdownStatus(MtPromotionBidRecord record, boolean markdownMissing) {
        if (StringUtils.isNotBlank(record.getLogMarkdownStatus())) {
            return record.getLogMarkdownStatus();
        }
        if (markdownMissing) {
            return "PENDING";
        }
        PromotionDecisionDetail detail = JsonUtil.toBean(record.getDecisionDetailJson(), PromotionDecisionDetail.class);
        if (detail != null && "fallback".equalsIgnoreCase(detail.getLogMarkdownSource())) {
            return "FALLBACK";
        }
        return "READY";
    }

    private String resolveType(MtPromotionBidRecord record) {
        if ("INCREASE".equals(record.getDecisionAction()) || "INIT".equals(record.getDecisionAction())) {
            return "increase";
        }
        if ("DECREASE".equals(record.getDecisionAction())) {
            return "decrease";
        }
        return "steady";
    }

    private String resolvePrice(MtPromotionBidRecord record) {
        if (record.getNextBidAmount() != null) {
            return "出价" + stripTrailingZeros(record.getNextBidAmount());
        }
        if (promotionLogMessageBuilder.isReminderRecord(record)) {
            return "提醒";
        }
        if ("SKIPPED".equals(record.getExecuteStatus())) {
            return "已跳过";
        }
        if ("FAILED".equals(record.getExecuteStatus())) {
            return "执行失败";
        }
        return "无出价";
    }

    private String stripTrailingZeros(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
