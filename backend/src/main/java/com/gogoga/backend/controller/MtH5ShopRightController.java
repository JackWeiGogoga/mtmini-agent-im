package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.service.MtShopRightService;
import com.gogoga.backend.vo.MtH5ShopRightVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * H5 门店权益接口
 *
 * @author system
 * @since 2026-03-25
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/h5/rights")
@RequiresLogin
public class MtH5ShopRightController {

    private final MtShopRightService mtShopRightService;

    /**
     * 查询当前门店权益
     *
     * @return 权益列表
     */
    @GetMapping("/current")
    public ResultVO<List<MtH5ShopRightVO>> getCurrentRights() {
        return ResultVO.success(mtShopRightService.getCurrentRights());
    }
}
