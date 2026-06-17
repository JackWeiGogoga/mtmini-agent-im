package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.gogoga.backend.param.AdminMtAppOrderQueryParam;
import com.gogoga.backend.param.AdminMtAppOrderSettlementQueryParam;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminMtAppOrderRowVO;
import com.gogoga.backend.vo.AdminMtAppOrderSettlementRowVO;
import com.gogoga.backend.vo.AdminMtAppOrderVersionOptionVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 后台美团订阅订单服务
 */
@Service
@RequiredArgsConstructor
public class AdminMtAppOrderService {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();
    private static final int MT_APP_ORDER_PAID_STATUS = 2;
    private static final BigDecimal YUAN_DIVISOR = BigDecimal.valueOf(100);
    private static final BigDecimal MEITUAN_DEDUCTION_RATE = BigDecimal.valueOf(0.2);
    private static final BigDecimal NET_RATE = BigDecimal.valueOf(0.8);

    private final MtAppOrderMapper mtAppOrderMapper;
    private final AdminDataScopeService adminDataScopeService;

    public PageInfo<AdminMtAppOrderRowVO> listRows(AdminMtAppOrderQueryParam param) {
        AdminMtAppOrderQueryParam query = prepareQuery(param);
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        return new PageInfo<>(selectRows(query));
    }

    public byte[] exportXlsx(AdminMtAppOrderQueryParam param) {
        AdminMtAppOrderQueryParam query = prepareQuery(param);
        PageHelper.startPage(1, ExcelExportUtil.MAX_EXPORT_ROWS + 1, false);
        List<AdminMtAppOrderRowVO> rows;
        try {
            rows = selectRows(query);
        } finally {
            PageHelper.clearPage();
        }
        return ExcelExportUtil.toXlsx("美团应用订单", List.of(
                ExcelExportUtil.column("订单号", AdminMtAppOrderRowVO::getOrderId),
                ExcelExportUtil.column("展示订单号", AdminMtAppOrderRowVO::getOrderViewId),
                ExcelExportUtil.column("门店名称", AdminMtAppOrderRowVO::getShopName),
                ExcelExportUtil.column("门店ID", AdminMtAppOrderRowVO::getPoiId),
                ExcelExportUtil.column("服务标识", AdminMtAppOrderRowVO::getServiceCode),
                ExcelExportUtil.column("支付状态", row -> row.getPayStatus() == null ? "" : row.getPayStatus()),
                ExcelExportUtil.column("权益状态", AdminMtAppOrderRowVO::getRightStatus),
                ExcelExportUtil.column("版本号", AdminMtAppOrderRowVO::getVersion),
                ExcelExportUtil.column("版本名称", AdminMtAppOrderRowVO::getVersionName),
                ExcelExportUtil.column("天数", AdminMtAppOrderRowVO::getDays),
                ExcelExportUtil.column("实付金额", row -> ExcelExportUtil.moneyFen(row.getRealPayPrice())),
                ExcelExportUtil.column("权益累加", row -> row.getType() == null ? "" : row.getType() == 1 ? "已累加" : "待累加"),
                ExcelExportUtil.column("服务开始时间", row -> ExcelExportUtil.dateTime(row.getStartTime())),
                ExcelExportUtil.column("服务结束时间", row -> ExcelExportUtil.dateTime(row.getEndTime())),
                ExcelExportUtil.column("下单时间", row -> ExcelExportUtil.dateTime(row.getOrderTime())),
                ExcelExportUtil.column("联系方式", AdminMtAppOrderRowVO::getPhone),
                ExcelExportUtil.column("地址", AdminMtAppOrderRowVO::getAddress),
                ExcelExportUtil.column("创建时间", row -> ExcelExportUtil.dateTime(row.getCreateTime()))
        ), rows);
    }

    public List<AdminMtAppOrderVersionOptionVO> listVersionOptions() {
        AdminOrderScope scope = resolveCurrentScope();
        if (scope.isEmptyTeamScope()) {
            return List.of();
        }
        return mtAppOrderMapper.selectAdminVersionOptions(
                scope.scopeTypeName(),
                scope.currentUserId(),
                scope.visibleUserIds()
        );
    }

