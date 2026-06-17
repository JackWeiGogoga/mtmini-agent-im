package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.param.MtH5PowerRechargeCreateParam;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.MtPowerRechargeService;
import com.gogoga.backend.util.IPUtil;
import com.gogoga.backend.vo.MtH5PowerRechargeCreateVO;
import com.gogoga.backend.vo.MtH5PowerRechargeResultVO;
import com.gogoga.backend.vo.MtH5PowerSummaryVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * H5 算力接口
 *
 * @author system
 * @since 2026-03-23
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/h5/power")
public class MtH5PowerController {

    private final MtH5ShopService mtH5ShopService;
    private final MtPowerRechargeService mtPowerRechargeService;

    /**
     * 获取算力摘要
     *
     * @return 算力摘要
     */
    @GetMapping("/summary")
    @RequiresLogin
    public ResultVO<MtH5PowerSummaryVO> getSummary(@RequestParam(required = false) String redemptionCode) {
        return ResultVO.success(mtPowerRechargeService.getSummary(mtH5ShopService.getCurrentShop(), redemptionCode));
    }

    /**
     * 创建充值单
     *
     * @param param 请求参数
     * @param request HTTP 请求
     * @return 创建结果
     */
    @PostMapping("/recharges")
    @RequiresLogin
    public ResultVO<MtH5PowerRechargeCreateVO> createRecharge(
            @Valid @RequestBody MtH5PowerRechargeCreateParam param,
            HttpServletRequest request) {

        return ResultVO.success(mtPowerRechargeService.createRecharge(
                mtH5ShopService.getCurrentShop(),
                param.getPowerAmount(),
                param.getRedemptionCode(),
                IPUtil.getRealIp(request)
        ));
    }

    /**
     * 查询充值结果
     *
     * @param rechargeNo 充值单号
     * @return 充值结果
     */
    @GetMapping("/recharges/{rechargeNo}")
    @RequiresLogin
    public ResultVO<MtH5PowerRechargeResultVO> getRechargeResult(@PathVariable String rechargeNo) {
        return ResultVO.success(mtPowerRechargeService.getRechargeResult(mtH5ShopService.getCurrentShop().getId(), rechargeNo));
    }

    /**
     * mock 支付成功
     *
     * @param rechargeNo 充值单号
     * @return 充值结果
     */
    @PostMapping("/recharges/{rechargeNo}/mock-success")
    @RequiresLogin
    public ResultVO<MtH5PowerRechargeResultVO> mockSuccess(@PathVariable String rechargeNo) {
        return ResultVO.success(mtPowerRechargeService.mockSuccess(mtH5ShopService.getCurrentShop().getId(), rechargeNo));
    }
}
