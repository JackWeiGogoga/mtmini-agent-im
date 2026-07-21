package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImTestChatLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MtImTestChatLogMapper {

    int insert(MtImTestChatLog log);

    /**
     * 统计指定门店在 since 之后的测试消息条数，用于每日限流。
     */
    long countByShopSince(@Param("shopId") Long shopId, @Param("since") LocalDateTime since);

    List<MtImTestChatLog> selectByShop(@Param("shopId") Long shopId);
}