    public PageInfo<AdminMtAppOrderSettlementRowVO> listSettlementRows(AdminMtAppOrderSettlementQueryParam param) {
        SettlementQuery query = prepareSettlementQuery(param);
        int pageNum = query.param().getPageNum() == null || query.param().getPageNum() < 1 ? 1 : query.param().getPageNum();
        int pageSize = query.param().getPageSize() == null || query.param().getPageSize() < 1 ? 10 : query.param().getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        return new PageInfo<>(selectSettlementRows(query));
    }

    public byte[] exportSettlementXlsx(AdminMtAppOrderSettlementQueryParam param) {
        SettlementQuery query = prepareSettlementQuery(param);
        PageHelper.startPage(1, ExcelExportUtil.MAX_EXPORT_ROWS + 1, false);
        List<AdminMtAppOrderSettlementRowVO> rows;
        try {
            rows = selectSettlementRows(query);
        } finally {
            PageHelper.clearPage();
        }
        return ExcelExportUtil.toXlsx("美团订单结算统计", List.of(
                ExcelExportUtil.column("订单号", AdminMtAppOrderSettlementRowVO::getOrderId),
                ExcelExportUtil.column("下单时间", row -> ExcelExportUtil.dateTime(row.getOrderTime())),
                ExcelExportUtil.column("店铺名称", AdminMtAppOrderSettlementRowVO::getShopName),
                ExcelExportUtil.column("手机号", AdminMtAppOrderSettlementRowVO::getPhone),
                ExcelExportUtil.column("付费规格", AdminMtAppOrderSettlementRowVO::getPaidSpec),
                ExcelExportUtil.column("付费金额", row -> formatMoney(row.getPaidAmount())),
                ExcelExportUtil.column("本月使用天数", AdminMtAppOrderSettlementRowVO::getCurrentMonthUsedDays),
                ExcelExportUtil.column("上月使用天数", AdminMtAppOrderSettlementRowVO::getPreviousMonthUsedDays),
                ExcelExportUtil.column("剩余未使用天数", AdminMtAppOrderSettlementRowVO::getRemainingUnusedDays),
                ExcelExportUtil.column("美团扣点20%", row -> formatMoney(row.getMeituanDeductionAmount())),
                ExcelExportUtil.column("上月实际收入", row -> formatMoney(row.getPreviousMonthActualIncome())),
                ExcelExportUtil.column("本月实际收入", row -> formatMoney(row.getCurrentMonthActualIncome())),
                ExcelExportUtil.column("剩余未结实际收入", row -> formatMoney(row.getRemainingUnsettledActualIncome()))
        ), rows);
    }

    private AdminMtAppOrderQueryParam prepareQuery(AdminMtAppOrderQueryParam param) {
        AdminMtAppOrderQueryParam query = param == null ? new AdminMtAppOrderQueryParam() : param;
        if (query.getOrderStartTime() != null
                && query.getOrderEndTime() != null
                && query.getOrderStartTime().isAfter(query.getOrderEndTime())) {
            throw new BusinessException("下单开始时间不能晚于结束时间");
        }
        return query;
    }

    private List<AdminMtAppOrderRowVO> selectRows(AdminMtAppOrderQueryParam query) {
        String targetUserId = StringUtils.trimToNull(query.getTargetUserId());
        adminDataScopeService.validateCurrentUserCanViewTargetUser(targetUserId);

        AdminOrderScope scope = resolveCurrentScope();
        if (scope.isEmptyTeamScope()) {
            return List.of();
        }

        return mtAppOrderMapper.selectAdminPage(
                StringUtils.trimToNull(query.getOrderId()),
                StringUtils.trimToNull(query.getOrderViewId()),
                StringUtils.trimToNull(query.getPoiId()),
                StringUtils.trimToNull(query.getShopName()),
                query.getPayStatus(),
                StringUtils.trimToNull(query.getRightStatus()),
                StringUtils.trimToNull(query.getVersion()),
                StringUtils.trimToNull(query.getVersionName()),
                targetUserId,
                toEpochSecond(query.getOrderStartTime()),
                toEpochSecond(query.getOrderEndTime()),
                scope.scopeTypeName(),
                scope.currentUserId(),
                scope.visibleUserIds()
        );
    }

