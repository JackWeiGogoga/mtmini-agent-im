package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.SalesCommissionRatioUpdateParam;
import com.gogoga.backend.param.SalesCommissionSettlementUpdateParam;
import com.gogoga.backend.service.SalesCommissionService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.SalesCommissionRowVO;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/sales/commissions")
@RequiresLogin
public class SalesCommissionController {

    private final SalesCommissionService salesCommissionService;

    @GetMapping
    @RequiresPermission("sales:commission:list")
    public ResultVO<PageInfo<SalesCommissionRowVO>> list(
            @RequestParam(required = false) String shopKeyword,
            @RequestParam(required = false) String redemptionCode,
            @RequestParam(required = false) String rechargeNo,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(salesCommissionService.listRows(shopKeyword, redemptionCode, rechargeNo, pageNum, pageSize));
    }

    @GetMapping("/export")
    @RequiresPermission("sales:commission:list")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String shopKeyword,
            @RequestParam(required = false) String redemptionCode,
            @RequestParam(required = false) String rechargeNo
    ) {
        return ExcelExportUtil.xlsxResponse("销售分佣.xlsx", salesCommissionService.exportXlsx(
                shopKeyword,
                redemptionCode,
                rechargeNo
        ));
    }

    @PutMapping("/{id}/ratio")
    @RequiresPermission("sales:commission:ratio-edit")
    public ResultVO<Void> updateRatio(@PathVariable Long id,
                                      @Valid @RequestBody SalesCommissionRatioUpdateParam param) {
        salesCommissionService.updateCommissionRatio(id, param.getCommissionRatio());
        return ResultVO.success();
    }

    @PutMapping("/{id}/settlement-status")
    @RequiresPermission("sales:commission:settle")
    public ResultVO<Void> updateSettlementStatus(@PathVariable Long id,
                                                 @Valid @RequestBody SalesCommissionSettlementUpdateParam param) {
        salesCommissionService.updateSettlementStatus(id, param.getSettlementStatus());
        return ResultVO.success();
    }
}
