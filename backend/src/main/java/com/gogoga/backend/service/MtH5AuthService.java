package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.param.MtH5DirectLoginParam;
import com.gogoga.backend.vo.MtH5LoginVO;
import com.meituan.sdk.internal.utils.SignerUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 美团 H5 门店登录服务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtH5AuthService {

    private final MtProperties mtProperties;
    private final MtShopService mtShopService;
    private final LoginService loginService;

    /**
     * 验证 /api/callbacks/meituan/auth 回调签名
     *
     * @param code        授权码
     * @param developerId 开发者 ID
     * @param businessId  业务 ID
     * @param sign        签名
     * @return true 验签通过
     */
    public boolean verifyAuthNotifySign(String code, String developerId, String businessId, String sign) {
        if (StringUtils.isAnyBlank(code, developerId, businessId, sign)) {
            return false;
        }
        if (!StringUtils.equals(developerId, String.valueOf(mtProperties.getDeveloperId()))) {
            return false;
        }
        if (!StringUtils.equals(businessId, String.valueOf(mtProperties.getBusinessId()))) {
            return false;
        }

        Map<String, String> params = new HashMap<>();
        params.put("code", code);
        params.put("developerId", developerId);
        params.put("businessId", businessId);
        return verifySign(params, sign);
    }

    /**
     * 美团 H5 直登
     *
     * @param param 直登参数
     * @return H5 登录结果
     */
    public MtH5LoginVO login(MtH5DirectLoginParam param) {
        validateAppKey(param.getAppKey());

        String wmPoiId = StringUtils.trimToNull(param.getWmPoiId());
        if (wmPoiId == null) {
            throw new BusinessException(400, "wmPoiId不能为空");
        }

        MtShop shop = mtShopService.findByEpoiId(wmPoiId);
        if (shop == null) {
            throw new BusinessException(404, "门店尚未完成授权，请先在美团商家中心完成授权");
        }

        LoginUser loginUser = loginService.loginMtShop(shop);
        log.info("美团 H5 直登成功，wmPoiId={}, appKey={}, platform={}, bizTokenPresent={}",
                wmPoiId, param.getAppKey(), param.getPlatform(), StringUtils.isNotBlank(param.getBizToken()));
        return buildLoginPayload(shop, loginUser, param.getAppKey(), param.getPlatform());
    }

    private void validateAppKey(String appKey) {
        String configuredAppKey = mtProperties.getH5().getAppKey();
        if (StringUtils.isNotBlank(configuredAppKey) && StringUtils.isNotBlank(appKey)
                && !StringUtils.equals(configuredAppKey, appKey)) {
            throw new BusinessException(400, "应用标识不匹配");
        }
    }

    private MtH5LoginVO buildLoginPayload(MtShop shop, LoginUser loginUser, String appKey, String platform) {
        String fallbackShopCode = firstNonBlank(shop.getEpoiId(), shop.getPoiId(), loginUser.getEpoiId());
        String shopName = firstNonBlank(shop.getPoiName(), shop.getName(), loginUser.getUserName(), fallbackShopCode);

        MtH5LoginVO.MtH5ShopInfoVO shopInfo = new MtH5LoginVO.MtH5ShopInfoVO();
        shopInfo.setNickName(shopName);
        shopInfo.setUserName(shopName);
        shopInfo.setPoiId(shop.getPoiId());
        shopInfo.setEpoiId(shop.getEpoiId());
        shopInfo.setPlatform(platform);
        shopInfo.setAppKey(appKey);
        shopInfo.setVersion(0);

        MtH5LoginVO payload = new MtH5LoginVO();
        payload.setToken(loginUser.getToken());
        payload.setVersion(0);
        payload.setShopInfo(shopInfo);
        return payload;
    }

    private boolean verifySign(Map<String, String> params, String sign) {
        try {
            String expected = SignerUtil.getSign(mtProperties.getSignKey(), params);
            return StringUtils.equals(expected, sign);
        } catch (Exception e) {
            log.error("校验美团签名失败，params={}", params, e);
            return false;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
