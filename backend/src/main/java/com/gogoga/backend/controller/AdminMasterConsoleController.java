package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.param.ImBroadcastSettingUpdateParam;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.param.ImAutoReplyLogQueryParam;
import com.gogoga.backend.service.ImBroadcastQueryService;
import com.gogoga.backend.service.ImReplyLogQueryService;
import com.gogoga.backend.service.AdminMasterConsoleService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.ImBroadcastSettingHistoryVO;
import com.gogoga.backend.vo.ImBroadcastSettingVO;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import com.gogoga.backend.vo.ImAutoReplyLogVO;
import com.gogoga.backend.vo.ImConversationHistoryPageVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import java.util.List;

/**
 * 后台总控台接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/master-console")
@RequiresLogin
public class AdminMasterConsoleController {

    private final AdminMasterConsoleService adminMasterConsoleService;
    private final ImReplyLogQueryService imReplyLogQueryService;
    private final ImBroadcastQueryService imBroadcastQueryService;

    @GetMapping("/shops")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<AdminMasterConsoleRowVO>> listRows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(required = false) String bindingStatus,
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
                DashboardStatsRangeTypeEnum.fromValue(statsRange),
                sortField,
                sortOrder,
                pageNum,
                pageSize
        ));
    }

    @GetMapping("/shops/export")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResponseEntity<byte[]> exportRows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(required = false) String bindingStatus,
            @RequestParam(defaultValue = "LAST_7_DAYS") String statsRange,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return ExcelExportUtil.xlsxResponse("总控台.xlsx", adminMasterConsoleService.exportXlsx(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                sortField,
                sortOrder,
                DashboardStatsRangeTypeEnum.fromValue(statsRange)
        ));
    }

    @GetMapping("/visible-users")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<List<VisibleUserOptionVO>> listVisibleUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bindingDomain) {
        return ResultVO.success(adminMasterConsoleService.listVisibleUsers(keyword, bindingDomain));
    }

    @PutMapping("/shops/{shopId}/operation-settings")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<ShopOperationSettingsVO> updateOperationSettings(
            @PathVariable Long shopId,
            @Valid @RequestBody ShopOperationSettingsUpdateParam param) {
        return ResultVO.success(adminMasterConsoleService.updateOperationSettings(shopId, param));
    }

    /**
     * 查看门店定时群发配置。
     */
    @GetMapping("/shops/{shopId}/broadcast-setting")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<ImBroadcastSettingVO> getBroadcastSetting(@PathVariable Long shopId) {
        return ResultVO.success(adminMasterConsoleService.getBroadcastSetting(shopId));
    }

    /**
     * 代商家保存定时群发配置，与 H5 侧共用同一套校验（敏感词、字数、频率）。
     */
    @PutMapping("/shops/{shopId}/broadcast-setting")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<ImBroadcastSettingVO> saveBroadcastSetting(
            @PathVariable Long shopId,
            @Valid @RequestBody ImBroadcastSettingUpdateParam param) {
        return ResultVO.success(adminMasterConsoleService.saveBroadcastSetting(shopId, param));
    }

    /**
     * 清空门店定时群发配置。
     */
    @DeleteMapping("/shops/{shopId}/broadcast-setting")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<ImBroadcastSettingVO> deleteBroadcastSetting(@PathVariable Long shopId) {
        return ResultVO.success(adminMasterConsoleService.deleteBroadcastSetting(shopId));
    }

    /**
     * 门店群发配置变更历史，含已被删除的版本。
     */
    @GetMapping("/shops/{shopId}/broadcast-setting/history")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<ImBroadcastSettingHistoryVO>> listBroadcastSettingHistory(
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        adminMasterConsoleService.requireVisibleShop(shopId);
        return ResultVO.success(imBroadcastQueryService.listSettingHistory(shopId, pageNum, pageSize));
    }

    @GetMapping("/shops/{shopId}/im-auto-replies")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<ImAutoReplyLogVO>> listImAutoReplies(
            @PathVariable Long shopId,
            ImAutoReplyLogQueryParam param) {
        adminMasterConsoleService.requireVisibleShop(shopId);
        return ResultVO.success(imReplyLogQueryService.listReplyLogs(param, shopId));
    }

    @GetMapping("/shops/{shopId}/im-conversations/{conversationId}/messages")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<ImConversationHistoryPageVO> getImConversationHistory(
            @PathVariable Long shopId,
            @PathVariable Long conversationId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime cursorTime,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "30") Integer pageSize) {
        adminMasterConsoleService.requireVisibleShop(shopId);
        return ResultVO.success(imReplyLogQueryService.history(
                conversationId, shopId, cursorTime, cursorId, pageSize));
    }

}
