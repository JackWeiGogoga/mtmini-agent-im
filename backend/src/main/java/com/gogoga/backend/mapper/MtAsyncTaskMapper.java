package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtAsyncTask;
import com.gogoga.backend.entity.mt.MtAsyncTaskBacklogSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库异步任务 Mapper。
 */
@Mapper
public interface MtAsyncTaskMapper {

    int insert(MtAsyncTask task);

    MtAsyncTask selectById(@Param("id") Long id);

    MtAsyncTask selectByTaskTypeAndBizKey(
            @Param("taskType") String taskType,
            @Param("bizKey") String bizKey
    );

    List<Long> selectExecutableIds(
            @Param("taskType") String taskType,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("limit") int limit
    );

    int claimPendingById(
            @Param("id") Long id,
            @Param("taskType") String taskType,
            @Param("workerId") String workerId,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    int markSuccess(
            @Param("id") Long id,
            @Param("taskType") String taskType,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    int rescheduleFromProcessing(
            @Param("id") Long id,
            @Param("taskType") String taskType,
            @Param("retryCount") Integer retryCount,
            @Param("availableAt") LocalDateTime availableAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int markDead(
            @Param("id") Long id,
            @Param("taskType") String taskType,
            @Param("retryCount") Integer retryCount,
            @Param("finishedAt") LocalDateTime finishedAt,
            @Param("lastError") String lastError
    );

    int resetForManualRetry(
            @Param("id") Long id,
            @Param("taskType") String taskType,
            @Param("maxRetryCount") Integer maxRetryCount,
            @Param("availableAt") LocalDateTime availableAt
    );

    List<MtAsyncTask> selectStaleProcessingTasks(
            @Param("taskType") String taskType,
            @Param("claimedBefore") LocalDateTime claimedBefore,
            @Param("limit") int limit
    );

    List<MtAsyncTaskBacklogSummary> selectBacklogSummary(@Param("currentTime") LocalDateTime currentTime);

    int deleteHistoricalTerminalTasks(
            @Param("status") String status,
            @Param("finishedBefore") LocalDateTime finishedBefore,
            @Param("limit") int limit
    );
}
