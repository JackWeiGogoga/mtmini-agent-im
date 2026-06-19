package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPowerConsume;
import com.gogoga.backend.entity.mt.MtPowerConsumeDetail;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.constant.PowerThresholdConstant;
import com.gogoga.backend.enums.ShopBindingDomainEnum;
import com.gogoga.backend.enums.ShopBindingStatusEnum;
import com.gogoga.backend.enums.PowerRechargeStatusEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerConsumeDetailMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import com.gogoga.backend.mapper.MtPowerRechargeMapper;
import com.gogoga.backend.mapper.UserShopBindingMapper;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPowerAccountSummaryVO;
import com.gogoga.backend.vo.AdminPowerConsumeDetailVO;
import com.gogoga.backend.vo.AdminPowerConsumeRecordVO;
import com.gogoga.backend.vo.AdminPowerManagementRowVO;
import com.gogoga.backend.vo.AdminPowerRechargeRecordVO;
import com.gogoga.backend.vo.AdminPowerGrantVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台算力管理服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminPowerManagementService {

    private static final String RISK_STATUS_NORMAL = "NORMAL";
    private static final String RISK_STATUS_WARNING = "WARNING";
    private static final String RISK_STATUS_CRITICAL = "CRITICAL";

    private final UserShopBindingMapper userShopBindingMapper;
    private final UserShopBindingService userShopBindingService;
    private final MtShopService mtShopService;
    private final MtPowerAccountService mtPowerAccountService;
    private final MtPowerRechargeMapper mtPowerRechargeMapper;
    private final MtPowerConsumeMapper mtPowerConsumeMapper;
    private final MtPowerConsumeDetailMapper mtPowerConsumeDetailMapper;
    private final AdminPowerGrantService adminPowerGrantService;

    public PageInfo<AdminPowerManagementRowVO> listRows(
            String keyword,
            String targetUserId,
            String riskStatus,
            Integer pageNum,
            Integer pageSize
    ) {
        return listRows(keyword, targetUserId, null, null, riskStatus, pageNum, pageSize);
    }

    public PageInfo<AdminPowerManagementRowVO> listRows(
            String keyword,
            String targetUserId,
            String bindingDomain,
            String bindingStatus,
            String riskStatus,
            Integer pageNum,
            Integer pageSize
    ) {
        return listRows(keyword, targetUserId, bindingDomain, bindingStatus, riskStatus, null, null, pageNum, pageSize);
    }

    public PageInfo<AdminPowerManagementRowVO> listRows(
            String keyword,
            String targetUserId,
            String bindingDomain,
            String bindingStatus,
            String riskStatus,
            String sortField,
            String sortOrder,
            Integer pageNum,
            Integer pageSize
    ) {
        requireSuperAdmin();
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String normalizedRiskStatus = normalizeRiskStatus(riskStatus);
        String normalizedSortDirection = normalizePowerBalanceSortDirection(sortField, sortOrder);
        ShopBindingDomainEnum normalizedBindingDomain = normalizeBindingDomain(bindingDomain);
        ShopBindingStatusEnum normalizedBindingStatus = normalizeBindingStatus(bindingStatus);
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<AdminPowerManagementRowVO> rows = userShopBindingMapper.selectAdminPowerManagedMerchantShops(
                normalizedBindingDomain.name(),
                normalizedBindingStatus.name(),
                StringUtils.trimToNull(targetUserId),
                StringUtils.trimToNull(keyword),
                normalizedRiskStatus,
                normalizedSortDirection
        );
        rows.forEach(this::fillRiskStatus);
        warnDuplicateMerchantBindings(rows, "power-management");
        return new PageInfo<>(rows);
    }

    public PageInfo<AdminPowerManagementRowVO> listRows(
            String keyword,
            String targetUserId,
            String bindingDomain,
            String riskStatus,
            Integer pageNum,
            Integer pageSize
    ) {
        return listRows(keyword, targetUserId, bindingDomain, null, riskStatus, pageNum, pageSize);
    }

    private String normalizePowerBalanceSortDirection(String sortField, String sortOrder) {
        if (!StringUtils.equalsAnyIgnoreCase(StringUtils.trimToEmpty(sortField), "powerBalance", "balance")) {
            return null;
        }
        String normalizedOrder = StringUtils.trimToEmpty(sortOrder);
        if (StringUtils.equalsAnyIgnoreCase(normalizedOrder, "ascend", "asc")) {
            return "ASC";
        }
        if (StringUtils.equalsAnyIgnoreCase(normalizedOrder, "descend", "desc")) {
            return "DESC";
        }
        return null;
    }

    public AdminPowerAccountSummaryVO getSummary(Long shopId) {
        requireSuperAdmin();
        MtShop shop = requireAccessibleShop(shopId);
        MtPowerAccount account = mtPowerAccountService.getOrCreateAccount(shop.getId());

        AdminPowerAccountSummaryVO summary = new AdminPowerAccountSummaryVO();
        summary.setShopId(shop.getId());
        summary.setPoiId(shop.getPoiId());
        summary.setEpoiId(shop.getEpoiId());
        summary.setShopName(StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName()));
        summary.setAddress(shop.getAddress());
        summary.setBalance(defaultInt(account.getBalance()));
        summary.setRechargeBalance(defaultInt(account.getRechargeBalance()));
        summary.setGrantBalance(defaultInt(account.getGrantBalance()));
        summary.setTotalRecharge(defaultInt(account.getTotalRecharge()));
        summary.setTotalGrant(defaultInt(account.getTotalGrant()));
        summary.setTotalConsume(defaultInt(account.getTotalConsume()));
        summary.setRiskStatus(resolveRiskStatus(account.getBalance()));

        AdminPowerManagementRowVO binding = userShopBindingMapper.selectPowerManagedMerchantByShopId(shop.getId());
        if (binding != null) {
            summary.setBoundUserId(binding.getBoundUserId());
            summary.setBoundUserName(binding.getBoundUserName());
            summary.setBindingStatus(binding.getBindingStatus());
            summary.setBindingStatusName(binding.getBindingStatusName());
            summary.setOrdinaryBindingId(binding.getOrdinaryBindingId());
            summary.setOrdinaryBoundUserId(binding.getOrdinaryBoundUserId());
            summary.setOrdinaryBoundUserName(binding.getOrdinaryBoundUserName());
            summary.setSalesBindingId(binding.getSalesBindingId());
            summary.setSalesBoundUserId(binding.getSalesBoundUserId());
            summary.setSalesBoundUserName(binding.getSalesBoundUserName());
        }
        return summary;
    }

    public PageInfo<AdminPowerRechargeRecordVO> listRechargeRecords(
            Long shopId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize
    ) {
        requireSuperAdmin();
        validateTimeRange(startTime, endTime);
        MtShop shop = requireAccessibleShop(shopId);
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<AdminPowerRechargeRecordVO> records = mtPowerRechargeMapper
                .selectByShopIdAndCreateTimeBetween(shop.getId(), startTime, endTime)
                .stream()
                .map(this::toRechargeRecord)
                .collect(Collectors.toList());
        return new PageInfo<>(records);
    }

    public PageInfo<AdminPowerGrantVO> listGrantRecords(
            Long shopId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize
    ) {
        requireSuperAdmin();
        requireAccessibleShop(shopId);
        validateTimeRange(startTime, endTime);
        return adminPowerGrantService.listByShopId(shopId, startTime, endTime, pageNum, pageSize);
    }

    public PageInfo<AdminPowerConsumeRecordVO> listConsumeRecords(
            Long shopId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize
    ) {
        requireSuperAdmin();
        validateTimeRange(startTime, endTime);
        MtShop shop = requireAccessibleShop(shopId);
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<MtPowerConsume> consumes = mtPowerConsumeMapper.selectByShopIdAndCreateTimeBetween(
                shop.getId(),
                startTime,
                endTime
        );
        List<Long> consumeIds = consumes.stream()
                .map(MtPowerConsume::getId)
                .collect(Collectors.toList());
        Map<Long, List<MtPowerConsumeDetail>> detailMap = new LinkedHashMap<>();
        if (!consumeIds.isEmpty()) {
            for (MtPowerConsumeDetail detail : mtPowerConsumeDetailMapper.selectByConsumeIds(consumeIds)) {
                detailMap.computeIfAbsent(detail.getConsumeId(), key -> new ArrayList<>()).add(detail);
            }
        }
        List<AdminPowerConsumeRecordVO> records = consumes.stream()
                .map(consume -> toConsumeRecord(consume, detailMap.getOrDefault(consume.getId(), List.of())))
                .collect(Collectors.toList());
        return new PageInfo<>(records);
    }

    private AdminPowerRechargeRecordVO toRechargeRecord(MtPowerRecharge recharge) {
        AdminPowerRechargeRecordVO vo = new AdminPowerRechargeRecordVO();
        vo.setId(recharge.getId());
        vo.setRechargeNo(recharge.getRechargeNo());
        vo.setShopId(recharge.getShopId());
        vo.setPowerAmount(recharge.getPowerAmount());
        vo.setUnitPower(recharge.getUnitPower());
        vo.setUnitPriceFen(recharge.getUnitPriceFen());
        vo.setTotalAmountFen(recharge.getTotalAmountFen());
        vo.setPayChannel(recharge.getPayChannel());
        vo.setPayStatus(recharge.getPayStatus());
        vo.setPayStatusDesc(resolvePayStatusDesc(recharge.getPayStatus()));
        vo.setPaymentOrderNo(recharge.getPaymentOrderNo());
        vo.setTransactionId(recharge.getTransactionId());
        vo.setPaidAt(recharge.getPaidAt());
        vo.setCreateTime(recharge.getCreateTime());
        return vo;
    }

    private AdminPowerConsumeRecordVO toConsumeRecord(MtPowerConsume consume, List<MtPowerConsumeDetail> details) {
        AdminPowerConsumeRecordVO vo = new AdminPowerConsumeRecordVO();
        vo.setId(consume.getId());
        vo.setConsumeNo(consume.getConsumeNo());
        vo.setShopId(consume.getShopId());
        vo.setSourceType(consume.getSourceType());
        vo.setSourceTypeText(resolveConsumeSourceTypeText(consume.getSourceType()));
        vo.setSourceId(consume.getSourceId());
        vo.setRequestNo(consume.getRequestNo());
        vo.setPowerAmount(consume.getPowerAmount());
        vo.setBalanceBefore(consume.getBalanceBefore());
        vo.setBalanceAfter(consume.getBalanceAfter());
        vo.setRemark(consume.getRemark());
        vo.setCreateTime(consume.getCreateTime());
        vo.setDetails(details.stream().map(this::toConsumeDetail).collect(Collectors.toList()));
        return vo;
    }

    private AdminPowerConsumeDetailVO toConsumeDetail(MtPowerConsumeDetail detail) {
        AdminPowerConsumeDetailVO vo = new AdminPowerConsumeDetailVO();
        vo.setSourceBalanceType(detail.getSourceBalanceType());
        vo.setSourceBalanceTypeText(resolveSourceBalanceTypeText(detail.getSourceBalanceType()));
        vo.setPowerAmount(detail.getPowerAmount());
        vo.setBalanceBefore(detail.getBalanceBefore());
        vo.setBalanceAfter(detail.getBalanceAfter());
        return vo;
    }

    private void fillRiskStatus(AdminPowerManagementRowVO row) {
        row.setBalance(defaultInt(row.getBalance()));
        row.setRechargeBalance(defaultInt(row.getRechargeBalance()));
        row.setGrantBalance(defaultInt(row.getGrantBalance()));
        row.setTotalRecharge(defaultInt(row.getTotalRecharge()));
        row.setTotalGrant(defaultInt(row.getTotalGrant()));
        row.setTotalConsume(defaultInt(row.getTotalConsume()));
        row.setRiskStatus(resolveRiskStatus(row.getBalance()));
    }

    private String normalizeRiskStatus(String riskStatus) {
        String normalized = StringUtils.upperCase(StringUtils.trimToNull(riskStatus));
        if (normalized == null) {
            return null;
        }
        if (RISK_STATUS_NORMAL.equals(normalized)
                || RISK_STATUS_WARNING.equals(normalized)
                || RISK_STATUS_CRITICAL.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("算力风险状态不正确");
    }

    private ShopBindingDomainEnum normalizeBindingDomain(String bindingDomain) {
        ShopBindingDomainEnum domain = ShopBindingDomainEnum.fromValue(bindingDomain);
        return domain == null ? ShopBindingDomainEnum.ALL : domain;
    }

    private ShopBindingStatusEnum normalizeBindingStatus(String bindingStatus) {
        ShopBindingStatusEnum status = ShopBindingStatusEnum.fromValue(bindingStatus);
        return status == null ? ShopBindingStatusEnum.ALL : status;
    }

    private String resolveRiskStatus(Integer balance) {
        int currentBalance = defaultInt(balance);
        if (currentBalance < PowerThresholdConstant.CRITICAL_POWER_BALANCE) {
            return RISK_STATUS_CRITICAL;
        }
        if (currentBalance < PowerThresholdConstant.WARNING_POWER_BALANCE) {
            return RISK_STATUS_WARNING;
        }
        return RISK_STATUS_NORMAL;
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

    private String resolveConsumeSourceTypeText(String sourceType) {
        if (StringUtils.isBlank(sourceType)) {
            return "";
        }
        if (MtPaidApiPowerConsumeService.CONSUME_SOURCE_AUTHORIZED_SHOP.equals(sourceType)) {
            return "授权门店详情";
        }
        if (MtPaidApiPowerConsumeService.CONSUME_SOURCE_ORDER_LIST.equals(sourceType)) {
            return "订单列表";
        }
        if (MtPaidApiPowerConsumeService.CONSUME_SOURCE_ORDER_DETAIL.equals(sourceType)) {
            return "订单详情";
        }
        return sourceType;
    }

    private String resolveSourceBalanceTypeText(String sourceBalanceType) {
        if (MtPowerConsumeService.SOURCE_BALANCE_GRANT.equals(sourceBalanceType)) {
            return "赠送算力";
        }
        if (MtPowerConsumeService.SOURCE_BALANCE_RECHARGE.equals(sourceBalanceType)) {
            return "实充算力";
        }
        return sourceBalanceType;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
    }

    private void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(403, "仅超级管理员可执行该操作");
        }
    }

    private MtShop requireAccessibleShop(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        if (UserContext.isSuperAdmin()) {
            MtShop shop = mtShopService.findById(shopId);
            if (shop == null) {
                throw new BusinessException("门店不存在");
            }
            return shop;
        }
        return userShopBindingService.requireCurrentUserBoundShop(shopId);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void warnDuplicateMerchantBindings(List<AdminPowerManagementRowVO> rows, String scene) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (AdminPowerManagementRowVO row : rows) {
            if (row == null || row.getShopId() == null) {
                continue;
            }
            if (row.getOrdinaryBindingCount() != null && row.getOrdinaryBindingCount() > 1) {
                log.warn("检测到普通商户绑定重复，scene={}, shopId={}, count={}, chosenBindingId={}",
                        scene, row.getShopId(), row.getOrdinaryBindingCount(), row.getOrdinaryBindingId());
            }
            if (row.getSalesBindingCount() != null && row.getSalesBindingCount() > 1) {
                log.warn("检测到网销用户绑定重复，scene={}, shopId={}, count={}, chosenBindingId={}",
                        scene, row.getShopId(), row.getSalesBindingCount(), row.getSalesBindingId());
            }
        }
    }
}
