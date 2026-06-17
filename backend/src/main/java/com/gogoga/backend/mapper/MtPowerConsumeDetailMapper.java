package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPowerConsumeDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MtPowerConsumeDetailMapper {

    int insertBatch(@Param("list") List<MtPowerConsumeDetail> list);

    List<MtPowerConsumeDetail> selectByConsumeId(@Param("consumeId") Long consumeId);

    List<MtPowerConsumeDetail> selectByConsumeIds(@Param("consumeIds") List<Long> consumeIds);
}
