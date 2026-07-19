package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImAutoReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MtImAutoReplyMapper {

    int insert(MtImAutoReply autoReply);

    MtImAutoReply selectById(@Param("id") Long id);

    MtImAutoReply selectByTriggerMessageId(@Param("triggerMessageId") Long triggerMessageId);

    int update(MtImAutoReply autoReply);
}
