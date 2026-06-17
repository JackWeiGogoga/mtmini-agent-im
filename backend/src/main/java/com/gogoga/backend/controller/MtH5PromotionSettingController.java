package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.param.MtH5ManualPromotionBidParam;
import com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.MtPromotionAutoBidService;
import com.gogoga.backend.service.MtPromotionSettingService;
import com.gogoga.backend.vo.MtH5ManualPromotionBidVO;
import com.gogoga.backend.vo.MtH5PromotionSettingsVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * H5 推广配置接口
 *
 * @author system
 * @since 2026-03-24
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/h5/promotion/settings")
@RequiresLogin
@RequiresShopRight(MtShopRightCodeConstant.H5_PROMOTION)
public class MtH5PromotionSettingController {

    private final MtPromotionSettingService mtPromotionSettingService;
    private final MtPromotionAutoBidService mtPromotionAutoBidService;
    private final MtH5ShopService mtH5ShopService;

    /**
     * 获取当前门店推广配置
     *
     * @return 推广配置
     */
    @GetMapping
    public ResultVO<MtH5PromotionSettingsVO> getCurrentSettings() {
        return ResultVO.success(mtPromotionSettingService.getCurrentSettings());
    }

    /**
     * 更新当前门店推广配置
     *
     * @param param 更新参数
     * @return 推广配置
     */
    @PutMapping
    public ResultVO<MtH5PromotionSettingsVO> updateCurrentSettings(
            @Valid @RequestBody MtH5PromotionSettingsUpdateParam param) {

        MtShop shop = mtH5ShopService.getCurrentShop();
        MtH5PromotionSettingsVO settings = mtPromotionSettingService.updateCurrentSettings(param);
        mtPromotionAutoBidService.resetBidToFloorIfOutOfPromotionWindowAfterSettingsSave(shop);
        return ResultVO.success(settings);
    }

    /**
     * 手动识别高峰期并尝试解锁 AI 自动管理时段
     *
     * @return 最新推广配置
     */
    @PostMapping("/auto-time-mode/unlock")
    public ResultVO<MtH5PromotionSettingsVO> unlockAutoTimeMode() {
        return ResultVO.success(mtPromotionSettingService.unlockCurrentAutoTimeMode());
    }

    /**
     * 手动修改当前推广窗口出价
     *
     * @param param 手动出价参数
     * @return 生效结果
     */
    @PostMapping("/manual-bid")
    public ResultVO<MtH5ManualPromotionBidVO> updateManualBid(
            @Valid @RequestBody MtH5ManualPromotionBidParam param) {

        return ResultVO.success(mtPromotionAutoBidService.updateCurrentWindowBidManually(
                mtH5ShopService.getCurrentShop(),
                param.getBidAmount()
        ));
    }
}
