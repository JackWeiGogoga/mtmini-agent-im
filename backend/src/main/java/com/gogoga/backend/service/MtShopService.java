package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.meituan.sdk.auth.MeituanTokenData;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 美团店铺服务
 *
 * @author system
 * @since 2026-03-22
 */
@Service
public class MtShopService {

    private static final String AUTHORIZED_SHOP_GRANT_STATUS_SUCCESS = "SUCCESS";
    private static final String AUTHORIZED_SHOP_GRANT_REASON = "新商户授权赠送";
    private static final String AUTHORIZED_SHOP_GRANT_OPERATOR = "system";

    private final MtShopMapper mtShopMapper;
    private final MtPowerGrantMapper mtPowerGrantMapper;
    private final MtPowerAccountService mtPowerAccountService;
    private final PowerProperties powerProperties;

    public MtShopService(
            MtShopMapper mtShopMapper,
            MtPowerGrantMapper mtPowerGrantMapper,
            MtPowerAccountService mtPowerAccountService,
            PowerProperties powerProperties
    ) {
        this.mtShopMapper = mtShopMapper;
        this.mtPowerGrantMapper = mtPowerGrantMapper;
        this.mtPowerAccountService = mtPowerAccountService;
        this.powerProperties = powerProperties;
    }

    /**
     * 根据主键查询店铺
     *
     * @param id 店铺主键
     * @return 店铺实体
     */
    public MtShop findById(Long id) {
        return mtShopMapper.selectById(id);
    }

    /**
     * 根据 epoiId 查询店铺
     *
     * @param epoiId 店铺 epoiId
     * @return 店铺实体
     */
    public MtShop findByEpoiId(String epoiId) {
        return mtShopMapper.selectByEpoiId(epoiId);
    }

    /**
     * 根据 poiId 查询店铺
     *
     * @param poiId 店铺 poiId
     * @return 店铺实体
     */
    public MtShop findByPoiId(String poiId) {
        return mtShopMapper.selectByPoiId(poiId);
    }

    /**
     * 根据 poiId 查询店铺，不存在时抛出业务异常
     *
     * @param poiId 店铺 poiId
     * @return 店铺实体
     */
    public MtShop getByPoiIdOrThrow(String poiId) {
        String normalizedPoiId = StringUtils.trimToNull(poiId);
        if (normalizedPoiId == null) {
            throw new BusinessException("店铺ID不能为空");
        }
        MtShop shop = findByPoiId(normalizedPoiId);
        if (shop == null) {
            throw new BusinessException("门店不存在或尚未同步，请确认店铺ID");
        }
        return shop;
    }

    /**
     * 保存授权店铺
     *
     * @param tokenData  美团 token 数据
     * @param shopDetail 店铺详情
     * @return 店铺实体
     */
    @Transactional(rollbackFor = Exception.class)
    public MtShop saveAuthorizedShop(MeituanTokenData tokenData, Data shopDetail) {
        LocalDateTime now = LocalDateTime.now();
        String epoiId = StringUtils.defaultIfBlank(shopDetail == null ? null : shopDetail.getEpoiId(), tokenData.getOpBizCode());
        MtShop current = findByEpoiId(epoiId);
        boolean isNew = current == null;
        MtShop shop = isNew ? new MtShop() : current;

        if (isNew) {
            shop.setCreateTime(now);
        }

        String shopName = firstNonBlank(shopDetail == null ? null : shopDetail.getName(), tokenData.getOpBizName(), tokenData.getOpBizCode());
        shop.setName(shopName);
        shop.setPoiName(shopName);
        shop.setAddress(firstNonBlank(shopDetail == null ? null : shopDetail.getAddress(), shop.getAddress()));
        shop.setLatitude(formatCoordinate(shopDetail == null ? null : shopDetail.getLatitude(), shop.getLatitude()));
        shop.setLongitude(formatCoordinate(shopDetail == null ? null : shopDetail.getLongitude(), shop.getLongitude()));
        shop.setEpoiId(epoiId);
        shop.setPoiId(StringUtils.defaultIfBlank(shop.getPoiId(), null));
        shop.setMiniPoiId(StringUtils.defaultIfBlank(shop.getMiniPoiId(), null));
        shop.setPromotionInfo(firstNonBlank(shopDetail == null ? null : shopDetail.getPromotionInfo(), shop.getPromotionInfo()));
        shop.setContactPhone(firstNonBlank(
                shopDetail == null ? null : shopDetail.getPhone(),
                shopDetail == null ? null : shopDetail.getStandbyTel(),
                shop.getContactPhone()
        ));
        shop.setPhone(firstNonBlank(shop.getPhone(), shop.getContactPhone()));
        shop.setLogo(firstNonBlank(shopDetail == null ? null : shopDetail.getPicUrl(), shop.getLogo()));
        shop.setShippingTime(firstNonBlank(extractShippingTime(shopDetail), shop.getShippingTime()));
        shop.setSendPrice(toBigDecimal(shopDetail == null ? null : shopDetail.getShippingFee(), shop.getSendPrice()));
        shop.setUpdateTime(now);

        if (isNew) {
            mtShopMapper.insert(shop);
            grantPowerForNewAuthorizedShop(shop.getId(), now);
            return shop;
        }

        mtShopMapper.updateById(shop);
        return shop;
    }

