package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台总控台推广日志抽屉摘要
 */
@Data
public class AdminMasterConsolePromotionLogSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前最新商圈平均出价
     */
    private BigDecimal latestPeerAverageBid;

    /**
     * 摘要生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 营业状态
     */
    private BusinessStatus businessStatus;

    /**
     * 高峰状态
     */
    private PeakStatus peakStatus;

    @Data
    public static class BusinessStatus implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean open;
        private String shippingTime;
        private String reason;
        private String currentWindow;
    }

    @Data
    public static class PeakStatus implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean peakMatched;
        private Boolean gatingEnabled;
        private Boolean trusted;
        private String currentDayType;
        private String profileDayType;
        private String profileStatus;
        private Integer sampleDayCount;
        private Integer sampleOrderCount;
        private String unavailableReason;
        private String matchedWindow;
        private List<String> configuredWindows;
    }
}
