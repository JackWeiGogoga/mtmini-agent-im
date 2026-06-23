package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.service.AdminMasterConsoleService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminMasterConsoleRowVO;
import com.gogoga.backend.vo.MtH5CommentReplyLogVO;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/comment-reply-logs")
    @RequiresPermission(value = "shop:master-console:list,sales:master-console:list", logical = RequiresPermission.Logical.OR)
    public ResultVO<PageInfo<MtH5CommentReplyLogVO>> listCommentReplyLogs(
            @RequestParam Long shopId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ResultVO.success(adminMasterConsoleService.listCommentReplyLogs(
                shopId,
                bindingDomain,
                pageNum,
                pageSize
        ));
    }
}
