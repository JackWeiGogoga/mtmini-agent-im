package com.gogoga.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.constant.SystemPromptKeyConstant;
import com.gogoga.backend.dto.CommentImageSummaryResultDTO;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.entity.mt.MtComment;
import com.gogoga.backend.entity.mt.MtCommentImageSummary;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentImageSummaryStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.exception.PowerInsufficientException;
import com.gogoga.backend.mapper.MtCommentImageSummaryMapper;
import com.gogoga.backend.service.CommentImageVisionService;
import com.gogoga.backend.service.MtPowerConsumeService;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.util.CommentImageUrlUtil;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TokenHub 多模态图片识别服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentImageVisionServiceImpl implements CommentImageVisionService {

    private static final String MODEL_PROVIDER_TOKENHUB = "TOKENHUB";
    private static final String POWER_SOURCE_TYPE = "COMMENT_IMAGE_VISION";

    private final MtProperties mtProperties;
    private final PowerProperties powerProperties;
    private final MtCommentImageSummaryMapper mtCommentImageSummaryMapper;
    private final MtPowerConsumeService mtPowerConsumeService;
    private final SystemPromptService systemPromptService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentImageSummaryResultDTO resolveForReply(MtShop shop, MtComment comment, boolean chargePower) {
        if (shop == null || shop.getId() == null || comment == null || comment.getCommentId() == null) {
            return emptyResult(CommentImageSummaryStatusEnum.NONE);
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        List<String> urls = CommentImageUrlUtil.parseImageUrls(comment.getCommentPictures(), knowledge.getVisionMaxImageCount());
        String simulatedSummary = StringUtils.trimToNull(comment.getImageSummaryText());
        if (simulatedSummary != null) {
            return simulatedResult(simulatedSummary, urls);
        }
        if (urls.isEmpty()) {
            return emptyResult(CommentImageSummaryStatusEnum.NONE);
        }

        MtCommentImageSummary existing = mtCommentImageSummaryMapper.selectByShopIdAndCommentId(shop.getId(), comment.getCommentId());
        if (existing != null && CommentImageSummaryStatusEnum.SUCCESS.name().equals(existing.getStatus())) {
            return toResult(existing, urls);
        }
        MtCommentImageSummary record = existing == null ? insertInitialRecord(shop, comment, urls) : existing;

        if (!knowledge.isVisionEnabled()) {
            return markFailure(record, urls, "VISION_DISABLED");
        }
        if (StringUtils.isBlank(knowledge.getVisionModelApiKey())) {
            return markFailure(record, urls, "VISION_MODEL_API_KEY_EMPTY");
        }
        if (StringUtils.isBlank(knowledge.getVisionModelBaseUrl()) || StringUtils.isBlank(knowledge.getVisionModel())) {
            return markFailure(record, urls, "VISION_MODEL_CONFIG_INVALID");
        }

        try {
            VisionModelResponse response = callVisionModel(knowledge, urls);
            int powerAmount = chargePower ? consumePower(shop.getId(), comment.getCommentId(), response, knowledge) : 0;
            MtCommentImageSummary success = buildSuccessRecord(record, response, urls, knowledge, powerAmount);
            mtCommentImageSummaryMapper.updateResultById(success);
            return toResult(success, urls);
        } catch (PowerInsufficientException e) {
            throw e;
        } catch (Exception e) {
            log.warn("评价图片识别失败，shopId={}, commentId={}", shop.getId(), comment.getCommentId(), e);
            return markFailure(record, urls, StringUtils.defaultIfBlank(e.getMessage(), "VISION_ERROR"));
        }
    }

    private MtCommentImageSummary insertInitialRecord(MtShop shop, MtComment comment, List<String> urls) {
        LocalDateTime now = LocalDateTime.now();
        MtCommentImageSummary record = new MtCommentImageSummary();
        record.setShopId(shop.getId());
        record.setMtCommentId(comment.getId());
        record.setCommentId(comment.getCommentId());
        record.setImageCount(urls.size());
        record.setImageUrlsJson(JsonUtil.toJson(urls));
        record.setStatus(CommentImageSummaryStatusEnum.SKIPPED.name());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        mtCommentImageSummaryMapper.insert(record);
        return record;
    }

    private VisionModelResponse callVisionModel(MtProperties.Knowledge knowledge, List<String> urls) {
        String endpoint = StringUtils.stripEnd(knowledge.getVisionModelBaseUrl(), "/") + "/chat/completions";
        Map<String, Object> requestBody = buildVisionRequestBody(knowledge.getVisionModel(), urls);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Math.max(1000L, knowledge.getVisionTimeoutSeconds() * 1000L);
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        String response = RestClient.builder()
                .requestFactory(requestFactory)
                .build()
                .post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + knowledge.getVisionModelApiKey())
                .body(requestBody)
                .retrieve()
                .body(String.class);
        return parseVisionResponse(response);
    }

    private Map<String, Object> buildVisionRequestBody(String model, List<String> urls) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (String url : urls) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", url)
            ));
        }
        content.add(Map.of(
                "type", "text",
                "text", buildVisionPrompt(urls.size())
        ));
        return Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", content)),
                "temperature", 0.1,
                "stream", false,
                "max_tokens", 800
        );
    }

    private String buildVisionPrompt(int imageCount) {
        String fallback = "你是外卖评价图片质检助手。请识别这 " + imageCount + " 张顾客上传的外卖评价图片，只描述图片中清晰可见的事实。"
                + "重点判断是否存在餐品异常、包装破损、汤汁撒漏、异物、票据或文字信息。"
                + "不要推测图片之外的原因，不要生成商家回复。"
                + "只输出 JSON 对象，字段固定为："
                + "{\"hasFoodIssue\":boolean,\"hasPackageIssue\":boolean,\"hasForeignObject\":boolean,"
                + "\"hasLeakage\":boolean,\"hasReceiptOrText\":boolean,\"summary\":\"中文摘要，80字以内\","
                + "\"confidence\":\"HIGH|MEDIUM|LOW\"}";
        return systemPromptService.render(
                SystemPromptKeyConstant.COMMENT_IMAGE_VISION,
                Map.of("imageCount", imageCount),
                fallback
        ).getContent();
    }

    private VisionModelResponse parseVisionResponse(String response) {
        JsonNode root = JsonUtil.readTree(response);
        String content = root == null ? null : root.path("choices").path(0).path("message").path("content").asText(null);
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(502, "VISION_EMPTY_REPLY");
        }
        String normalizedJson = JsonUtil.normalizeLlmJsonObjectText(content);
        String summaryJson = normalizedJson;
        String summaryText = null;
        if (StringUtils.isNotBlank(normalizedJson)) {
            JsonNode summaryNode = JsonUtil.readTree(normalizedJson);
            summaryText = summaryNode == null ? null : summaryNode.path("summary").asText(null);
        }
        if (StringUtils.isBlank(summaryText)) {
            summaryText = StringUtils.left(StringUtils.trim(content), 1000);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("summary", summaryText);
            fallback.put("raw", content);
            summaryJson = JsonUtil.toJson(fallback);
        }
        JsonNode usage = root == null ? null : root.path("usage");
        VisionModelResponse result = new VisionModelResponse();
        result.summaryText = StringUtils.left(summaryText, 1000);
        result.summaryJson = summaryJson;
        result.promptTokens = readInt(usage, "prompt_tokens");
        result.completionTokens = readInt(usage, "completion_tokens");
        result.totalTokens = readInt(usage, "total_tokens");
        return result;
    }

    private Integer readInt(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || !node.has(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isNumber() ? value.asInt() : null;
    }

    private int consumePower(Long shopId,
                             Long commentId,
                             VisionModelResponse response,
                             MtProperties.Knowledge knowledge) {
        int powerAmount = calculatePowerAmount(response, knowledge);
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(POWER_SOURCE_TYPE);
        request.setSourceId(String.valueOf(commentId));
        request.setRequestNo(POWER_SOURCE_TYPE + ":" + commentId);
        request.setPowerAmount(powerAmount);
        request.setRemark("评价图片识别");
        mtPowerConsumeService.consume(request);
        return powerAmount;
    }

    private int calculatePowerAmount(VisionModelResponse response, MtProperties.Knowledge knowledge) {
        Long promptTokens = toLong(response.promptTokens);
        Long completionTokens = toLong(response.completionTokens);
        if ((promptTokens == null || promptTokens <= 0L)
                && (completionTokens == null || completionTokens <= 0L)
                && response.totalTokens != null && response.totalTokens > 0) {
            promptTokens = response.totalTokens.longValue();
            completionTokens = 0L;
        }
        try {
            return powerProperties.getBilling().calculateLlmPowerAmount(promptTokens, completionTokens);
        } catch (Exception e) {
            return Math.max(knowledge.getVisionFallbackPowerAmount(), 1);
        }
    }

    private Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private MtCommentImageSummary buildSuccessRecord(MtCommentImageSummary record,
                                                     VisionModelResponse response,
                                                     List<String> urls,
                                                     MtProperties.Knowledge knowledge,
                                                     int powerAmount) {
        record.setImageCount(urls.size());
        record.setImageUrlsJson(JsonUtil.toJson(urls));
        record.setStatus(CommentImageSummaryStatusEnum.SUCCESS.name());
        record.setSummaryJson(response.summaryJson);
        record.setSummaryText(response.summaryText);
        record.setModelProvider(MODEL_PROVIDER_TOKENHUB);
        record.setModel(knowledge.getVisionModel());
        record.setPromptTokens(response.promptTokens);
        record.setCompletionTokens(response.completionTokens);
        record.setTotalTokens(response.totalTokens);
        record.setPowerAmount(powerAmount > 0 ? powerAmount : null);
        record.setErrorMessage(null);
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private CommentImageSummaryResultDTO markFailure(MtCommentImageSummary record, List<String> urls, String reason) {
        String error = StringUtils.left(StringUtils.defaultIfBlank(reason, "VISION_FAILED"), 1000);
        mtCommentImageSummaryMapper.updateFailureById(
                record.getId(),
                CommentImageSummaryStatusEnum.FAILED.name(),
                error,
                LocalDateTime.now()
        );
        CommentImageSummaryResultDTO result = toResult(record, urls);
        result.setStatus(CommentImageSummaryStatusEnum.FAILED.name());
        result.setSummaryText(buildFallbackSummary(urls.size()));
        result.setErrorMessage(error);
        return result;
    }

    private CommentImageSummaryResultDTO emptyResult(CommentImageSummaryStatusEnum status) {
        CommentImageSummaryResultDTO result = new CommentImageSummaryResultDTO();
        result.setStatus(status.name());
        result.setImageCount(0);
        result.setImageUrls(List.of());
        return result;
    }

    private CommentImageSummaryResultDTO simulatedResult(String summaryText, List<String> urls) {
        CommentImageSummaryResultDTO result = new CommentImageSummaryResultDTO();
        result.setStatus(CommentImageSummaryStatusEnum.SIMULATED.name());
        result.setImageCount(urls == null ? 0 : urls.size());
        result.setImageUrls(urls == null ? List.of() : urls);
        result.setSummaryText(summaryText);
        result.setSummaryJson(JsonUtil.toJson(Map.of("summary", summaryText, "source", "SIMULATED")));
        return result;
    }

    private CommentImageSummaryResultDTO toResult(MtCommentImageSummary summary, List<String> urls) {
        CommentImageSummaryResultDTO result = new CommentImageSummaryResultDTO();
        result.setSummaryId(summary.getId());
        result.setStatus(summary.getStatus());
        result.setImageCount(summary.getImageCount());
        result.setImageUrls(urls == null ? List.of() : urls);
        result.setSummaryText(summary.getSummaryText());
        result.setSummaryJson(summary.getSummaryJson());
        result.setModelProvider(summary.getModelProvider());
        result.setModel(summary.getModel());
        result.setPromptTokens(summary.getPromptTokens());
        result.setCompletionTokens(summary.getCompletionTokens());
        result.setTotalTokens(summary.getTotalTokens());
        result.setPowerAmount(summary.getPowerAmount());
        result.setErrorMessage(summary.getErrorMessage());
        if (StringUtils.isBlank(result.getSummaryText())
                && CommentImageSummaryStatusEnum.FAILED.name().equals(result.getStatus())) {
            result.setSummaryText(buildFallbackSummary(urls == null ? 0 : urls.size()));
        }
        return result;
    }

    private String buildFallbackSummary(int imageCount) {
        return "顾客上传了 " + imageCount + " 张图片，但系统未能识别图片内容。";
    }

    private static class VisionModelResponse {
        private String summaryText;
        private String summaryJson;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
