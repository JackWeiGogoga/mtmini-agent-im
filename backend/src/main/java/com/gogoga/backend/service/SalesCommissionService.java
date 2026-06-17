package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.SalesCommissionSnapshot;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.SalesCommissionSettlementStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.SalesCommissionSnapshotMapper;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.SalesCommissionRowVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesCommissionService {

    private final SalesCommissionSnapshotMapper salesCommissionSnapshotMapper;
    private final SalesReceiptCodePreviewService salesReceiptCodePreviewService;

    @Transactional(rollbackFor = Exception.class)
    public void createSnapshotForPaidRecharge(MtPowerRecharge recharge, MtShop shop) {
        if (recharge == null || recharge.getId() == null || StringUtils.isBlank(recharge.getSalesUserId())) {
            return;
        }
        if (salesCommissionSnapshotMapper.selectByRechargeId(recharge.getId()) != null) {
            return;
        }
        int commissionRatio = recharge.getDefaultCommissionRatio() == null ? 0 : recharge.getDefaultCommissionRatio();
        SalesCommissionSnapshot snapshot = new SalesCommissionSnapshot();
        snapshot.setRechargeId(recharge.getId());
        snapshot.setRechargeNo(recharge.getRechargeNo());
        snapshot.setShopId(recharge.getShopId());
        snapshot.setPoiId(shop == null ? null : shop.getPoiId());
        snapshot.setShopName(shop == null ? null : StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName()));
        snapshot.setShopPhone(shop == null ? null : StringUtils.firstNonBlank(shop.getPhone(), shop.getContactPhone()));
        snapshot.setPowerAmount(recharge.getPowerAmount());
        snapshot.setPaidAmountFen(recharge.getTotalAmountFen());
        snapshot.setCommissionRatio(commissionRatio);
        snapshot.setCommissionAmountFen(calculateCommissionAmountFen(recharge.getTotalAmountFen(), commissionRatio));
        snapshot.setSalesUserId(recharge.getSalesUserId());
        snapshot.setSalesUserPhone(recharge.getSalesUserPhone());
        snapshot.setRedemptionCode(recharge.getRedemptionCode());
        snapshot.setSettlementStatus(SalesCommissionSettlementStatusEnum.UNSETTLED.name());
        snapshot.setCreateTime(LocalDateTime.now());
        snapshot.setUpdateTime(LocalDateTime.now());
        salesCommissionSnapshotMapper.insert(snapshot);
    }

    public PageInfo<SalesCommissionRowVO> listRows(String shopKeyword,
                                                   String redemptionCode,
                                                   String rechargeNo,
                                                   Integer pageNum,
                                                   Integer pageSize) {
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String salesUserId = UserContext.isSuperAdmin() ? null : UserContext.getUserId();
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<SalesCommissionRowVO> rows = salesCommissionSnapshotMapper.selectList(
                StringUtils.trimToNull(shopKeyword),
                StringUtils.trimToNull(StringUtils.upperCase(redemptionCode)),
                StringUtils.trimToNull(rechargeNo),
                salesUserId
        );
        rows.forEach(row -> {
            com.gogoga.backend.entity.SalesUserProfile profile = new com.gogoga.backend.entity.SalesUserProfile();
            profile.setUserId(row.getSalesUserId());
            profile.setReceiptCodeObjectKey(row.getReceiptCodeObjectKey());
            profile.setReceiptCodeUrl(row.getReceiptCodeUrl());
            row.setReceiptCodeUrl(salesReceiptCodePreviewService.buildPreviewUrl(row.getSalesUserId(), profile));
        });
        return new PageInfo<>(rows);
    }

    public byte[] exportXlsx(String shopKeyword,
                             String redemptionCode,
                             String rechargeNo) {
        String salesUserId = UserContext.isSuperAdmin() ? null : UserContext.getUserId();
        PageHelper.startPage(1, ExcelExportUtil.MAX_EXPORT_ROWS + 1, false);
        List<SalesCommissionRowVO> rows;
        try {
            rows = salesCommissionSnapshotMapper.selectList(
                    StringUtils.trimToNull(shopKeyword),
                    StringUtils.trimToNull(StringUtils.upperCase(redemptionCode)),
                    StringUtils.trimToNull(rechargeNo),
                    salesUserId
            );
        } finally {
            PageHelper.clearPage();
        }
        return ExcelExportUtil.toXlsx("销售分佣", List.of(
                ExcelExportUtil.column("兑换码", SalesCommissionRowVO::getRedemptionCode),
                ExcelExportUtil.column("充值订单号", SalesCommissionRowVO::getRechargeNo),
                ExcelExportUtil.column("充值时间", row -> ExcelExportUtil.dateTime(row.getPaidAt())),
                ExcelExportUtil.column("商家名称", SalesCommissionRowVO::getShopName),
                ExcelExportUtil.column("门店ID", SalesCommissionRowVO::getPoiId),
                ExcelExportUtil.column("商家手机号", SalesCommissionRowVO::getShopPhone),
                ExcelExportUtil.column("充值算力", SalesCommissionRowVO::getPowerAmount),
                ExcelExportUtil.column("充值金额", row -> ExcelExportUtil.moneyFen(row.getPaidAmountFen())),
                ExcelExportUtil.column("佣金比例", row -> ExcelExportUtil.percent(row.getCommissionRatio())),
                ExcelExportUtil.column("提成金额", row -> ExcelExportUtil.moneyFen(row.getCommissionAmountFen())),
                ExcelExportUtil.column("网销用户", row -> StringUtils.firstNonBlank(row.getSalesUserName(), row.getSalesUserId())),
                ExcelExportUtil.column("网销手机号", SalesCommissionRowVO::getSalesUserPhone),
                ExcelExportUtil.column("结算状态", row -> resolveSettlementStatusDesc(row.getSettlementStatus())),
                ExcelExportUtil.column("结算时间", row -> ExcelExportUtil.dateTime(row.getSettledAt())),
                ExcelExportUtil.column("创建时间", row -> ExcelExportUtil.dateTime(row.getCreateTime()))
        ), rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCommissionRatio(Long snapshotId, Integer commissionRatio) {
        requireSuperAdmin();
        SalesCommissionSnapshot snapshot = requireSnapshot(snapshotId);
        if (isSettled(snapshot)) {
            throw new BusinessException("已结算订单不允许修改佣金比例");
        }
        int updated = salesCommissionSnapshotMapper.updateCommissionRatio(
                snapshotId,
                commissionRatio,
                calculateCommissionAmountFen(snapshot.getPaidAmountFen(), commissionRatio)
        );
        if (updated <= 0) {
            throw new BusinessException("佣金比例更新失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSettlementStatus(Long snapshotId, String settlementStatus) {
        requireSuperAdmin();
        SalesCommissionSnapshot snapshot = requireSnapshot(snapshotId);
        SalesCommissionSettlementStatusEnum targetStatus = SalesCommissionSettlementStatusEnum.fromValue(settlementStatus);
        if (targetStatus == null) {
            throw new BusinessException("结算状态不正确");
        }
        if (isSettled(snapshot) && targetStatus != SalesCommissionSettlementStatusEnum.SETTLED) {
            throw new BusinessException("已结算订单不允许重新打开");
        }
        salesCommissionSnapshotMapper.updateSettlementStatus(snapshotId, targetStatus.name());
    }

    private SalesCommissionSnapshot requireSnapshot(Long snapshotId) {
        if (snapshotId == null) {
            throw new BusinessException("提成记录不能为空");
        }
        SalesCommissionSnapshot snapshot = salesCommissionSnapshotMapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException("提成记录不存在");
        }
        return snapshot;
    }

    private boolean isSettled(SalesCommissionSnapshot snapshot) {
        return snapshot != null && SalesCommissionSettlementStatusEnum.SETTLED.name().equalsIgnoreCase(snapshot.getSettlementStatus());
    }

    private String resolveSettlementStatusDesc(String settlementStatus) {
        if (SalesCommissionSettlementStatusEnum.SETTLED.name().equalsIgnoreCase(settlementStatus)) {
            return "已结算";
        }
        if (SalesCommissionSettlementStatusEnum.UNSETTLED.name().equalsIgnoreCase(settlementStatus)) {
            return "未结算";
        }
        return StringUtils.defaultString(settlementStatus);
    }

    private int calculateCommissionAmountFen(Integer paidAmountFen, Integer commissionRatio) {
        int safePaidAmount = paidAmountFen == null ? 0 : paidAmountFen;
        int safeRatio = commissionRatio == null ? 0 : commissionRatio;
        return BigDecimal.valueOf(safePaidAmount)
                .multiply(BigDecimal.valueOf(safeRatio))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(403, "无权执行该操作");
        }
    }
}
