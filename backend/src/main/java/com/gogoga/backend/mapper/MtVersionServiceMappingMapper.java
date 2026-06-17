package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtVersionServiceMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 美团版本权益映射 Mapper
 *
 * @author system
 * @since 2026-03-25
 */
@Mapper
public interface MtVersionServiceMappingMapper {

    /**
     * 查询指定版本的启用映射
     *
     * @param mtVersion 美团版本号
     * @return 映射列表
     */
    List<MtVersionServiceMapping> selectEnabledByMtVersion(@Param("mtVersion") String mtVersion);
}
