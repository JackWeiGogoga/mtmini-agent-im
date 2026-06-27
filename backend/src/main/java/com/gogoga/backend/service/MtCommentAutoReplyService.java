package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.dto.CommentReplyContentResultDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.async.MtAuthPostProcessTaskPayload;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;
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
    private static final int DAILY_INCREMENTAL_LOOKBACK_DAYS = 1;
    private static final DateTimeFormatter COMMENT_QUERY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MtShopMapper mtShopMapper;
    private final MtCommentMapper mtCommentMapper;
    private final MeituanClient meituanClient;
    private final MtProperties mtProperties;
    private final MtAsyncTaskService mtAsyncTaskService;
    private final ShopOperationSettingsService shopOperationSettingsService;
    private final MtCommentReplyContentService mtCommentReplyContentService;
    private final CommentLogAnalysisService commentLogAnalysisService;

    public void syncAndReplyRecentUnrepliedComments() {
        LocalDateTime now = LocalDateTime.now();
        List<MtAccessToken> candidates = mtAccessTokenMapper.selectCommentAutoReplyCandidates(now);
        log.info("评价自动回复任务开始，candidateCount={}", candidates.size());
        for (MtAccessToken token : candidates) {
            try {
                syncAndReplyShop(token, now.toLocalDate(), DAILY_INCREMENTAL_LOOKBACK_DAYS, "DAILY_INCREMENTAL");
            } catch (RuntimeException e) {
                log.error("评价自动回复门店处理失败，shopId={}, opBizCode={}", token.getShopId(), token.getOpBizCode(), e);
            }
        }
        log.info("评价自动回复任务完成，candidateCount={}", candidates.size());
    }

    void syncAndReplyShopForAuthBackfill(MtAccessToken token, LocalDate currentDate) {
        syncAndReplyShop(token, currentDate, Math.max(mtProperties.getComment().getLookbackDays(), 1), "AUTH_BACKFILL");
    }

    public void executeAuthCommentBackfillTask(MtAuthPostProcessTaskPayload payload) {
        if (payload == null || payload.getShopId() == null) {
            throw new IllegalArgumentException("AUTH_COMMENT_BACKFILL_PAYLOAD_INVALID");
        }
        MtAccessToken token = mtAccessTokenMapper.selectByShopId(payload.getShopId());
        if (token == null || StringUtils.isBlank(token.getAccessToken())) {
            throw new IllegalStateException("AUTH_COMMENT_BACKFILL_TOKEN_MISSING");
        }
        syncAndReplyShop(token, LocalDate.now(), Math.max(mtProperties.getComment().getLookbackDays(), 1), "AUTH_BACKFILL");
    }

    private void syncAndReplyShop(MtAccessToken token, LocalDate currentDate, int lookbackDays, String scene) {
        if (token == null || token.getShopId() == null || StringUtils.isBlank(token.getAccessToken())) {
            return;
        }
        MtShop shop = mtShopMapper.selectById(token.getShopId());
        if (shop == null || StringUtils.isBlank(shop.getEpoiId())) {
            log.warn("评价自动回复跳过门店，原因=缺少门店或 epoiId, shopId={}", token.getShopId());
            return;
        }
        if (!shopOperationSettingsService.isAutoReplyEnabled(shop.getId())) {
            log.info("评价自动回复跳过门店，原因=AI回复主开关关闭, shopId={}", token.getShopId());
            return;
        }
        LocalDate endDate = currentDate == null ? LocalDate.now() : currentDate;
        LocalDate startDate = endDate.minusDays(Math.max(lookbackDays, 1));
        log.info("评价自动回复同步开始，scene={}, shopId={}, startDate={}, endDate={}",
                scene, token.getShopId(), startDate, endDate);
        syncUnrepliedComments(token, shop, startDate, endDate);
        enqueuePendingCommentReplyTasks(shop);
        log.info("评价自动回复同步完成，scene={}, shopId={}, startDate={}, endDate={}",
                scene, token.getShopId(), startDate, endDate);
    }

    private void syncUnrepliedComments(MtAccessToken token, MtShop shop, LocalDate startDate, LocalDate endDate) {
        String startTime = COMMENT_QUERY_DATE_FORMATTER.format(startDate);
        String endTime = COMMENT_QUERY_DATE_FORMATTER.format(endDate);
        if (mtProperties.getComment().isQueryDryRun()) {
            log.warn("评价查询 dry run 已开启，跳过美团查询评价接口，shopId={}, startTime={}, endTime={}",
                    shop.getId(), startTime, endTime);
            return;
        }
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
        if (mtProperties.getComment().isReplyDryRun()) {
            log.warn("评价回复 dry run 已开启，跳过自动回复任务入队，shopId={}", shop.getId());
            return;
        }
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
        if (!shopOperationSettingsService.isAutoReplyEnabled(shop.getId())) {
            log.info("评价自动回复任务跳过，原因=AI回复主开关关闭, shopId={}, commentId={}",
                    shop.getId(), comment.getCommentId());
            return;
        }
        MtAccessToken token = mtAccessTokenMapper.selectByShopId(payload.getShopId());
        if (token == null || StringUtils.isBlank(token.getAccessToken())) {
            throw new IllegalStateException("COMMENT_AUTO_REPLY_TOKEN_MISSING");
        }
        CommentReplyContentResultDTO replyResult = mtCommentReplyContentService.generateReply(shop, comment);
        if (replyResult == null || !replyResult.isShouldReply()) {
            String reason = replyResult == null ? "COMMENT_REPLY_GENERATION_SKIPPED" : replyResult.getSkipReason();
            mtCommentMapper.markAutoReplySkipped(comment.getId(), StringUtils.left(reason, 1000), LocalDateTime.now());
            log.warn("评价自动回复任务跳过，原因=回复内容生成跳过, shopId={}, commentId={}, reason={}",
                    shop.getId(), comment.getCommentId(), reason);
            return;
        }
        String reply = StringUtils.defaultIfBlank(replyResult.getReplyContent(), mtProperties.getComment().getDefaultReply());
        if (mtProperties.getComment().isReplyDryRun()) {
            log.warn("评价回复 dry run 已开启，跳过美团添加商家回复接口，shopId={}, commentId={}",
                    shop.getId(), comment.getCommentId());
            throw new MtAsyncTaskDryRunException(
                    "MT_COMMENT_REPLY_DRY_RUN",
                    mtProperties.getComment().getDryRunRescheduleSeconds()
            );
        }
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
            mtCommentMapper.markAutoReplySuccessWithGeneration(
                    comment.getId(),
                    reply,
                    now,
                    replyResult.getReplySource(),
                    replyResult.getSentiment(),
                    replyResult.getGenerationId(),
                    replyResult.getKnowledgeHitJson(),
                    replyResult.getModel(),
                    replyResult.getPowerAmount(),
                    replyResult.getReplyDurationMs(),
                    now
            );
            comment.setAutoReplyContent(reply);
            comment.setAutoReplyAt(now);
            comment.setAutoReplyStatus("SUCCESS");
            comment.setAutoReplySource(replyResult.getReplySource());
            comment.setAutoReplySentiment(replyResult.getSentiment());
            comment.setAutoReplyGenerationId(replyResult.getGenerationId());
            comment.setAutoReplyKnowledgeHitJson(replyResult.getKnowledgeHitJson());
            comment.setAutoReplyModel(replyResult.getModel());
            comment.setAutoReplyPowerAmount(replyResult.getPowerAmount());
            comment.setAutoReplyDurationMs(replyResult.getReplyDurationMs());
            comment.setImageSummaryText(replyResult.getImageSummaryText());
            commentLogAnalysisService.generateAfterAutoReply(shop, comment);
            enqueueCommentComplaintTaskIfNeeded(shop, comment);
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
        comment.setAnalysisStatus("PENDING");
        comment.setComplaintStatus("PENDING");
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        return comment;
    }

    private String buildCommentReplyTaskBizKey(Long commentId) {
        return "comment:" + commentId;
    }

    private void enqueueCommentComplaintTaskIfNeeded(MtShop shop, MtComment comment) {
        if (shop == null || shop.getId() == null || comment == null || comment.getCommentId() == null) {
            return;
        }
        if (!shopOperationSettingsService.isNegativeCommentAppealEnabled(shop.getId())) {
            return;
        }
        CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.fromProductAndPackingScore(
                comment.getProductScore(),
                comment.getPackingScore()
        );
        if (sentiment != CommentReplySentimentEnum.BAD) {
            return;
        }
        MtCommentAutoComplaintTaskPayload payload = new MtCommentAutoComplaintTaskPayload();
        payload.setShopId(shop.getId());
        payload.setCommentId(comment.getCommentId());
        mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.COMMENT_AUTO_COMPLAINT,
                buildCommentComplaintTaskBizKey(comment.getCommentId()),
                String.valueOf(shop.getId()),
                payload,
                LocalDateTime.now(),
                mtProperties.getAsyncTask().getCommentAutoComplaint().getMaxRetryCount()
        );
    }

    private String buildCommentComplaintTaskBizKey(Long commentId) {
        return "comment-complaint:" + commentId;
    }
}
