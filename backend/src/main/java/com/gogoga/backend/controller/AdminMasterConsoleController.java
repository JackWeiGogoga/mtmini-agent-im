package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.param.MtH5ManualPromotionBidParam;
import com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam;
import com.gogoga.backend.service.AdminMasterConsoleService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminAsyncTaskMetricsVO;
import com.gogoga.backend.vo.AdminMasterConsolePromotionLogSummaryVO;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.MtH5ManualPromotionBidVO;
import com.gogoga.backend.vo.MtH5PromotionLogVO;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台总控台接口
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/master-console")
@RequiresLogin
public class AdminMasterConsoleController {

    private final AdminMasterConsoleService adminMasterConsoleService;

    @GetMapping("/shops")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list,youzan:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<AdminMasterConsoleRowVO>> listRows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(required = false) String bindingStatus,
            @RequestParam(required = false) String promotionStatus,
            @RequestParam(defaultValue = "LAST_7_DAYS") String statsRange,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResultVO.success(adminMasterConsoleService.listRows(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                promotionStatus,
                DashboardStatsRangeTypeEnum.fromValue(statsRange),
                sortField,
                sortOrder,
                pageNum,
                pageSize
        ));
    }

    @GetMapping("/shops/export")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list,youzan:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResponseEntity<byte[]> exportRows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(required = false) String bindingStatus,
            @RequestParam(required = false) String promotionStatus,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return ExcelExportUtil.xlsxResponse("总控台.xlsx", adminMasterConsoleService.exportXlsx(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                promotionStatus,
                sortField,
                sortOrder
        ));
    }

    @GetMapping("/visible-users")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list,youzan:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<List<VisibleUserOptionVO>> listVisibleUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bindingDomain) {
        return ResultVO.success(adminMasterConsoleService.listVisibleUsers(keyword, bindingDomain));
    }

    @GetMapping("/promotion-settings")
    @RequiresPermission(value = "shop:master-console:config,sales:master-console:view,youzan:master-console:config", logical = RequiresPermission.Logical.OR)
    public ResultVO<MtH5PromotionSettingsVO> getPromotionSettings(
            @RequestParam Long shopId,
            @RequestParam(required = false) String bindingDomain) {
        return ResultVO.success(adminMasterConsoleService.getPromotionSettings(shopId, bindingDomain));
    }

    @PutMapping("/promotion-settings")
    @RequiresPermission(value = "shop:master-console:config,youzan:master-console:config", logical = RequiresPermission.Logical.OR)
    public ResultVO<MtH5PromotionSettingsVO> updatePromotionSettings(
            @RequestParam Long shopId,
            @RequestParam(required = false) String bindingDomain,
            @Valid @RequestBody MtH5PromotionSettingsUpdateParam param) {
        return ResultVO.success(adminMasterConsoleService.updatePromotionSettings(shopId, bindingDomain, param));
    }

    @PostMapping("/promotion-settings/manual-bid")
    @RequiresPermission(value = "shop:master-console:config,youzan:master-console:config", logical = RequiresPermission.Logical.OR)
    public ResultVO<MtH5ManualPromotionBidVO> updateManualPromotionBid(
            @RequestParam Long shopId,
            @RequestParam(required = false) String bindingDomain,
            @Valid @RequestBody MtH5ManualPromotionBidParam param) {
        return ResultVO.success(adminMasterConsoleService.updateManualPromotionBid(shopId, bindingDomain, param));
    }

    @GetMapping("/promotion-logs")
    @RequiresPermission(value = "shop:master-console:log,sales:master-console:log,youzan:master-console:log", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<MtH5PromotionLogVO>> listPromotionLogs(
            @RequestParam Long shopId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ResultVO.success(adminMasterConsoleService.listPromotionLogs(shopId, bindingDomain, pageNum, pageSize));
    }

    @GetMapping("/promotion-log-summary")
    @RequiresPermission(value = "shop:master-console:log,sales:master-console:log,youzan:master-console:log", logical = RequiresPermission.Logical.OR)
    public ResultVO<AdminMasterConsolePromotionLogSummaryVO> getPromotionLogSummary(
            @RequestParam Long shopId,
            @RequestParam(required = false) String bindingDomain) {
        return ResultVO.success(adminMasterConsoleService.getPromotionLogSummary(shopId, bindingDomain));
    }

    @GetMapping("/async-task-metrics")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list,youzan:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<AdminAsyncTaskMetricsVO> getAsyncTaskMetrics() {
        return ResultVO.success(adminMasterConsoleService.getAsyncTaskMetrics());
    }
}
