package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.param.AdminImBroadcastQueryParam;
import com.gogoga.backend.vo.ImBroadcastTaskVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MtImBroadcastTaskMapper {

    int insert(MtImBroadcastTask task);

    MtImBroadcastTask selectById(@Param("id") Long id);

    int markPlanned(@Param("id") Long id,
                    @Param("targetCount") int targetCount,
                    @Param("truncatedCount") int truncatedCount,
                    @Param("shardCount") int shardCount,
                    @Param("plannedAt") LocalDateTime plannedAt);

    int markSkipped(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("skipReason") String skipReason,
                    @Param("finishedAt") LocalDateTime finishedAt);

    int markSending(@Param("id") Long id, @Param("startedAt") LocalDateTime startedAt);

    /**
     * 用明细表重算出的计数覆盖批次进度。
     *
     * <p>刻意用「覆盖」而非「累加」：分片任务可能因超时回收或崩溃重跑，累加会重复计数。</p>
     */
    int updateProgress(@Param("id") Long id,
                       @Param("successCount") long successCount,
                       @Param("failCount") long failCount,
                       @Param("status") String status,
                       @Param("finishedAt") LocalDateTime finishedAt);

    List<ImBroadcastTaskVO> selectAdminPage(AdminImBroadcastQueryParam query);
}
