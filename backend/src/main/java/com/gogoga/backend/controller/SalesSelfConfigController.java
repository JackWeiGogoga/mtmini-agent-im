package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.service.SalesRedemptionCodeService;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.SalesSelfConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/system/sales/self-config")
@RequiresLogin
public class SalesSelfConfigController {

    private final SalesRedemptionCodeService salesRedemptionCodeService;

    @GetMapping
    @RequiresPermission("sales:self-config:view")
    public ResultVO<SalesSelfConfigVO> getConfig() {
        return ResultVO.success(salesRedemptionCodeService.getCurrentSalesConfig());
    }

    @PostMapping(value = "/receipt-code", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("sales:self-config:upload-receipt")
    public ResultVO<SalesSelfConfigVO> uploadReceiptCode(@RequestParam("file") MultipartFile file) {
        return ResultVO.success(salesRedemptionCodeService.uploadReceiptCode(file));
    }
}
