package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.SalesRedemptionCode;
import com.gogoga.backend.vo.AdminSalesRedemptionCodeRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SalesRedemptionCodeMapper {

    int insert(SalesRedemptionCode code);

    int updateById(SalesRedemptionCode code);

    SalesRedemptionCode selectById(@Param("id") Long id);

    SalesRedemptionCode selectByCode(@Param("code") String code);

    SalesRedemptionCode selectBySalesUserId(@Param("salesUserId") String salesUserId);

    List<AdminSalesRedemptionCodeRowVO> selectAdminList(@Param("keyword") String keyword);
}
