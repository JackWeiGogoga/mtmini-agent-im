package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.youzan.YzAppOrder;
import com.gogoga.backend.vo.AdminYouzanAppOrderRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 有赞应用订购订单 Mapper
 */
@Mapper
public interface YzAppOrderMapper {

    YzAppOrder selectById(@Param("id") Long id);

    YzAppOrder selectByIdForUpdate(@Param("id") Long id);

    YzAppOrder selectByOrderNo(@Param("orderNo") String orderNo);

    int insert(YzAppOrder order);

    int updateById(YzAppOrder order);

    int updateUseStatus(YzAppOrder order);

    List<AdminYouzanAppOrderRowVO> selectAdminPage(
            @Param("orderNo") String orderNo,
            @Param("kdtId") Long kdtId,
            @Param("shopName") String shopName,
            @Param("buyerKeyword") String buyerKeyword,
            @Param("skuKeyword") String skuKeyword,
            @Param("status") Integer status,
            @Param("useStatus") String useStatus,
            @Param("payStartTime") LocalDateTime payStartTime,
            @Param("payEndTime") LocalDateTime payEndTime
    );
}