    private void grantPowerForNewAuthorizedShop(Long shopId, LocalDateTime now) {
        Integer grantAmount = powerProperties.getNewAuthorizedShopGrantAmount();
        if (shopId == null || grantAmount == null || grantAmount <= 0) {
            return;
        }

        MtPowerGrant grant = new MtPowerGrant();
        grant.setGrantNo(buildGrantNo(shopId));
        grant.setShopId(shopId);
        grant.setPowerAmount(grantAmount);
        grant.setGrantReason(AUTHORIZED_SHOP_GRANT_REASON);
        grant.setRemark("首次授权自动赠送算力");
        grant.setOperatorUserId(AUTHORIZED_SHOP_GRANT_OPERATOR);
        grant.setOperatorUsername(AUTHORIZED_SHOP_GRANT_OPERATOR);
        grant.setStatus(AUTHORIZED_SHOP_GRANT_STATUS_SUCCESS);
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        try {
            mtPowerGrantMapper.insert(grant);
            mtPowerAccountService.addAdminGrant(shopId, grantAmount);
        } catch (DuplicateKeyException ignored) {
            // 授权回调可能重复触发，固定 grantNo 保证授权赠送幂等。
        }
    }

    /**
     * 用美团 H5 启动参数补齐门店映射
     *
     * @param shop    店铺实体
     * @param poiId   美团后台可见门店 ID
     * @param poiName 门店名称
     * @param address 门店地址
     * @return 更新后的店铺
     */
    public MtShop updateH5Binding(MtShop shop, String poiId, String poiName, String address) {
        boolean changed = false;
        if (StringUtils.isNotBlank(poiId) && !StringUtils.equals(shop.getPoiId(), poiId)) {
            shop.setPoiId(poiId);
            changed = true;
        }
        if (StringUtils.isBlank(shop.getPoiName()) && StringUtils.isNotBlank(poiName)) {
            shop.setPoiName(poiName);
            changed = true;
        }
        if (StringUtils.isBlank(shop.getName()) && StringUtils.isNotBlank(poiName)) {
            shop.setName(poiName);
            changed = true;
        }
        if (StringUtils.isBlank(shop.getAddress()) && StringUtils.isNotBlank(address)) {
            shop.setAddress(address);
            changed = true;
        }

        if (!changed) {
            return shop;
        }

        shop.setUpdateTime(LocalDateTime.now());
        mtShopMapper.updateById(shop);
        return shop;
    }

    /**
     * 仅在门店缺少 poiId 时补齐该字段
     *
     * @param shop  门店实体
     * @param poiId 待补齐的 poiId
     * @return 更新后的门店
     */
    public MtShop updatePoiIdIfMissing(MtShop shop, String poiId) {
        if (shop == null || StringUtils.isBlank(poiId) || StringUtils.isNotBlank(shop.getPoiId())) {
            return shop;
        }
        shop.setPoiId(poiId);
        shop.setUpdateTime(LocalDateTime.now());
        mtShopMapper.updateById(shop);
        return shop;
    }

    /**
     * 根据最新商家详情补齐营业时间
     *
     * @param shop 店铺实体
     * @param shopDetail 最新商家详情
     * @return 更新后的店铺
     */
    public MtShop updateShippingTimeFromAuthorizedDetail(MtShop shop, Data shopDetail) {
        if (shop == null || shopDetail == null) {
            return shop;
        }
        String shippingTime = extractShippingTime(shopDetail);
        if (StringUtils.isBlank(shippingTime) || StringUtils.equals(shop.getShippingTime(), shippingTime)) {
            return shop;
        }
        shop.setShippingTime(shippingTime);
        shop.setUpdateTime(LocalDateTime.now());
        mtShopMapper.updateById(shop);
        return shop;
    }

    private String formatCoordinate(Double value, String fallback) {
        return value == null ? fallback : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal toBigDecimal(Double value, BigDecimal fallback) {
        return value == null ? fallback : BigDecimal.valueOf(value);
    }

    private String extractShippingTime(Data shopDetail) {
        if (shopDetail == null) {
            return null;
        }
        return firstNonBlank(
                readStringByMethod(shopDetail, "getShippingTime"),
                readStringByMethod(shopDetail, "getShipping_time"),
                readStringByField(shopDetail, "shippingTime"),
                readStringByField(shopDetail, "shipping_time")
        );
    }

    private String readStringByMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String readStringByField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return value == null ? null : StringUtils.trimToNull(String.valueOf(value));
        } catch (Exception e) {
            return null;
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

    private String buildGrantNo(Long shopId) {
        return "AUTH_BONUS:" + shopId;
    }
}
