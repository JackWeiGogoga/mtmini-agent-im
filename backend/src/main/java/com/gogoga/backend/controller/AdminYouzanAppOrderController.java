package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminYouzanAppOrderQueryParam;
import com.gogoga.backend.param.AdminYouzanOrderRedeemParam;
import com.gogoga.backend.service.AdminYouzanAppOrderService;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.vo.AdminYouzanAppOrderDetailVO;
import com.gogoga.backend.vo.AdminYouzanAppOrderRowVO;
import com.gogoga.backend.vo.AdminYouzanOrderRedeemVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台有赞订购订单接口
 */
@RestController
@RequestMapping("/api/system/youzan-app-orders")
@RequiredArgsConstructor
@RequiresLogin
public class AdminYouzanAppOrderController {

    private final AdminYouzanAppOrderService adminYouzanAppOrderService;

    @GetMapping
    @RequiresPermission("youzan:app-order:list")
    public ResultVO<PageInfo<AdminYouzanAppOrderRowVO>> listRows(AdminYouzanAppOrderQueryParam param) {
        return ResultVO.success(adminYouzanAppOrderService.listRows(param));
    }

    @GetMapping("/export")
    @RequiresPermission("youzan:app-order:list")
    public ResponseEntity<byte[]> exportRows(AdminYouzanAppOrderQueryParam param) {
        return ExcelExportUtil.xlsxResponse("有赞应用订单.xlsx", adminYouzanAppOrderService.exportXlsx(param));
    }

    @GetMapping("/{id}")
    @RequiresPermission("youzan:app-order:list")
    public ResultVO<AdminYouzanAppOrderDetailVO> detail(@PathVariable Long id) {
        return ResultVO.success(adminYouzanAppOrderService.getDetail(id));
    }

    @PostMapping("/redeem")
    @RequiresPermission("youzan:app-order:redeem")
    public ResultVO<AdminYouzanOrderRedeemVO> redeem(@Valid @RequestBody AdminYouzanOrderRedeemParam param) {
        return ResultVO.success(adminYouzanAppOrderService.redeem(
                param.getOrderId(),
                param.getMtShopId(),
                param.getRemark()
        ));
    }
}
