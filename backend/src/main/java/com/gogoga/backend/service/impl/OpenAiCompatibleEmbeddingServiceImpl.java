package com.gogoga.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.client.OpenAiCompatibleClientFactory;
import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 embedding 服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleEmbeddingServiceImpl implements CommentReplyEmbeddingService {

    private final MtProperties mtProperties;
    private final OpenAiCompatibleClientFactory clientFactory;

    @Override
    public float[] embed(String text) {
        if (StringUtils.isBlank(text)) {
            throw new BusinessException(400, "向量化文本不能为空");
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        String baseUrlConfig = StringUtils.defaultIfBlank(knowledge.getEmbeddingBaseUrl(), knowledge.getModelBaseUrl());
        String apiKeyConfig = StringUtils.defaultIfBlank(knowledge.getEmbeddingApiKey(), knowledge.getModelApiKey());
        if (StringUtils.isBlank(baseUrlConfig)) {
            throw new BusinessException(400, "Embedding baseUrl 未配置");
        }
        if (StringUtils.isBlank(apiKeyConfig)) {
            throw new BusinessException(400, "Embedding apiKey 未配置");
        }
        if (StringUtils.isBlank(knowledge.getEmbeddingModel())) {
            throw new BusinessException(400, "Embedding 模型未配置");
        }

        String baseUrl = clientFactory.normalizeBaseUrl(baseUrlConfig);
        String endpoint = StringUtils.stripEnd(baseUrl, "/") + "/embeddings";
        Map<String, Object> requestBody = Map.of(
                "model", knowledge.getEmbeddingModel(),
                "input", text,
                "dimensions", knowledge.getEmbeddingDim(),
                "encoding_format", "float"
        );
        try {
            String response = RestClient.create()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKeyConfig)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            return parseEmbedding(response, knowledge.getEmbeddingDim());
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("调用 OpenAI 兼容 embedding 失败，endpoint={}, model={}", endpoint, knowledge.getEmbeddingModel(), e);
            throw new BusinessException(502, "调用 Embedding 服务失败", e);
        }
    }

    private float[] parseEmbedding(String response, int expectedDim) {
        JsonNode root = JsonUtil.readTree(response);
        JsonNode embeddingNode = root == null ? null : root.path("data").path(0).path("embedding");
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new BusinessException(502, "Embedding 服务响应缺少向量数据");
        }
        List<Float> values = JsonUtil.getObjectMapper().convertValue(
                embeddingNode,
                JsonUtil.getObjectMapper().getTypeFactory().constructCollectionType(List.class, Float.class)
        );
        if (values == null || values.isEmpty()) {
            throw new BusinessException(502, "Embedding 服务响应向量为空");
        }
        if (expectedDim > 0 && values.size() != expectedDim) {
            throw new BusinessException(502, "Embedding 向量维度不匹配");
        }
        float[] embedding = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            embedding[i] = values.get(i) == null ? 0F : values.get(i);
        }
        return embedding;
    }
}
