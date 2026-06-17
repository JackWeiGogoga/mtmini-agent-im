package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.SalesCommissionSnapshot;
import com.gogoga.backend.vo.SalesCommissionRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SalesCommissionSnapshotMapper {

    int insert(SalesCommissionSnapshot snapshot);

    SalesCommissionSnapshot selectByRechargeId(@Param("rechargeId") Long rechargeId);

    SalesCommissionSnapshot selectById(@Param("id") Long id);

    List<SalesCommissionRowVO> selectList(
            @Param("shopKeyword") String shopKeyword,
            @Param("redemptionCode") String redemptionCode,
            @Param("rechargeNo") String rechargeNo,
            @Param("salesUserId") String salesUserId
    );

    int updateCommissionRatio(
            @Param("id") Long id,
            @Param("commissionRatio") Integer commissionRatio,
            @Param("commissionAmountFen") Integer commissionAmountFen
    );

    int updateSettlementStatus(
            @Param("id") Long id,
            @Param("settlementStatus") String settlementStatus
    );
}
