package com.gogoga.backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识库测试聊天回复及调试信息。
 */
@Data
public class ImTestChatReplyVO {

    private String reply;
    private String category;
    private Boolean ragUsed;
    private Integer ragChunkCount;
    private Double ragMaxScore;
    private Integer rewriteCount;
    private Boolean blocked;
    private Integer promptTokens;
    private Integer completionTokens;
    private List<RagHit> ragHits;

    /**
     * 召回诊断信息，用于排查“为何未命中”。
     */
    private Diagnostics diagnostics;

    /**
     * 触发敏感词改写/兜底时的命中详情（未触发则为 null），仅供后台排查。
     */
    private SensitiveHit sensitiveHit;

    @Data
    public static class SensitiveHit {
        private List<String> categories;
        private List<String> words;
        private String highestSeverity;
        private Integer hitCount;
    }

    @Data
    public static class RagHit {
        private String chunkText;
        private Double score;
    }

    @Data
    public static class Diagnostics {
        /** 进入回复的最低余弦阈值。 */
        private Double threshold;
        /** 门店是否存在任何可用知识（未过 embedding 配置过滤）。 */
        private Boolean hasShopKnowledge;
        /** 匹配当前 embedding 配置的候选数（诊断样本）。 */
        private Integer candidateCount;
        /** 当前 embedding 配置：provider / model / dim。 */
        private String embeddingConfig;
        /** Top-N 候选及其原始余弦分（含低于阈值者）。 */
        private List<Candidate> topCandidates;

        @Data
        public static class Candidate {
            private String chunkText;
            private Double cosineScore;
            private String sourceType;
            private String category;
            /** 该候选是否达到阈值。 */
            private Boolean passed;
        }
    }
}
