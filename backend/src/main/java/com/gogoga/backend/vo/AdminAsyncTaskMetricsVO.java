package com.gogoga.backend.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 后台异步任务监控摘要。
 */
@Data
public class AdminAsyncTaskMetricsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private LocalDateTime generatedAt;

    private Boolean asyncTaskEnabled;

    private Boolean workerEnabled;

    private Integer pollSize;

    private Long pollDelayMs;

    private Map<String, Long> counters;

    private List<TaskBacklogVO> taskBacklogs;

    @Data
    public static class TaskBacklogVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String taskType;

        private Long pendingCount;

        private Long readyCount;

        private Long processingCount;

        private Long successCount;

        private Long deadCount;
    }
}
