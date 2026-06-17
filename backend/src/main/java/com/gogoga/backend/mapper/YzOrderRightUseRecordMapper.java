package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.youzan.YzOrderRightUseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 有赞订单权益核销记录 Mapper
 */
@Mapper
public interface YzOrderRightUseRecordMapper {

    YzOrderRightUseRecord selectSuccessByOrderId(@Param("yzAppOrderId") Long yzAppOrderId);

    int insert(YzOrderRightUseRecord record);
}
