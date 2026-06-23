package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtCommentReplyGeneration;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评价自动回复生成记录 Mapper。
 */
@Mapper
public interface MtCommentReplyGenerationMapper {

    MtCommentReplyGeneration selectById(Long id);

    int insert(MtCommentReplyGeneration generation);
}
