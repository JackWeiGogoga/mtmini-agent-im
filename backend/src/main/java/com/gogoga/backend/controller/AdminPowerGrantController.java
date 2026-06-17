package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminPowerGrantCreateParam;
import com.gogoga.backend.service.AdminPowerGrantService;
import com.gogoga.backend.vo.AdminPowerGrantVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/shop-power-grants")
@RequiredArgsConstructor
@RequiresLogin
public class AdminPowerGrantController {

    private final AdminPowerGrantService adminPowerGrantService;

    @GetMapping
    @RequiresPermission("shop:power-grant:list")
    public ResultVO<List<AdminPowerGrantVO>> listByShopId(@RequestParam Long shopId) {
        return ResultVO.success(adminPowerGrantService.listByShopId(shopId));
    }

    @PostMapping
    @RequiresPermission("shop:power-grant:add")
    public ResultVO<AdminPowerGrantVO> createGrant(@Valid @RequestBody AdminPowerGrantCreateParam param) {
        return ResultVO.success(adminPowerGrantService.createGrant(
                param.getShopId(),
                param.getPowerAmount(),
                param.getGrantReason(),
                param.getRemark()
        ));
    }
}