    private SettlementQuery prepareSettlementQuery(AdminMtAppOrderSettlementQueryParam param) {
        AdminMtAppOrderSettlementQueryParam query = param == null ? new AdminMtAppOrderSettlementQueryParam() : param;
        String statsMonth = StringUtils.trimToNull(query.getStatsMonth());
        if (statsMonth == null) {
            throw new BusinessException("统计月份不能为空");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(statsMonth);
        } catch (DateTimeParseException e) {
            throw new BusinessException("统计月份格式应为yyyy-MM");
        }
        query.setPoiId(StringUtils.trimToNull(query.getPoiId()));
        return new SettlementQuery(query, month);
    }

    private List<AdminMtAppOrderSettlementRowVO> selectSettlementRows(SettlementQuery query) {
        AdminOrderScope scope = resolveCurrentScope();
        if (scope.isEmptyTeamScope()) {
            return List.of();
        }

        YearMonth statsMonth = query.statsMonth();
        LocalDate previousMonthStart = statsMonth.minusMonths(1).atDay(1);
        LocalDate currentMonthEnd = statsMonth.atEndOfMonth();
        List<AdminMtAppOrderSettlementRowVO> rows = mtAppOrderMapper.selectSettlementCandidates(
                StringUtils.trimToNull(query.param().getPoiId()),
                MT_APP_ORDER_PAID_STATUS,
                toEpochSecond(previousMonthStart.atStartOfDay()),
                toEpochSecond(currentMonthEnd.plusDays(1).atStartOfDay().minusSeconds(1)),
                scope.scopeTypeName(),
                scope.currentUserId(),
                scope.visibleUserIds()
        );
        rows.forEach(row -> fillSettlementFields(row, statsMonth));
        return rows;
    }

