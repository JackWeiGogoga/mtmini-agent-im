package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminMtAppOrderManualPullParam;
import com.gogoga.backend.param.AdminMtAppOrderQueryParam;
import com.gogoga.backend.param.AdminMtAppOrderSettlementQueryParam;
import com.gogoga.backend.service.AdminMtAppOrderManualPullService;
import com.gogoga.backend.service.AdminMtAppOrderService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminMtAppOrderManualPullVO;
import com.gogoga.backend.vo.AdminMtAppOrderRowVO;
import com.gogoga.backend.vo.AdminMtAppOrderSettlementRowVO;
import com.gogoga.backend.vo.AdminMtAppOrderVersionOptionVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台美团订阅订单接口
 */
@RestController
@RequestMapping("/api/system/mt-app-orders")
@RequiredArgsConstructor
@RequiresLogin
public class AdminMtAppOrderController {

    private final AdminMtAppOrderService adminMtAppOrderService;
    private final AdminMtAppOrderManualPullService adminMtAppOrderManualPullService;

    @GetMapping
    @RequiresPermission("shop:mt-app-order:list")
    public ResultVO<PageInfo<AdminMtAppOrderRowVO>> listRows(AdminMtAppOrderQueryParam param) {
        return ResultVO.success(adminMtAppOrderService.listRows(param));
    }

    @GetMapping("/version-options")
    @RequiresPermission("shop:mt-app-order:list")
    public ResultVO<List<AdminMtAppOrderVersionOptionVO>> listVersionOptions() {
        return ResultVO.success(adminMtAppOrderService.listVersionOptions());
    }

    @PostMapping("/manual-pull")
    @RequiresPermission("shop:mt-app-order:manual-pull")
    public ResultVO<AdminMtAppOrderManualPullVO> manualPull(
            @Valid @RequestBody AdminMtAppOrderManualPullParam param) {
        return ResultVO.success(adminMtAppOrderManualPullService.pull(param));
    }

    @GetMapping("/export")
    @RequiresPermission("shop:mt-app-order:list")
    public ResponseEntity<byte[]> exportRows(AdminMtAppOrderQueryParam param) {
        return ExcelExportUtil.xlsxResponse("美团应用订单.xlsx", adminMtAppOrderService.exportXlsx(param));
    }

    @GetMapping("/settlement-stats")
    @RequiresPermission("shop:mt-app-order:list")
    public ResultVO<PageInfo<AdminMtAppOrderSettlementRowVO>> listSettlementRows(AdminMtAppOrderSettlementQueryParam param) {
        return ResultVO.success(adminMtAppOrderService.listSettlementRows(param));
    }

    @GetMapping("/settlement-stats/export")
    @RequiresPermission("shop:mt-app-order:list")
    public ResponseEntity<byte[]> exportSettlementRows(AdminMtAppOrderSettlementQueryParam param) {
        return ExcelExportUtil.xlsxResponse("美团订单结算统计.xlsx", adminMtAppOrderService.exportSettlementXlsx(param));
    }
}
