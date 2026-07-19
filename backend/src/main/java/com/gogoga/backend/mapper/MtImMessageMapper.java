package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtImMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MtImMessageMapper {

    int insert(MtImMessage message);

    MtImMessage selectByCallbackMsgId(@Param("callbackMsgId") String callbackMsgId);

    MtImMessage selectByPlatformMessage(@Param("appId") Long appId,
                                        @Param("platformMsgId") Long platformMsgId);
}
