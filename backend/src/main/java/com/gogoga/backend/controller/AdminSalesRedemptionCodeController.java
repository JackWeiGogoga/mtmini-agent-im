package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminSalesRedemptionCodeGenerateParam;
import com.gogoga.backend.service.SalesRedemptionCodeService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminSalesRedemptionCodeRowVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/sales/redemption-codes")
@RequiresLogin
public class AdminSalesRedemptionCodeController {

    private final SalesRedemptionCodeService salesRedemptionCodeService;

    @GetMapping
    @RequiresPermission("sales:redemption-code:list")
    public ResultVO<PageInfo<AdminSalesRedemptionCodeRowVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return ResultVO.success(salesRedemptionCodeService.listAdminRows(keyword, pageNum, pageSize));
    }

    @GetMapping("/export")
    @RequiresPermission("sales:redemption-code:list")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword) {
        return ExcelExportUtil.xlsxResponse("销售兑换码.xlsx", salesRedemptionCodeService.exportAdminXlsx(keyword));
    }

    @PostMapping
    @RequiresPermission("sales:redemption-code:create")
    public ResultVO<AdminSalesRedemptionCodeRowVO> generate(@Valid @RequestBody AdminSalesRedemptionCodeGenerateParam param) {
        return ResultVO.success(salesRedemptionCodeService.generateByPhone(param.getPhone()));
    }

    @DeleteMapping("/{id}/receipt-code")
    @RequiresPermission("sales:redemption-code:receipt-clear")
    public ResultVO<Void> clearReceiptCode(@PathVariable Long id) {
        salesRedemptionCodeService.clearReceiptCode(id);
        return ResultVO.success();
    }
}
