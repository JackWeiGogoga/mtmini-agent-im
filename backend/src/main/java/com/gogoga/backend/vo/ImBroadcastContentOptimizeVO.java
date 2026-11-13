package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 群发文案优化结果。
 */
@Data
public class ImBroadcastContentOptimizeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优化后的文案正文，已做敏感词校验与长度收敛。
     */
    private String content;
}
