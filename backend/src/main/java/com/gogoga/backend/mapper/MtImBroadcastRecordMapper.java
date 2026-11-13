package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImBroadcastRecord;
import com.gogoga.backend.vo.ImBroadcastRecordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MtImBroadcastRecordMapper {

    /**
     * 批量插入目标明细；重复投递时靠 (task_id, open_user_id) 唯一键忽略。
     */
    int batchInsertIgnore(@Param("records") List<MtImBroadcastRecord> records);

    /**
     * 捞取分片内仍待发送的明细，用于首次执行与失败重试。
     */
    List<MtImBroadcastRecord> selectPendingByShard(@Param("taskId") Long taskId,
                                                   @Param("shardNo") Integer shardNo,
                                                   @Param("limit") int limit);

    int markSent(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    int markFailed(@Param("id") Long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorSummary") String errorSummary);

    /**
     * 把分片内仍待发送的明细整体置为失败，用于门店已确认失去授权、继续发送必然失败的场景。
     */
    int markShardPendingFailed(@Param("taskId") Long taskId,
                               @Param("shardNo") Integer shardNo,
                               @Param("errorCode") String errorCode,
                               @Param("errorSummary") String errorSummary);

    /**
     * 统计批次明细条数；status 为空表示统计全部。
     */
    long countByTask(@Param("taskId") Long taskId, @Param("status") String status);

    List<ImBroadcastRecordVO> selectAdminPageByTask(@Param("taskId") Long taskId,
                                                    @Param("status") String status);
}