    private void fillSettlementFields(AdminMtAppOrderSettlementRowVO row, YearMonth statsMonth) {
        row.setPaidSpec(resolvePaidSpec(row));
        row.setPaidAmount(toYuan(row.getPaidAmountFen()));

        if (!hasValidSettlementBasis(row)) {
            row.setCurrentMonthUsedDays(0);
            row.setPreviousMonthUsedDays(0);
            row.setRemainingUnusedDays(0);
            row.setMeituanDeductionAmount(zeroMoney());
            row.setPreviousMonthActualIncome(zeroMoney());
            row.setCurrentMonthActualIncome(zeroMoney());
            row.setRemainingUnsettledActualIncome(zeroMoney());
            row.setActualIncome(zeroMoney());
            return;
        }

        LocalDate serviceStart = row.getStartTime().toLocalDate();
        LocalDate serviceEnd = row.getEndTime().toLocalDate();
        int currentMonthUsedDays = overlapDays(serviceStart, serviceEnd, statsMonth.atDay(1), statsMonth.atEndOfMonth());
        YearMonth previousMonth = statsMonth.minusMonths(1);
        int previousMonthUsedDays = overlapDays(serviceStart, serviceEnd, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        int coveredDays = overlapDays(serviceStart, serviceEnd, serviceStart, statsMonth.atEndOfMonth());
        int remainingUnusedDays = Math.max(row.getDays() - coveredDays, 0);

        BigDecimal dailyAmount = row.getPaidAmount()
                .divide(BigDecimal.valueOf(row.getDays()), 10, RoundingMode.HALF_UP);
        BigDecimal meituanDeductionAmount = calculateDeductionAmount(dailyAmount, currentMonthUsedDays);
        BigDecimal previousMonthActualIncome = calculateNetIncome(dailyAmount, previousMonthUsedDays);
        BigDecimal currentMonthActualIncome = calculateNetIncome(dailyAmount, currentMonthUsedDays);
        BigDecimal remainingUnsettledActualIncome = calculateNetIncome(dailyAmount, remainingUnusedDays);

        row.setCurrentMonthUsedDays(currentMonthUsedDays);
        row.setPreviousMonthUsedDays(previousMonthUsedDays);
        row.setRemainingUnusedDays(remainingUnusedDays);
        row.setMeituanDeductionAmount(meituanDeductionAmount);
        row.setPreviousMonthActualIncome(previousMonthActualIncome);
        row.setCurrentMonthActualIncome(currentMonthActualIncome);
        row.setRemainingUnsettledActualIncome(remainingUnsettledActualIncome);
        row.setActualIncome(currentMonthActualIncome);
    }

    private BigDecimal calculateDeductionAmount(BigDecimal dailyAmount, int days) {
        return dailyAmount
                .multiply(BigDecimal.valueOf(days))
                .multiply(MEITUAN_DEDUCTION_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateNetIncome(BigDecimal dailyAmount, int days) {
        return dailyAmount
                .multiply(BigDecimal.valueOf(days))
                .multiply(NET_RATE)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasValidSettlementBasis(AdminMtAppOrderSettlementRowVO row) {
        return row.getPaidAmount() != null
                && row.getPaidAmountFen() != null
                && row.getPaidAmountFen() >= 0
                && row.getDays() != null
                && row.getDays() > 0
                && row.getStartTime() != null
                && row.getEndTime() != null
                && !row.getEndTime().isBefore(row.getStartTime());
    }

    private int overlapDays(LocalDate serviceStart, LocalDate serviceEnd, LocalDate rangeStart, LocalDate rangeEnd) {
        LocalDate start = serviceStart.isAfter(rangeStart) ? serviceStart : rangeStart;
        LocalDate end = serviceEnd.isBefore(rangeEnd) ? serviceEnd : rangeEnd;
        if (end.isBefore(start)) {
            return 0;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);
    }

    private String resolvePaidSpec(AdminMtAppOrderSettlementRowVO row) {
        String versionText = StringUtils.firstNonBlank(row.getVersionName(), row.getVersion(), "-");
        String daysText = row.getDays() == null ? "天数-" : row.getDays() + "天";
        return versionText + " / " + daysText;
    }

    private BigDecimal toYuan(Integer fen) {
        if (fen == null) {
            return zeroMoney();
        }
        return BigDecimal.valueOf(fen).divide(YUAN_DIVISOR, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return "¥" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private AdminOrderScope resolveCurrentScope() {
        AdminDataScopeTypeEnum scopeType = adminDataScopeService.getCurrentScopeType();
        String currentUserId = null;
        List<String> visibleUserIds = List.of();
        if (scopeType == AdminDataScopeTypeEnum.SELF) {
            currentUserId = requireCurrentUserId();
        } else if (scopeType == AdminDataScopeTypeEnum.TEAM) {
            visibleUserIds = new ArrayList<>(adminDataScopeService.getCurrentVisibleUserIds());
        }
        return new AdminOrderScope(
                scopeType == null ? null : scopeType.name(),
                currentUserId,
                visibleUserIds
        );
    }

    private Long toEpochSecond(java.time.LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(DEFAULT_ZONE_ID).toEpochSecond();
    }

    private String requireCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException(401, "当前用户未登录");
        }
        return userId;
    }

    private record AdminOrderScope(String scopeTypeName, String currentUserId, List<String> visibleUserIds) {

        private boolean isEmptyTeamScope() {
            return AdminDataScopeTypeEnum.TEAM.name().equals(scopeTypeName)
                    && (visibleUserIds == null || visibleUserIds.isEmpty());
        }
    }

    private record SettlementQuery(AdminMtAppOrderSettlementQueryParam param, YearMonth statsMonth) {
    }
}
