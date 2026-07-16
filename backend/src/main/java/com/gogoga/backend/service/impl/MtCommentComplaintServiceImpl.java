package com.gogoga.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.constant.SystemPromptKeyConstant;
import com.gogoga.backend.dto.CommentComplaintPreviewDTO;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.dto.CommentReplySensitiveWordHitDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentComplaint;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentComplaintReportTypeEnum;
import com.gogoga.backend.enums.CommentComplaintStatusEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import com.gogoga.backend.mapper.MtCommentComplaintMapper;
import com.gogoga.backend.mapper.MtCommentMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.service.CommentReplySensitiveWordService;
import com.gogoga.backend.service.CommentImageVisionService;
import com.gogoga.backend.service.MtCommentComplaintService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.OpenAiCompatibleChatService;
import com.gogoga.backend.service.ShopOperationSettingsService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.service.async.MtAsyncTaskDryRunException;
import com.gogoga.backend.service.async.MtAsyncTaskTerminalFailureException;
import com.gogoga.backend.service.async.MtCommentAutoComplaintTaskPayload;
import com.gogoga.backend.util.CommentImageUrlUtil;
import com.gogoga.backend.util.JsonUtil;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.comment.commentComplainReport.CommentComplainReportRequest;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 差评自动投诉服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtCommentComplaintServiceImpl implements MtCommentComplaintService {

    private static final String MODEL_PROVIDER_TOKENHUB = "TOKENHUB";
    private static final String POWER_SOURCE_TYPE = "COMMENT_AUTO_COMPLAINT";

    private final MtProperties mtProperties;
    private final PowerProperties powerProperties;
    private final MtCommentMapper mtCommentMapper;
    private final MtCommentComplaintMapper mtCommentComplaintMapper;
    private final MtShopMapper mtShopMapper;
    private final MtAccessTokenMapper mtAccessTokenMapper;
    private final MeituanClient meituanClient;
    private final SystemPromptService systemPromptService;
    private final OpenAiCompatibleChatService openAiCompatibleChatService;
    private final CommentReplySensitiveWordService commentReplySensitiveWordService;
    private final CommentImageVisionService commentImageVisionService;
    private final MtPowerConsumeService mtPowerConsumeService;
    private final ShopOperationSettingsService shopOperationSettingsService;

    @Override
    public void executeCommentAutoComplaintTask(MtCommentAutoComplaintTaskPayload payload) {
        long startNanos = System.nanoTime();
        if (payload == null || payload.getShopId() == null || payload.getCommentId() == null) {
            throw new IllegalArgumentException("COMMENT_AUTO_COMPLAINT_PAYLOAD_INVALID");
        }
        MtComment comment = mtCommentMapper.selectByCommentId(payload.getCommentId());
        if (comment == null) {
            log.warn("差评自动投诉跳过，原因=评价不存在，shopId={}, commentId={}", payload.getShopId(), payload.getCommentId());
            return;
        }
        if (!payload.getShopId().equals(comment.getShopId())) {
            throw new IllegalStateException("COMMENT_AUTO_COMPLAINT_SHOP_MISMATCH");
        }
        MtShop shop = mtShopMapper.selectById(payload.getShopId());
        if (shop == null || StringUtils.isBlank(shop.getEpoiId())) {
            throw new IllegalStateException("COMMENT_AUTO_COMPLAINT_SHOP_INVALID");
        }
        MtCommentComplaint record = selectOrCreateRecord(shop, comment);
        if (StringUtils.equals(record.getStatus(), CommentComplaintStatusEnum.SUCCESS.name())) {
            log.info("差评自动投诉跳过，原因=已成功申诉，shopId={}, commentId={}", shop.getId(), comment.getCommentId());
            return;
        }
        if (!shopOperationSettingsService.isAutoReplyEnabled(shop.getId())
                || !shopOperationSettingsService.isNegativeCommentAppealEnabled(shop.getId())) {
            markSkipped(comment, record, "COMMENT_AUTO_COMPLAINT_SWITCH_DISABLED", durationMs(startNanos));
            return;
        }
        CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.fromProductAndPackingScore(
                comment.getProductScore(), comment.getPackingScore());
        if (sentiment != CommentReplySentimentEnum.BAD) {
            markSkipped(comment, record, "COMMENT_AUTO_COMPLAINT_NOT_BAD", durationMs(startNanos));
            return;
        }
        MtAccessToken token = mtAccessTokenMapper.selectByShopId(shop.getId());
        if (token == null || StringUtils.isBlank(token.getAccessToken())) {
            markFailed(comment, record, "COMMENT_AUTO_COMPLAINT_TOKEN_MISSING", durationMs(startNanos));
            throw new IllegalStateException("COMMENT_AUTO_COMPLAINT_TOKEN_MISSING");
        }

        try {
            boolean reuseGenerated = canReuseGenerated(record);
            ComplaintGeneration generation = reuseGenerated
                    ? buildGenerationFromRecord(record)
                    : generateComplaint(shop, comment, sentiment);
            CommentReplySensitiveWordHitDTO sensitiveHit = commentReplySensitiveWordService.detect(generation.reportContent);
            if (sensitiveHit != null && sensitiveHit.isHit()) {
                String error = "COMMENT_AUTO_COMPLAINT_SENSITIVE:"
                        + StringUtils.join(sensitiveHit.getCategories(), "、");
                markFailed(comment, record, error, durationMs(startNanos));
                throw new MtAsyncTaskTerminalFailureException(error);
            }
            if (!reuseGenerated) {
                int powerAmount = calculatePowerAmount(generation);
                consumePower(shop.getId(), comment.getCommentId(), powerAmount);
                fillGeneratedRecord(record, generation, powerAmount, durationMs(startNanos));
                mtCommentComplaintMapper.markGenerated(record);
                mtCommentMapper.markComplaintGenerated(comment.getId(), generation.reportType.getCode(), generation.reportContent, LocalDateTime.now());
            }
            if (mtProperties.getComment().isComplaintDryRun()) {
                throw new MtAsyncTaskDryRunException(
                        "MT_COMMENT_COMPLAINT_DRY_RUN",
                        mtProperties.getComment().getDryRunRescheduleSeconds()
                );
            }
            submitComplaint(shop, token, comment, record, generation, startNanos);
        } catch (MtAsyncTaskDryRunException e) {
            throw e;
        } catch (MtAsyncTaskTerminalFailureException e) {
            throw e;
        } catch (Exception e) {
            String error = StringUtils.left(StringUtils.defaultIfBlank(e.getMessage(), "COMMENT_AUTO_COMPLAINT_FAILED"), 1000);
            markFailed(comment, record, error, durationMs(startNanos));
            log.warn("差评自动投诉失败，shopId={}, commentId={}", shop.getId(), comment.getCommentId(), e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(error, e);
        }
    }

    @Override
    public CommentComplaintPreviewDTO previewComplaint(MtShop shop, MtComment comment) {
        long startNanos = System.nanoTime();
        if (shop == null || comment == null || comment.getCommentId() == null) {
            throw new IllegalArgumentException("COMMENT_COMPLAINT_PREVIEW_CONTEXT_INVALID");
        }
        CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.fromProductAndPackingScore(
                comment.getProductScore(),
                comment.getPackingScore()
        );
        CommentComplaintPreviewDTO result = new CommentComplaintPreviewDTO();
        result.setSentiment(sentiment.name());
        if (sentiment != CommentReplySentimentEnum.BAD) {
            result.setShouldComplain(false);
            result.setSkipReason(sentiment == CommentReplySentimentEnum.UNKNOWN
                    ? "PRODUCT_PACKING_SCORE_INVALID"
                    : "COMMENT_NOT_BAD");
            result.setDurationMs(durationMs(startNanos));
            return result;
        }
        CommentImageSummaryResultDTO imageSummary = commentImageVisionService.resolveForReply(shop, comment, false);
        if (imageSummary != null && StringUtils.isNotBlank(imageSummary.getSummaryText())) {
            comment.setImageSummaryText(imageSummary.getSummaryText());
        }
        ComplaintGeneration generation = generateComplaint(shop, comment, sentiment);
        CommentReplySensitiveWordHitDTO sensitiveHit = commentReplySensitiveWordService.detect(generation.reportContent);
        result.setShouldComplain(sensitiveHit == null || !sensitiveHit.isHit());
        result.setSkipReason(result.isShouldComplain() ? null : "COMMENT_AUTO_COMPLAINT_SENSITIVE");
        result.setReportTypeCode(generation.reportType.getCode());
        result.setReportTypeName(generation.reportType.getName());
        result.setTypeDefaulted(generation.typeDefaulted);
        result.setTypeReason(generation.typeReason);
        result.setReportContent(generation.reportContent);
        result.setHasPicture(generation.hasPicture);
        result.setModel(generation.model);
        result.setTypePromptKey(generation.typePromptKey);
        result.setTypePromptVersion(generation.typePromptVersion);
        result.setContentPromptKey(generation.contentPromptKey);
        result.setContentPromptVersion(generation.contentPromptVersion);
        result.setPromptTokens(generation.promptTokens);
        result.setCompletionTokens(generation.completionTokens);
        result.setTotalTokens(generation.totalTokens);
        result.setEstimatedPowerAmount(calculatePowerAmount(generation));
        if (sensitiveHit != null && sensitiveHit.isHit()) {
            result.setSensitiveErrorMessage("命中敏感词：" + StringUtils.join(sensitiveHit.getCategories(), "、"));
        }
        attachImageSummary(result, imageSummary);
        result.setDurationMs(durationMs(startNanos));
        return result;
    }

    private void attachImageSummary(CommentComplaintPreviewDTO result, CommentImageSummaryResultDTO imageSummary) {
        if (result == null || imageSummary == null) {
            return;
        }
        result.setImageSummaryStatus(imageSummary.getStatus());
        result.setImageSummaryText(imageSummary.getSummaryText());
        result.setImageSummaryJson(imageSummary.getSummaryJson());
        result.setImageSummaryErrorMessage(imageSummary.getErrorMessage());
        result.setImagePromptTokens(imageSummary.getPromptTokens());
        result.setImageCompletionTokens(imageSummary.getCompletionTokens());
        result.setImageTotalTokens(imageSummary.getTotalTokens());
        result.setImagePowerAmount(imageSummary.getPowerAmount());
    }

    private boolean canReuseGenerated(MtCommentComplaint record) {
        return record != null
                && StringUtils.equals(record.getStatus(), CommentComplaintStatusEnum.GENERATED.name())
                && record.getReportTypeCode() != null
                && StringUtils.isNotBlank(record.getReportContent());
    }

    private ComplaintGeneration buildGenerationFromRecord(MtCommentComplaint record) {
        ComplaintGeneration generation = new ComplaintGeneration();
        generation.reportType = CommentComplaintReportTypeEnum.fromCode(record.getReportTypeCode());
        generation.typeDefaulted = Boolean.TRUE.equals(record.getTypeDefaulted());
        generation.typeReason = record.getTypeReason();
        generation.reportContent = record.getReportContent();
        generation.hasPicture = Boolean.TRUE.equals(record.getHasPicture());
        generation.model = record.getModel();
        generation.typePromptKey = record.getTypePromptKey();
        generation.typePromptVersion = record.getTypePromptVersion();
        generation.contentPromptKey = record.getContentPromptKey();
        generation.contentPromptVersion = record.getContentPromptVersion();
        generation.promptTokens = record.getPromptTokens();
        generation.completionTokens = record.getCompletionTokens();
        generation.totalTokens = record.getTotalTokens();
        return generation;
    }

    private MtCommentComplaint selectOrCreateRecord(MtShop shop, MtComment comment) {
        MtCommentComplaint existing = mtCommentComplaintMapper.selectByShopIdAndCommentId(shop.getId(), comment.getCommentId());
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        MtCommentComplaint record = new MtCommentComplaint();
        record.setShopId(shop.getId());
        record.setMtCommentId(comment.getId());
        record.setCommentId(comment.getCommentId());
        record.setStatus(CommentComplaintStatusEnum.PENDING.name());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            mtCommentComplaintMapper.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            return mtCommentComplaintMapper.selectByShopIdAndCommentId(shop.getId(), comment.getCommentId());
        }
    }

    private ComplaintGeneration generateComplaint(MtShop shop, MtComment comment, CommentReplySentimentEnum sentiment) {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        String pictureContext = buildPictureContext(comment);
        TypeModelResponse typeResponse = callTypeModel(knowledge, comment, sentiment, pictureContext);
        CommentComplaintReportTypeEnum reportType = CommentComplaintReportTypeEnum.fromCode(typeResponse.reportTypeCode);
        boolean defaulted = !CommentComplaintReportTypeEnum.isSupported(typeResponse.reportTypeCode);
        ContentModelResponse contentResponse = callContentModel(knowledge, comment, reportType, typeResponse.reason, pictureContext);
        String content = StringUtils.left(StringUtils.trim(contentResponse.content),
                Math.max(mtProperties.getComment().getComplaint().getMaxContentLength(), 1));
        if (StringUtils.isBlank(content)) {
            throw new IllegalStateException("COMMENT_AUTO_COMPLAINT_EMPTY_CONTENT");
        }
        ComplaintGeneration generation = new ComplaintGeneration();
        generation.reportType = reportType;
        generation.typeDefaulted = defaulted;
        generation.typeReason = StringUtils.left(typeResponse.reason, 500);
        generation.reportContent = content;
        generation.hasPicture = hasPictures(comment);
        generation.model = StringUtils.defaultIfBlank(contentResponse.model, StringUtils.defaultIfBlank(typeResponse.model, knowledge.getChatModel()));
        generation.typePromptKey = typeResponse.promptKey;
        generation.typePromptVersion = typeResponse.promptVersion;
        generation.contentPromptKey = contentResponse.promptKey;
        generation.contentPromptVersion = contentResponse.promptVersion;
        generation.promptTokens = sumTokens(typeResponse.promptTokens, contentResponse.promptTokens);
        generation.completionTokens = sumTokens(typeResponse.completionTokens, contentResponse.completionTokens);
        generation.totalTokens = sumTokens(typeResponse.totalTokens, contentResponse.totalTokens);
        log.info("差评自动投诉内容生成完成，shopId={}, commentId={}, reportTypeCode={}",
                shop.getId(), comment.getCommentId(), reportType.getCode());
        return generation;
    }

    private TypeModelResponse callTypeModel(MtProperties.Knowledge knowledge,
                                            MtComment comment,
                                            CommentReplySentimentEnum sentiment,
                                            String pictureContext) {
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                SystemPromptKeyConstant.COMMENT_REPORT_TYPE,
                buildTypeVariables(comment, sentiment, pictureContext),
                defaultTypePrompt()
        );
        ChatCompletion completion = callChatModel(knowledge, prompt.getContent());
        TypeModelResponse result = new TypeModelResponse();
        result.promptKey = prompt.getPromptKey();
        result.promptVersion = prompt.getVersion();
        result.model = completion.model();
        fillUsage(result, completion);
        String content = firstContent(completion);
        try {
            JsonNode node = JsonUtil.readTree(JsonUtil.normalizeLlmJsonObjectText(content));
            result.reportTypeCode = node == null ? null : readInt(node, "reportTypeCode");
            result.reason = node == null ? null : node.path("reason").asText(null);
        } catch (Exception e) {
            result.reportTypeCode = CommentComplaintReportTypeEnum.DEFAULT_CODE;
            result.reason = "模型返回不可解析";
        }
        if (!CommentComplaintReportTypeEnum.isSupported(result.reportTypeCode)) {
            result.reason = StringUtils.defaultIfBlank(result.reason, "类型无效，默认无意义评价");
        }
        return result;
    }

    private ContentModelResponse callContentModel(MtProperties.Knowledge knowledge,
                                                  MtComment comment,
                                                  CommentComplaintReportTypeEnum reportType,
                                                  String typeReason,
                                                  String pictureContext) {
        ResolvedSystemPromptDTO prompt = systemPromptService.render(
                SystemPromptKeyConstant.COMMENT_REPORT_CONTENT,
                buildContentVariables(comment, reportType, typeReason, pictureContext),
                defaultContentPrompt()
        );
        ChatCompletion completion = callChatModel(knowledge, prompt.getContent());
        ContentModelResponse result = new ContentModelResponse();
        result.promptKey = prompt.getPromptKey();
        result.promptVersion = prompt.getVersion();
        result.model = completion.model();
        result.content = firstContent(completion);
        fillUsage(result, completion);
        return result;
    }

    private ChatCompletion callChatModel(MtProperties.Knowledge knowledge, String prompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(knowledge.getChatModel())
                .addUserMessage(prompt)
                .build();
        ChatCompletion completion = openAiCompatibleChatService.createChatCompletion(
                knowledge.getModelBaseUrl(),
                knowledge.getModelApiKey(),
                params
        );
        if (completion == null || completion.choices() == null || completion.choices().isEmpty()) {
            throw new IllegalStateException("COMMENT_AUTO_COMPLAINT_EMPTY_MODEL_RESPONSE");
        }
        return completion;
    }

    private void submitComplaint(MtShop shop,
                                 MtAccessToken token,
                                 MtComment comment,
                                 MtCommentComplaint record,
                                 ComplaintGeneration generation,
                                 long startNanos) throws MtSdkException {
        CommentComplainReportRequest request = new CommentComplainReportRequest();
        request.setCommentId(String.valueOf(comment.getCommentId()));
        request.setReportTypeCode(generation.reportType.getCode());
        request.setReportContent(generation.reportContent);
        MeituanResponse<String> response = meituanClient.invokeApi(request, token.getAccessToken());
        if (response == null || !response.isSuccess()) {
            String error = response == null ? "EMPTY_RESPONSE" : response.getMsg();
            throw new IllegalStateException("差评自动投诉失败：" + error);
        }
        LocalDateTime now = LocalDateTime.now();
        String requestJson = JsonUtil.toJson(Map.of(
                "commentId", String.valueOf(comment.getCommentId()),
                "reportTypeCode", generation.reportType.getCode(),
                "reportContent", generation.reportContent
        ));
        mtCommentComplaintMapper.markSuccess(
                record.getId(),
                requestJson,
                JsonUtil.toJson(response),
                now,
                durationMs(startNanos),
                now
        );
        mtCommentMapper.markComplaintSuccess(
                comment.getId(),
                generation.reportType.getCode(),
                generation.reportContent,
                now,
                now
        );
        log.info("差评自动投诉提交成功，shopId={}, commentId={}, reportTypeCode={}, durationMs={}",
                shop.getId(), comment.getCommentId(), generation.reportType.getCode(), durationMs(startNanos));
    }

    private void fillGeneratedRecord(MtCommentComplaint record, ComplaintGeneration generation, int powerAmount, int durationMs) {
        record.setStatus(CommentComplaintStatusEnum.GENERATED.name());
        record.setReportTypeCode(generation.reportType.getCode());
        record.setReportTypeName(generation.reportType.getName());
        record.setReportContent(generation.reportContent);
        record.setHasPicture(generation.hasPicture);
        record.setTypeDefaulted(generation.typeDefaulted);
        record.setTypeReason(generation.typeReason);
        record.setModelProvider(MODEL_PROVIDER_TOKENHUB);
        record.setModel(generation.model);
        record.setTypePromptKey(generation.typePromptKey);
        record.setTypePromptVersion(generation.typePromptVersion);
        record.setContentPromptKey(generation.contentPromptKey);
        record.setContentPromptVersion(generation.contentPromptVersion);
        record.setPromptTokens(generation.promptTokens);
        record.setCompletionTokens(generation.completionTokens);
        record.setTotalTokens(generation.totalTokens);
        record.setPowerAmount(powerAmount);
        record.setDurationMs(durationMs);
        record.setUpdateTime(LocalDateTime.now());
    }

    private void consumePower(Long shopId, Long commentId, int powerAmount) {
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(POWER_SOURCE_TYPE);
        request.setSourceId(String.valueOf(commentId));
        request.setRequestNo(POWER_SOURCE_TYPE + ":" + commentId);
        request.setPowerAmount(powerAmount);
        request.setRemark("差评自动投诉");
        mtPowerConsumeService.consume(request);
    }

    private int calculatePowerAmount(ComplaintGeneration generation) {
        Long promptTokens = toLong(generation.promptTokens);
        Long completionTokens = toLong(generation.completionTokens);
        if ((promptTokens == null || promptTokens <= 0L)
                && (completionTokens == null || completionTokens <= 0L)
                && generation.totalTokens != null && generation.totalTokens > 0) {
            promptTokens = generation.totalTokens.longValue();
            completionTokens = 0L;
        }
        try {
            return powerProperties.getBilling().calculateLlmPowerAmount(promptTokens, completionTokens);
        } catch (Exception e) {
            return Math.max(mtProperties.getComment().getComplaint().getFallbackPowerAmount(), 1);
        }
    }

    private void markSkipped(MtComment comment, MtCommentComplaint record, String reason, int durationMs) {
        mtCommentComplaintMapper.markFailed(record.getId(), CommentComplaintStatusEnum.SKIPPED.name(), reason, durationMs, LocalDateTime.now());
        mtCommentMapper.markComplaintFailed(comment.getId(), CommentComplaintStatusEnum.SKIPPED.name(), reason, LocalDateTime.now());
        log.info("差评自动投诉跳过，shopId={}, commentId={}, reason={}", comment.getShopId(), comment.getCommentId(), reason);
    }

    private void markFailed(MtComment comment, MtCommentComplaint record, String error, int durationMs) {
        String safeError = StringUtils.left(StringUtils.defaultIfBlank(error, "COMMENT_AUTO_COMPLAINT_FAILED"), 1000);
        mtCommentComplaintMapper.markFailed(record.getId(), CommentComplaintStatusEnum.FAILED.name(), safeError, durationMs, LocalDateTime.now());
        mtCommentMapper.markComplaintFailed(comment.getId(), CommentComplaintStatusEnum.FAILED.name(), safeError, LocalDateTime.now());
    }

    private Map<String, Object> buildTypeVariables(MtComment comment, CommentReplySentimentEnum sentiment, String pictureContext) {
        Map<String, Object> variables = baseVariables(comment, pictureContext);
        variables.put("sentiment", sentiment.name());
        return variables;
    }

    private Map<String, Object> buildContentVariables(MtComment comment,
                                                      CommentComplaintReportTypeEnum reportType,
                                                      String typeReason,
                                                      String pictureContext) {
        Map<String, Object> variables = baseVariables(comment, pictureContext);
        variables.put("reportTypeCode", reportType.getCode());
        variables.put("reportTypeName", reportType.getName());
        variables.put("typeReason", StringUtils.defaultIfBlank(typeReason, "无"));
        return variables;
    }

    private Map<String, Object> baseVariables(MtComment comment, String pictureContext) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("productScore", comment.getProductScore());
        variables.put("packingScore", comment.getPackingScore());
        variables.put("deliveryCommentScore", comment.getDeliveryCommentScore());
        variables.put("commentLabels", comment.getCommentLables());
        variables.put("overDeliveryTimeDesc", comment.getOverDeliveryTimeDesc());
        variables.put("commentContent", comment.getCommentContent());
        variables.put("addComment", comment.getAddComment());
        variables.put("pictureContext", pictureContext);
        return variables;
    }

    private String buildPictureContext(MtComment comment) {
        if (!hasPictures(comment)) {
            return "顾客未上传图片";
        }
        if (StringUtils.isNotBlank(comment.getImageSummaryText())) {
            return "顾客有上传图片。图片摘要：" + comment.getImageSummaryText();
        }
        return "顾客有上传图片，但当前没有可用图片摘要";
    }

    private boolean hasPictures(MtComment comment) {
        return !CommentImageUrlUtil.parseImageUrls(comment.getCommentPictures(), Integer.MAX_VALUE).isEmpty();
    }

    private String firstContent(ChatCompletion completion) {
        return completion.choices().get(0).message().content().orElse(null);
    }

    private void fillUsage(ModelResponse response, ChatCompletion completion) {
        completion.usage().ifPresent(usage -> fillUsage(response, usage));
    }

    private void fillUsage(ModelResponse response, CompletionUsage usage) {
        response.promptTokens = toInt(usage.promptTokens());
        response.completionTokens = toInt(usage.completionTokens());
        response.totalTokens = toInt(usage.totalTokens());
    }

    private Integer readInt(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || !node.path(fieldName).isNumber()) {
            return null;
        }
        return node.path(fieldName).asInt();
    }

    private Integer sumTokens(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return Math.max(left == null ? 0 : left, 0) + Math.max(right == null ? 0 : right, 0);
    }

    private Integer toInt(long value) {
        if (value <= 0L) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private int durationMs(long startNanos) {
        long millis = (System.nanoTime() - startNanos) / 1_000_000L;
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(millis, 0L);
    }

    private String defaultTypePrompt() {
        return "你是美团外卖商家差评申诉分类助手。只输出 JSON：{\"reportTypeCode\":47,\"reason\":\"20字以内理由\"}。"
                + "如果证据不足或无法判断，使用47。\n"
                + "评价类型：{{sentiment}}\n商品评分：{{productScore}}\n包装评分：{{packingScore}}\n配送评分：{{deliveryCommentScore}}\n"
                + "评价标签：{{commentLabels}}\n配送时效：{{overDeliveryTimeDesc}}\n顾客首次评价：{{commentContent}}\n追评：{{addComment}}\n图片上下文：{{pictureContext}}";
    }

    private String defaultContentPrompt() {
        return "你是一名资深外卖商家客服。请根据顾客评价和申诉类型，生成提交给美团平台的申诉内容。"
                + "300个中文字符以内，客观克制，不承诺退款、赔偿、补发、私下联系或站外处理。只输出最终申诉文本。\n"
                + "配送时效：{{overDeliveryTimeDesc}}\n顾客首次评价：{{commentContent}}\n追评：{{addComment}}\n图片上下文：{{pictureContext}}\n"
                + "申诉类型：{{reportTypeCode}} {{reportTypeName}}\n申诉类型判断理由：{{typeReason}}";
    }

    private static class ModelResponse {
        protected String model;
        protected String promptKey;
        protected Integer promptVersion;
        protected Integer promptTokens;
        protected Integer completionTokens;
        protected Integer totalTokens;
    }

    private static class TypeModelResponse extends ModelResponse {
        private Integer reportTypeCode;
        private String reason;
    }

    private static class ContentModelResponse extends ModelResponse {
        private String content;
    }

    private static class ComplaintGeneration {
        private CommentComplaintReportTypeEnum reportType;
        private boolean typeDefaulted;
        private String typeReason;
        private String reportContent;
        private boolean hasPicture;
        private String model;
        private String typePromptKey;
        private Integer typePromptVersion;
        private String contentPromptKey;
        private Integer contentPromptVersion;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
