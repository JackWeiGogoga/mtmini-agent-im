package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.service.AdminPowerRechargeOrderService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminPowerRechargeOrderDetailVO;
import com.gogoga.backend.vo.AdminPowerRechargeOrderRowVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/power-recharge-orders")
@RequiresLogin
public class AdminPowerRechargeOrderController {

    private final AdminPowerRechargeOrderService adminPowerRechargeOrderService;

    @GetMapping
    @RequiresPermission("system:power-recharge-order:list")
    public ResultVO<PageInfo<AdminPowerRechargeOrderRowVO>> listOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer payStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(adminPowerRechargeOrderService.listOrders(
                keyword,
                payStatus,
                startTime,
                endTime,
                pageNum,
                pageSize
        ));
    }

    @GetMapping("/export")
    @RequiresPermission("system:power-recharge-order:list")
    public ResponseEntity<byte[]> exportOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer payStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime
    ) {
        return ExcelExportUtil.xlsxResponse("算力充值订单.xlsx", adminPowerRechargeOrderService.exportXlsx(
                keyword,
                payStatus,
                startTime,
                endTime
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:power-recharge-order:list")
    public ResultVO<AdminPowerRechargeOrderDetailVO> getOrderDetail(@PathVariable Long id) {
        return ResultVO.success(adminPowerRechargeOrderService.getOrderDetail(id));
    }
}
