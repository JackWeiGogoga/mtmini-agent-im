package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.service.AdminPowerManagementService;
import com.gogoga.backend.vo.AdminPowerAccountSummaryVO;
import com.gogoga.backend.vo.AdminPowerConsumeRecordVO;
import com.gogoga.backend.vo.AdminPowerGrantVO;
import com.gogoga.backend.vo.AdminPowerManagementRowVO;
import com.gogoga.backend.vo.AdminPowerRechargeRecordVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台算力管理接口
 */
@RestController
@RequestMapping("/api/system/power-management")
@RequiredArgsConstructor
@RequiresLogin
public class AdminPowerManagementController {

    private final AdminPowerManagementService adminPowerManagementService;

    @GetMapping("/accounts")
    @RequiresPermission("shop:power-management:list")
    public ResultVO<PageInfo<AdminPowerManagementRowVO>> listRows(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId,
            @RequestParam(required = false) String bindingDomain,
            @RequestParam(required = false) String bindingStatus,
            @RequestParam(required = false) String riskStatus,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(adminPowerManagementService.listRows(
                keyword,
                targetUserId,
                bindingDomain,
                bindingStatus,
                riskStatus,
                sortField,
                sortOrder,
                pageNum,
                pageSize
        ));
    }

    @GetMapping("/summary")
    @RequiresPermission("shop:power-management:list")
    public ResultVO<AdminPowerAccountSummaryVO> getSummary(@RequestParam Long shopId) {
        return ResultVO.success(adminPowerManagementService.getSummary(shopId));
    }

    @GetMapping("/recharges")
    @RequiresPermission("shop:power-management:list")
    public ResultVO<PageInfo<AdminPowerRechargeRecordVO>> listRechargeRecords(
            @RequestParam Long shopId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(adminPowerManagementService.listRechargeRecords(shopId, startTime, endTime, pageNum, pageSize));
    }

    @GetMapping("/grants")
    @RequiresPermission("shop:power-management:list")
    public ResultVO<PageInfo<AdminPowerGrantVO>> listGrantRecords(
            @RequestParam Long shopId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(adminPowerManagementService.listGrantRecords(shopId, startTime, endTime, pageNum, pageSize));
    }

    @GetMapping("/consumes")
    @RequiresPermission("shop:power-management:list")
    public ResultVO<PageInfo<AdminPowerConsumeRecordVO>> listConsumeRecords(
            @RequestParam Long shopId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(adminPowerManagementService.listConsumeRecords(shopId, startTime, endTime, pageNum, pageSize));
    }
}
