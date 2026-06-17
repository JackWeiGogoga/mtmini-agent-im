package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminPricingAnalysisQueryParam;
import com.gogoga.backend.service.AdminPricingAnalysisService;
import com.gogoga.backend.vo.AdminPricingAnalysisDetailVO;
import com.gogoga.backend.vo.AdminPricingAnalysisRowVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台定价分析接口。
 */
@RestController
@RequestMapping("/api/system/pricing-analysis")
@RequiredArgsConstructor
@RequiresLogin
public class AdminPricingAnalysisController {

    private final AdminPricingAnalysisService adminPricingAnalysisService;

    @GetMapping
    @RequiresPermission("system:pricing-analysis:list")
    public ResultVO<PageInfo<AdminPricingAnalysisRowVO>> listRows(AdminPricingAnalysisQueryParam param) {
        return ResultVO.success(adminPricingAnalysisService.listRows(param));
    }

    @GetMapping("/{recordId}")
    @RequiresPermission("system:pricing-analysis:list")
    public ResultVO<AdminPricingAnalysisDetailVO> getDetail(@PathVariable Long recordId) {
        return ResultVO.success(adminPricingAnalysisService.getDetail(recordId));
    }
}
