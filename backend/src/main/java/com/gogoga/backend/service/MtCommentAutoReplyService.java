package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.async.MtCommentAutoReplyTaskPayload;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.comment.queryCommentList.QueryCommentListRequest;
import com.meituan.sdk.model.wmoperNg.comment.queryCommentList.QueryCommentListResponse;
import com.meituan.sdk.model.wmoperNg.comment.wmoperCommentAddReply.WmoperCommentAddReplyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 美团评价自动回复服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtCommentAutoReplyService {

    private static final int REPLY_STATUS_UNREPLIED = 0;
    private static final int MAX_PAGE_SIZE = 20;
    private static final DateTimeFormatter COMMENT_QUERY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MtShopMapper mtShopMapper;
    private final MtCommentMapper mtCommentMapper;
    private final MeituanClient meituanClient;
    private final MtProperties mtProperties;
    private final MtAsyncTaskService mtAsyncTaskService;

    public void syncAndReplyRecentUnrepliedComments() {
        LocalDateTime now = LocalDateTime.now();
        List<MtAccessToken> candidates = mtAccessTokenMapper.selectCommentAutoReplyCandidates(now);
        log.info("评价自动回复任务开始，candidateCount={}", candidates.size());
        for (MtAccessToken token : candidates) {
            try {
                syncAndReplyShop(token, now.toLocalDate());
            } catch (RuntimeException e) {
                log.error("评价自动回复门店处理失败，shopId={}, opBizCode={}", token.getShopId(), token.getOpBizCode(), e);
            }
        }
        log.info("评价自动回复任务完成，candidateCount={}", candidates.size());
    }

    void syncAndReplyShop(MtAccessToken token, LocalDate currentDate) {
        if (token == null || token.getShopId() == null || StringUtils.isBlank(token.getAccessToken())) {
            return;
        }
        MtShop shop = mtShopMapper.selectById(token.getShopId());
        if (shop == null || StringUtils.isBlank(shop.getEpoiId())) {
            log.warn("评价自动回复跳过门店，原因=缺少门店或 epoiId, shopId={}", token.getShopId());
            return;
        }
        LocalDate endDate = currentDate == null ? LocalDate.now() : currentDate;
        LocalDate startDate = endDate.minusDays(Math.max(mtProperties.getComment().getLookbackDays(), 1));
        syncUnrepliedComments(token, shop, startDate, endDate);
        enqueuePendingCommentReplyTasks(shop);
    }

    private void syncUnrepliedComments(MtAccessToken token, MtShop shop, LocalDate startDate, LocalDate endDate) {
        String startTime = COMMENT_QUERY_DATE_FORMATTER.format(startDate);
        String endTime = COMMENT_QUERY_DATE_FORMATTER.format(endDate);
        int pageSize = Math.min(Math.max(mtProperties.getComment().getPageSize(), 1), MAX_PAGE_SIZE);
        int offset = 0;
        while (true) {
            QueryCommentListRequest request = new QueryCommentListRequest();
            request.setStartTime(startTime);
            request.setEndTime(endTime);
            request.setOffset(offset);
            request.setLimit(pageSize);
            request.setReplyStatus(REPLY_STATUS_UNREPLIED);
            MeituanResponse<List<QueryCommentListResponse>> response;
            try {
                response = meituanClient.invokeApi(request, token.getAccessToken());
            } catch (MtSdkException e) {
                throw new IllegalStateException("查询门店评价失败：" + e.getMessage(), e);
            }
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("查询门店评价失败：" + (response == null ? "EMPTY_RESPONSE" : response.getMsg()));
            }
            List<QueryCommentListResponse> comments = response.getData();
            if (comments == null || comments.isEmpty()) {
                break;
            }
            for (QueryCommentListResponse comment : comments) {
                upsertComment(shop, comment, startTime, endTime);
            }
            if (comments.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
    }

    private void upsertComment(MtShop shop, QueryCommentListResponse response, String startTime, String endTime) {
        if (response == null || response.getCommentId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MtComment comment = toEntity(shop, response, startTime, endTime, now);
        MtComment existing = mtCommentMapper.selectByCommentId(response.getCommentId());
        if (existing == null) {
            mtCommentMapper.insert(comment);
            return;
        }
        comment.setId(existing.getId());
        mtCommentMapper.updateFromQuery(comment);
    }

    private void enqueuePendingCommentReplyTasks(MtShop shop) {
        List<MtComment> comments = mtCommentMapper.selectPendingAutoReplyByShopId(shop.getId());
        for (MtComment comment : comments) {
            if (comment == null || comment.getCommentId() == null) {
                continue;
            }
            MtCommentAutoReplyTaskPayload payload = new MtCommentAutoReplyTaskPayload();
            payload.setShopId(shop.getId());
            payload.setCommentId(comment.getCommentId());
            mtAsyncTaskService.enqueueTask(
                    MtAsyncTaskTypeEnum.COMMENT_AUTO_REPLY,
                    buildCommentReplyTaskBizKey(comment.getCommentId()),
                    String.valueOf(shop.getId()),
                    payload,
                    LocalDateTime.now(),
                    mtProperties.getAsyncTask().getCommentAutoReply().getMaxRetryCount()
            );
        }
    }

    public void executeCommentAutoReplyTask(MtCommentAutoReplyTaskPayload payload) {
        if (payload == null || payload.getShopId() == null || payload.getCommentId() == null) {
            throw new IllegalArgumentException("COMMENT_AUTO_REPLY_PAYLOAD_INVALID");
        }
        MtComment comment = mtCommentMapper.selectByCommentId(payload.getCommentId());
        if (comment == null) {
            log.warn("评价自动回复任务跳过，原因=评价不存在，shopId={}, commentId={}",
                    payload.getShopId(), payload.getCommentId());
            return;
        }
        if (Integer.valueOf(1).equals(comment.getReplyStatus()) || StringUtils.equals(comment.getAutoReplyStatus(), "SUCCESS")) {
            log.info("评价自动回复任务跳过，原因=评价已回复，shopId={}, commentId={}",
                    payload.getShopId(), payload.getCommentId());
            return;
        }
        MtShop shop = mtShopMapper.selectById(payload.getShopId());
        if (shop == null || StringUtils.isBlank(shop.getEpoiId())) {
            throw new IllegalStateException("COMMENT_AUTO_REPLY_SHOP_INVALID");
        }
        MtAccessToken token = mtAccessTokenMapper.selectByShopId(payload.getShopId());
        if (token == null || StringUtils.isBlank(token.getAccessToken())) {
            throw new IllegalStateException("COMMENT_AUTO_REPLY_TOKEN_MISSING");
        }
        String reply = StringUtils.defaultIfBlank(mtProperties.getComment().getDefaultReply(), "很好");
        try {
            WmoperCommentAddReplyRequest request = new WmoperCommentAddReplyRequest();
            request.setCommentId(comment.getCommentId());
            request.setEPoiId(shop.getEpoiId());
            request.setReply(reply);
            MeituanResponse<String> response = meituanClient.invokeApi(request, token.getAccessToken());
            if (response == null || !response.isSuccess()) {
                String error = response == null ? "EMPTY_RESPONSE" : response.getMsg();
                mtCommentMapper.markAutoReplyFailed(comment.getId(), StringUtils.left(error, 1000), LocalDateTime.now());
                throw new IllegalStateException("评价自动回复失败：" + error);
            }
            LocalDateTime now = LocalDateTime.now();
            mtCommentMapper.markAutoReplySuccess(comment.getId(), reply, now, now);
        } catch (MtSdkException e) {
            mtCommentMapper.markAutoReplyFailed(comment.getId(), StringUtils.left(e.getMessage(), 1000), LocalDateTime.now());
            log.warn("评价自动回复失败，shopId={}, commentId={}", shop.getId(), comment.getCommentId(), e);
            throw new IllegalStateException("评价自动回复失败：" + e.getMessage(), e);
        }
    }

    private MtComment toEntity(MtShop shop,
                               QueryCommentListResponse response,
                               String startTime,
                               String endTime,
                               LocalDateTime now) {
        MtComment comment = new MtComment();
        comment.setShopId(shop.getId());
        comment.setEpoiId(shop.getEpoiId());
        comment.setPoiId(shop.getPoiId());
        comment.setCommentId(response.getCommentId());
        comment.setResult(response.getResult());
        comment.setCommentContent(response.getCommentContent());
        comment.setOrderCommentScore(response.getOrderCommentScore());
        comment.setFoodCommentScore(response.getFoodCommentScore());
        comment.setDeliveryCommentScore(response.getDeliveryCommentScore());
        comment.setAddComment(response.getAddComment());
        comment.setAddCommentTime(response.getAddCommentTime());
        comment.setPackingScore(response.getPackingScore());
        comment.setCommentTime(response.getCommentTime());
        comment.setCommentLables(response.getCommentLables());
        comment.setShipTime(response.getShipTime());
        comment.setCommentPictures(response.getCommentPictures());
        comment.setPraiseFoodList(response.getPraiseFoodList());
        comment.setCriticFoodList(response.getCriticFoodList());
        comment.setReplyStatus(response.getReplyStatus());
        comment.setCommentOrderDetailJson(JsonUtil.toJson(response.getCommentOrderDetail()));
        comment.setEReplyContent(response.getEReplyContent());
        comment.setEReplyTime(response.getEReplyTime());
        comment.setCommentType(response.getCommentType());
        comment.setValid(response.getValid());
        comment.setUserId(response.getUserId());
        comment.setOverDeliveryTimeDesc(response.getOverDeliveryTimeDesc());
        comment.setCommentScoreType(response.getCommentScoreType());
        comment.setProductScore(response.getProductScore());
        comment.setBizCode(response.getBizCode());
        comment.setQueryStartTime(startTime);
        comment.setQueryEndTime(endTime);
        comment.setRawPayloadJson(JsonUtil.toJson(response));
        comment.setAutoReplyStatus("PENDING");
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        return comment;
    }

    private String buildCommentReplyTaskBizKey(Long commentId) {
        return "comment:" + commentId;
    }
}
