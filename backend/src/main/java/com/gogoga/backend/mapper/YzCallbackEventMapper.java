package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.youzan.YzCallbackEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 有赞回调事件 Mapper
 */
@Mapper
public interface YzCallbackEventMapper {

    int insert(YzCallbackEvent event);

    int updateById(YzCallbackEvent event);
}
