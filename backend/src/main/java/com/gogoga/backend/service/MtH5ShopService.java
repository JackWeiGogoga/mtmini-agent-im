package com.gogoga.backend.service;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.MtH5HomeVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 美团 H5 门店服务
 *
 * @author system
 * @since 2026-03-22
 */
@Service
@RequiredArgsConstructor
public class MtH5ShopService {

    private final MtShopService mtShopService;

    /**
     * 获取 H5 首页门店信息
     *
     * @return 首页门店信息
     */
    public MtH5HomeVO getHomeInfo() {
        MtShop shop = getCurrentShop();

        MtH5HomeVO homeInfo = new MtH5HomeVO();
        homeInfo.setShopName(firstNonBlank(shop.getPoiName(), shop.getName(), "美团门店"));
        homeInfo.setPoiId(shop.getPoiId());
        homeInfo.setEpoiId(shop.getEpoiId());
        return homeInfo;
    }

    /**
     * 获取当前已登录门店
     *
     * @return 当前门店
     */
    public MtShop getCurrentShop() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401, "请先登录门店账号");
        }

        String epoiId = StringUtils.trimToNull(loginUser.getEpoiId());
        if (epoiId == null) {
            throw new BusinessException(403, "当前门店登录态无效");
        }

        MtShop shop = mtShopService.findByEpoiId(epoiId);
        if (shop == null) {
            throw new BusinessException(404, "门店不存在或已被移除");
        }
        return shop;
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
