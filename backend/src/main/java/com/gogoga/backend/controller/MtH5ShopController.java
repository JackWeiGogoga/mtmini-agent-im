package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.enums.DashboardStatsRangeTypeEnum;
import com.gogoga.backend.param.ShopContactPhoneUpdateParam;
import com.gogoga.backend.param.ImBroadcastSettingUpdateParam;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.service.MtH5GuideStateService;
import com.gogoga.backend.service.MtH5HomeDashboardService;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.MtImBroadcastSettingService;
import com.gogoga.backend.service.ShopOperationSettingsService;
import com.gogoga.backend.vo.ImBroadcastSettingVO;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import com.gogoga.backend.vo.MtH5GuideStateVO;
import com.gogoga.backend.vo.MtH5HomeDashboardVO;
import com.gogoga.backend.vo.MtH5HomeVO;
import com.gogoga.backend.vo.MtH5TabStatusVO;
import com.gogoga.backend.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 美团 H5 门店接口
 *
 * @author system
 * @since 2026-03-22
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/h5/shop")
@RequiresLogin
public class MtH5ShopController {

    private final MtH5HomeDashboardService mtH5HomeDashboardService;
    private final MtH5ShopService mtH5ShopService;
    private final MtH5GuideStateService mtH5GuideStateService;
    private final ShopOperationSettingsService shopOperationSettingsService;
    private final MtImBroadcastSettingService mtImBroadcastSettingService;

    /**
     * 获取 H5 首页门店信息
     *
     * @return 首页门店信息
     */
    @GetMapping("/home")
    public ResultVO<MtH5HomeVO> getHomeInfo() {
        return ResultVO.success(mtH5ShopService.getHomeInfo());
    }

    /**
     * 获取 H5 首页聚合看板
     *
     * @return 首页聚合看板
     */
    @GetMapping("/home/dashboard")
    public ResultVO<MtH5HomeDashboardVO> getHomeDashboard(
            @RequestParam(defaultValue = "LAST_7_DAYS") String statsRange) {
        return ResultVO.success(mtH5HomeDashboardService.getDashboard(DashboardStatsRangeTypeEnum.fromValue(statsRange)));
    }

    /**
     * 获取 H5 底部 tab 状态
     *
     * @return tab 状态
     */
    @GetMapping("/tab-status")
    public ResultVO<MtH5TabStatusVO> getTabStatus() {
        return ResultVO.success(mtH5HomeDashboardService.getTabStatus());
    }

    /**
     * 更新当前门店操作开关。
     *
     * @param param 开关参数
     * @return 更新后的开关
     */
    @PutMapping("/operation-settings")
    public ResultVO<ShopOperationSettingsVO> updateOperationSettings(
            @Valid @RequestBody ShopOperationSettingsUpdateParam param) {
        return ResultVO.success(shopOperationSettingsService.updateSettings(
                mtH5ShopService.getCurrentShop().getId(),
                param,
                ShopOperationSettingsService.CHANGE_SOURCE_H5
        ));
    }

    /**
     * 获取当前门店联系方式配置。
     *
     * @return 含自定义与实际生效联系方式的开关视图
     */
    @GetMapping("/contact-phone")
    public ResultVO<ShopOperationSettingsVO> getContactPhone() {
        return ResultVO.success(shopOperationSettingsService.buildSettings(
                mtH5ShopService.getCurrentShop().getId()));
    }

    /**
     * 更新当前门店自定义联系方式，留空表示清除并回退到门店电话。
     *
     * @param param 联系方式参数
     * @return 更新后的配置
     */
    @PutMapping("/contact-phone")
    public ResultVO<ShopOperationSettingsVO> updateContactPhone(
            @Valid @RequestBody ShopContactPhoneUpdateParam param) {
        return ResultVO.success(shopOperationSettingsService.updateCustomContactPhone(
                mtH5ShopService.getCurrentShop().getId(),
                param.getCustomContactPhone(),
                ShopOperationSettingsService.CHANGE_SOURCE_H5
        ));
    }

    /**
     * 获取当前门店的消息群发配置。
     *
     * @return 群发配置
     */
    @GetMapping("/broadcast-setting")
    public ResultVO<ImBroadcastSettingVO> getBroadcastSetting() {
        return ResultVO.success(mtImBroadcastSettingService.getSettings(
                mtH5ShopService.getCurrentShop().getId()));
    }

    /**
     * 保存当前门店的消息群发配置。
     *
     * @param param 群发配置参数
     * @return 保存后的群发配置
     */
    @PutMapping("/broadcast-setting")
    public ResultVO<ImBroadcastSettingVO> saveBroadcastSetting(
            @Valid @RequestBody ImBroadcastSettingUpdateParam param) {
        return ResultVO.success(mtImBroadcastSettingService.saveSettings(
                mtH5ShopService.getCurrentShop().getId(), param,
                MtImBroadcastSettingService.CHANGE_SOURCE_H5));
    }

    /**
     * 删除当前门店的定时群发配置。
     *
     * @return 清空后的群发配置
     */
    @DeleteMapping("/broadcast-setting")
    public ResultVO<ImBroadcastSettingVO> deleteBroadcastSetting() {
        return ResultVO.success(mtImBroadcastSettingService.deleteSettings(
                mtH5ShopService.getCurrentShop().getId(),
                MtImBroadcastSettingService.CHANGE_SOURCE_H5));
    }

    /**
     * 标记 H5 引导完成
     *
     * @param guideCode 引导编码
     * @return 引导状态
     */
    @PostMapping("/guides/{guideCode}/complete")
    public ResultVO<MtH5GuideStateVO> completeGuide(@PathVariable String guideCode) {
        return ResultVO.success(mtH5GuideStateService.completeCurrentGuide(guideCode));
    }
}
