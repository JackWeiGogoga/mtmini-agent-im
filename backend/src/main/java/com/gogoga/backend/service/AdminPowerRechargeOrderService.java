package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.enums.PowerRechargeStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerRechargeMapper;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPowerRechargeOrderDetailVO;
import com.gogoga.backend.vo.AdminPowerRechargeOrderRowVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPowerRechargeOrderService {

    private final MtPowerRechargeMapper mtPowerRechargeMapper;

    public PageInfo<AdminPowerRechargeOrderRowVO> listOrders(String keyword,
                                                             Integer payStatus,
                                                             LocalDateTime startTime,
                                                             LocalDateTime endTime,
                                                             Integer pageNum,
                                                             Integer pageSize) {
        requireSuperAdmin();
        validateTimeRange(startTime, endTime);
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<AdminPowerRechargeOrderRowVO> rows = mtPowerRechargeMapper.selectAdminRechargeOrderList(
                StringUtils.trimToNull(keyword),
                payStatus,
                startTime,
                endTime
        );
        rows.forEach(this::fillPayStatusDesc);
        return new PageInfo<>(rows);
    }

    public byte[] exportXlsx(String keyword,
                             Integer payStatus,
                             LocalDateTime startTime,
                             LocalDateTime endTime) {
        requireSuperAdmin();
        validateTimeRange(startTime, endTime);
        PageHelper.startPage(1, ExcelExportUtil.MAX_EXPORT_ROWS + 1, false);
        List<AdminPowerRechargeOrderRowVO> rows;
        try {
            rows = mtPowerRechargeMapper.selectAdminRechargeOrderList(
                    StringUtils.trimToNull(keyword),
                    payStatus,
                    startTime,
                    endTime
            );
        } finally {
            PageHelper.clearPage();
        }
        rows.forEach(this::fillPayStatusDesc);
        return ExcelExportUtil.toXlsx("算力充值订单", List.of(
                ExcelExportUtil.column("充值订单号", AdminPowerRechargeOrderRowVO::getRechargeNo),
                ExcelExportUtil.column("门店名称", AdminPowerRechargeOrderRowVO::getShopName),
                ExcelExportUtil.column("门店ID", AdminPowerRechargeOrderRowVO::getPoiId),
                ExcelExportUtil.column("EPOI ID", AdminPowerRechargeOrderRowVO::getEpoiId),
                ExcelExportUtil.column("充值算力", AdminPowerRechargeOrderRowVO::getPowerAmount),
                ExcelExportUtil.column("原价金额", row -> ExcelExportUtil.moneyFen(row.getOriginalAmountFen())),
                ExcelExportUtil.column("实付金额", row -> ExcelExportUtil.moneyFen(row.getTotalAmountFen())),
                ExcelExportUtil.column("折扣标签", AdminPowerRechargeOrderRowVO::getDiscountTag),
                ExcelExportUtil.column("折扣比例", row -> formatDiscountRate(row.getDiscountRateBasisPoints())),
                ExcelExportUtil.column("网销用户", row -> StringUtils.firstNonBlank(row.getSalesUserName(), row.getSalesUserId())),
                ExcelExportUtil.column("网销手机号", AdminPowerRechargeOrderRowVO::getSalesUserPhone),
                ExcelExportUtil.column("兑换码", AdminPowerRechargeOrderRowVO::getRedemptionCode),
                ExcelExportUtil.column("冻结佣金比例", row -> ExcelExportUtil.percent(row.getDefaultCommissionRatio())),
                ExcelExportUtil.column("支付渠道", AdminPowerRechargeOrderRowVO::getPayChannel),
                ExcelExportUtil.column("支付状态", AdminPowerRechargeOrderRowVO::getPayStatusDesc),
                ExcelExportUtil.column("支付侧订单号", AdminPowerRechargeOrderRowVO::getPaymentOrderNo),
                ExcelExportUtil.column("微信交易号", AdminPowerRechargeOrderRowVO::getTransactionId),
                ExcelExportUtil.column("支付成功时间", row -> ExcelExportUtil.dateTime(row.getPaidAt())),
                ExcelExportUtil.column("创建时间", row -> ExcelExportUtil.dateTime(row.getCreateTime()))
        ), rows);
    }

    public AdminPowerRechargeOrderDetailVO getOrderDetail(Long id) {
        requireSuperAdmin();
        if (id == null) {
            throw new BusinessException("充值订单不能为空");
        }
        AdminPowerRechargeOrderDetailVO detail = mtPowerRechargeMapper.selectAdminRechargeOrderDetailById(id);
        if (detail == null) {
            throw new BusinessException("充值订单不存在");
        }
        fillPayStatusDesc(detail);
        return detail;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new BusinessException("结束时间不能早于开始时间");
        }
    }

    private void fillPayStatusDesc(AdminPowerRechargeOrderRowVO row) {
        if (row == null) {
            return;
        }
        row.setPayStatusDesc(resolvePayStatusDesc(row.getPayStatus()));
    }

    private String resolvePayStatusDesc(Integer payStatus) {
        if (payStatus == null) {
            return "";
        }
        for (PowerRechargeStatusEnum statusEnum : PowerRechargeStatusEnum.values()) {
            if (statusEnum.getCode() == payStatus) {
                return statusEnum.getDesc();
            }
        }
        return String.valueOf(payStatus);
    }

    private String formatDiscountRate(Integer discountRateBasisPoints) {
        if (discountRateBasisPoints == null) {
            return "";
        }
        if (discountRateBasisPoints == 10000) {
            return "原价";
        }
        return (discountRateBasisPoints / 100.0) + "%";
    }

    private void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(403, "无权查看充值订单");
        }
    }
}
