package com.gogoga.backend.service;

import com.gogoga.backend.annotation.RequiresShopRight;
import com.gogoga.backend.client.MtAppBuyInfoOpenApiClient;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.entity.mt.MtVersionServiceMapping;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.mapper.MtVersionServiceMappingMapper;
import com.gogoga.backend.vo.MtH5ShopRightVO;
import com.meituan.sdk.model.wmoperNg.waimaiad.adAppBuyInfo.AdAppBuyInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门店权益服务
 *
 * @author system
 * @since 2026-03-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtShopRightService {

    private static final int RIGHT_TYPE_DURATION = 2;
    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.systemDefault();

    private final MtShopRightMapper mtShopRightMapper;
    private final MtVersionServiceMappingMapper mtVersionServiceMappingMapper;
    private final MtH5ShopService mtH5ShopService;
    private final MtAppBuyInfoOpenApiClient mtAppBuyInfoOpenApiClient;

    /**
     * 查询当前门店权益
     *
     * @return 权益列表
     */
    public List<MtH5ShopRightVO> getCurrentRights() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        List<MtShopRight> rights = mtShopRightMapper.selectByShopId(shop.getId());
        List<MtH5ShopRightVO> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(rights)) {
            return result;
        }

        rights.stream()
                .sorted(Comparator.comparing(MtShopRight::getServiceCode, Comparator.nullsLast(String::compareTo)))
                .forEach(item -> {
                    MtH5ShopRightVO vo = new MtH5ShopRightVO();
                    vo.setServiceCode(item.getServiceCode());
                    vo.setType(item.getType());
                    vo.setActive(isRightActive(item));
                    vo.setExpireAt(item.getExpireAt());
                    vo.setSourceVersion(item.getSourceVersion());
                    result.add(vo);
                });
        return result;
    }

    /**
     * 查询首页会员卡展示使用的当前有效权益
     *
     * @param shopId 店铺 ID
     * @return 当前有效权益
     */
    public MtShopRight findHomepageActiveRight(Long shopId) {
        List<MtShopRight> rights = mtShopRightMapper.selectByShopId(shopId);
        if (CollectionUtils.isEmpty(rights)) {
            return null;
        }

        return rights.stream()
                .filter(this::isRightActive)
                .filter(this::isHomepageRelevantRight)
                .sorted(Comparator
                        .comparing((MtShopRight item) -> resolveHomepageRightPriority(item.getServiceCode()))
                        .thenComparing(MtShopRight::getExpireAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验当前门店是否拥有指定权益
     *
     * @param logical 逻辑模式
     * @param serviceCodes 权益编码
     * @return true 表示拥有权益
     */
    public boolean hasCurrentRight(RequiresShopRight.Logical logical, String... serviceCodes) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return hasShopRight(shop.getId(), logical, serviceCodes);
    }

    /**
     * 校验指定门店是否拥有有效权益，供异步任务使用。
     */
    public boolean hasShopRight(Long shopId, RequiresShopRight.Logical logical, String... serviceCodes) {
        if (shopId == null) {
            return false;
        }
        List<MtShopRight> rights = mtShopRightMapper.selectByShopId(shopId);
        if (CollectionUtils.isEmpty(rights) || serviceCodes == null || serviceCodes.length == 0) {
            return false;
        }
        if (hasVipRight(rights)) {
            return true;
        }

        if (logical == RequiresShopRight.Logical.AND) {
            for (String serviceCode : serviceCodes) {
                if (!hasActiveRight(rights, serviceCode)) {
                    return false;
                }
            }
            return true;
        }

        for (String serviceCode : serviceCodes) {
            if (hasActiveRight(rights, serviceCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验门店是否具备 IM 自动回复能力（含知识库配置）。
     *
     * <p>自动回复与群发的权益范围已经不同，各调用点一律走这两个语义方法，不要再各自罗列权益码，
     * 否则新增权益时极易漏改其中一处，造成权益穿透。</p>
     */
    public boolean hasAutoReplyRight(Long shopId) {
        return hasShopRight(shopId, RequiresShopRight.Logical.OR,
                MtShopRightCodeConstant.VIP_CODE,
                MtShopRightCodeConstant.H5_PROMOTION,
                MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY);
    }

    /**
     * 校验门店是否具备消息群发能力。
     *
     * <p>{@link MtShopRightCodeConstant#IM_AUTO_REPLY_ONLY} 不含群发，故刻意不在此列。</p>
     */
    public boolean hasBroadcastRight(Long shopId) {
        return hasShopRight(shopId, RequiresShopRight.Logical.OR,
                MtShopRightCodeConstant.VIP_CODE,
                MtShopRightCodeConstant.H5_PROMOTION);
    }

    /**
     * 授权后同步权益
     *
     * @param shop 门店
     * @param accessToken 美团 access token
     * @return true 表示已成功应用权益
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean syncRightsFromBuyInfo(MtShop shop, String accessToken) {
        if (shop == null || shop.getId() == null || StringUtils.isBlank(accessToken)) {
            log.info("授权后同步权益跳过，原因=参数缺失, shopId={}, hasAccessToken={}",
                    shop == null ? null : shop.getId(), StringUtils.isNotBlank(accessToken));
            return false;
        }

        AdAppBuyInfoResponse buyInfo = fetchBuyInfo(accessToken);
        if (buyInfo == null || StringUtils.isBlank(buyInfo.getVersion()) || buyInfo.getEndTime() == null) {
            log.info("授权后同步权益跳过，原因=buy-info为空或必要字段缺失, shopId={}, version={}, endTime={}",
                    shop.getId(),
                    buyInfo == null ? null : buyInfo.getVersion(),
                    buyInfo == null ? null : buyInfo.getEndTime());
            return false;
        }

        log.info("授权后同步权益开始，shopId={}, mtVersion={}, buyInfoEndTime={}",
                shop.getId(), buyInfo.getVersion(), toLocalDateTime(buyInfo.getEndTime()));
        return applyBuyInfoRights(shop.getId(), buyInfo.getVersion(), buyInfo.getEndTime());
    }

    /**
     * 应用订阅订单权益补偿
     *
     * @param order 订阅订单
     * @return true 表示已成功应用权益
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyOrderRights(MtAppOrder order) {
        return applyOrderRights(order, new HashSet<>());
    }

    /**
     * 应用订阅订单权益补偿。
     *
     * @param order 订阅订单
     * @param consumedBuyInfoCreditKeys 本批次已消费的 buy-info 权益抵扣标识
     * @return true 表示已成功应用或抵扣权益
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean applyOrderRights(MtAppOrder order, Set<String> consumedBuyInfoCreditKeys) {
        if (order == null || order.getShopId() == null || StringUtils.isBlank(order.getVersion())
                || order.getEndTime() == null || order.getDays() == null || order.getDays() <= 0) {
            log.info("订阅订单权益暂不应用，原因=订单必要字段缺失, orderId={}, shopId={}, version={}, days={}, endTime={}",
                    order == null ? null : order.getOrderId(),
                    order == null ? null : order.getShopId(),
                    order == null ? null : order.getVersion(),
                    order == null ? null : order.getDays(),
                    order == null ? null : order.getEndTime());
            return false;
        }
        Set<String> creditKeys = consumedBuyInfoCreditKeys == null ? new HashSet<>() : consumedBuyInfoCreditKeys;
        return applyOrderVersionRights(order, creditKeys);
    }

    private boolean applyBuyInfoRights(Long shopId, String mtVersion, Long endTime) {
        List<MtVersionServiceMapping> mappings = mtVersionServiceMappingMapper.selectEnabledByMtVersion(mtVersion);
        if (CollectionUtils.isEmpty(mappings)) {
            log.warn("未找到美团版本权益映射，shopId={}, mtVersion={}", shopId, mtVersion);
            return false;
        }

        LocalDateTime expireAt = toLocalDateTime(endTime);
        LocalDateTime now = LocalDateTime.now();
        for (MtVersionServiceMapping mapping : mappings) {
            MtShopRight current = mtShopRightMapper.selectByShopIdAndServiceCode(shopId, mapping.getServiceCode());
            if (current == null) {
                mtShopRightMapper.insert(buildDurationRight(shopId, mapping.getServiceCode(), expireAt, mtVersion, null, now));
                log.info("buy-info创建门店权益，shopId={}, mtVersion={}, serviceCode={}, expireAt={}",
                        shopId, mtVersion, mapping.getServiceCode(), expireAt);
                continue;
            }

            LocalDateTime previousExpireAt = current.getExpireAt();
            LocalDateTime nextExpireAt = current.getExpireAt();
            if (nextExpireAt == null || expireAt.isAfter(nextExpireAt)) {
                nextExpireAt = expireAt;
            }

            current.setType(RIGHT_TYPE_DURATION);
            current.setExpireAt(nextExpireAt);
            current.setRemainingTimes(null);
            current.setSourceVersion(mtVersion);
            current.setUpdateTime(now);
            mtShopRightMapper.updateById(current);
            if (nextExpireAt.equals(previousExpireAt)) {
                log.info("buy-info保留已有门店权益到期时间，shopId={}, mtVersion={}, serviceCode={}, existingExpireAt={}, buyInfoExpireAt={}, lastOrderId={}",
                        shopId, mtVersion, mapping.getServiceCode(), previousExpireAt, expireAt, current.getLastOrderId());
            } else {
                log.info("buy-info延长门店权益，shopId={}, mtVersion={}, serviceCode={}, previousExpireAt={}, nextExpireAt={}, lastOrderId={}",
                        shopId, mtVersion, mapping.getServiceCode(), previousExpireAt, nextExpireAt, current.getLastOrderId());
            }
        }
        return true;
    }

    private boolean applyOrderVersionRights(MtAppOrder order, Set<String> consumedBuyInfoCreditKeys) {
        List<MtVersionServiceMapping> mappings = mtVersionServiceMappingMapper.selectEnabledByMtVersion(order.getVersion());
        if (CollectionUtils.isEmpty(mappings)) {
            log.warn("未找到美团版本权益映射，shopId={}, mtVersion={}, orderId={}",
                    order.getShopId(), order.getVersion(), order.getOrderId());
            return false;
        }

        LocalDateTime orderExpireAt = toLocalDateTime(order.getEndTime());
        LocalDateTime now = LocalDateTime.now();
        for (MtVersionServiceMapping mapping : mappings) {
            MtShopRight current = mtShopRightMapper.selectByShopIdAndServiceCode(order.getShopId(), mapping.getServiceCode());
            if (current == null || !isRightActiveAt(current, now)) {
                MtShopRight right = current == null
                        ? buildDurationRight(order.getShopId(), mapping.getServiceCode(), orderExpireAt, order.getVersion(), order.getOrderId(), now)
                        : current;
                if (current == null) {
                    mtShopRightMapper.insert(right);
                    log.info("订阅订单创建门店权益，shopId={}, orderId={}, mtVersion={}, serviceCode={}, days={}, expireAt={}",
                            order.getShopId(), order.getOrderId(), order.getVersion(), mapping.getServiceCode(),
                            order.getDays(), orderExpireAt);
                } else {
                    LocalDateTime previousExpireAt = right.getExpireAt();
                    updateDurationRight(right, orderExpireAt, order.getVersion(), order.getOrderId(), now);
                    mtShopRightMapper.updateById(right);
                    log.info("订阅订单重置已过期门店权益，shopId={}, orderId={}, mtVersion={}, serviceCode={}, days={}, previousExpireAt={}, nextExpireAt={}",
                            order.getShopId(), order.getOrderId(), order.getVersion(), mapping.getServiceCode(),
                            order.getDays(), previousExpireAt, orderExpireAt);
                }
                continue;
            }

            String buyInfoCreditKey = buildBuyInfoCreditKey(order.getShopId(), mapping.getServiceCode());
            if (canConsumeBuyInfoCredit(current, orderExpireAt, consumedBuyInfoCreditKeys, buyInfoCreditKey)) {
                LocalDateTime previousExpireAt = current.getExpireAt();
                current.setType(RIGHT_TYPE_DURATION);
                current.setRemainingTimes(null);
                current.setSourceVersion(order.getVersion());
                current.setLastOrderId(order.getOrderId());
                current.setUpdateTime(now);
                mtShopRightMapper.updateById(current);
                consumedBuyInfoCreditKeys.add(buyInfoCreditKey);
                log.info("订阅订单消费buy-info权益下限，shopId={}, orderId={}, mtVersion={}, serviceCode={}, days={}, currentExpireAt={}, orderEndTime={}, creditKey={}",
                        order.getShopId(), order.getOrderId(), order.getVersion(), mapping.getServiceCode(),
                        order.getDays(), previousExpireAt, orderExpireAt, buyInfoCreditKey);
                continue;
            }

            LocalDateTime previousExpireAt = current.getExpireAt();
            LocalDateTime nextExpireAt = current.getExpireAt().plusDays(order.getDays());
            updateDurationRight(current, nextExpireAt, order.getVersion(), order.getOrderId(), now);
            mtShopRightMapper.updateById(current);
            log.info("订阅订单累加门店权益，shopId={}, orderId={}, mtVersion={}, serviceCode={}, days={}, previousExpireAt={}, nextExpireAt={}",
                    order.getShopId(), order.getOrderId(), order.getVersion(), mapping.getServiceCode(),
                    order.getDays(), previousExpireAt, nextExpireAt);
        }
        return true;
    }

    private MtShopRight buildDurationRight(
            Long shopId,
            String serviceCode,
            LocalDateTime expireAt,
            String sourceVersion,
            String lastOrderId,
            LocalDateTime now
    ) {
        MtShopRight right = new MtShopRight();
        right.setShopId(shopId);
        right.setServiceCode(serviceCode);
        right.setType(RIGHT_TYPE_DURATION);
        right.setExpireAt(expireAt);
        right.setRemainingTimes(null);
        right.setSourceVersion(sourceVersion);
        right.setLastOrderId(lastOrderId);
        right.setCreateTime(now);
        right.setUpdateTime(now);
        return right;
    }

    private void updateDurationRight(
            MtShopRight right,
            LocalDateTime expireAt,
            String sourceVersion,
            String lastOrderId,
            LocalDateTime now
    ) {
        right.setType(RIGHT_TYPE_DURATION);
        right.setExpireAt(expireAt);
        right.setRemainingTimes(null);
        right.setSourceVersion(sourceVersion);
        right.setLastOrderId(lastOrderId);
        right.setUpdateTime(now);
    }

    private boolean canConsumeBuyInfoCredit(
            MtShopRight current,
            LocalDateTime orderExpireAt,
            Set<String> consumedBuyInfoCreditKeys,
            String buyInfoCreditKey
    ) {
        return StringUtils.isBlank(current.getLastOrderId())
                && current.getExpireAt() != null
                && !current.getExpireAt().isBefore(orderExpireAt)
                && !consumedBuyInfoCreditKeys.contains(buyInfoCreditKey);
    }

    private boolean isRightActiveAt(MtShopRight right, LocalDateTime now) {
        if (right == null || right.getType() == null) {
            return false;
        }
        if (RIGHT_TYPE_DURATION == right.getType()) {
            return right.getExpireAt() != null && right.getExpireAt().isAfter(now);
        }
        return right.getRemainingTimes() != null && right.getRemainingTimes() > 0;
    }

    private String buildBuyInfoCreditKey(Long shopId, String serviceCode) {
        return shopId + "#" + serviceCode;
    }

    private boolean hasVipRight(List<MtShopRight> rights) {
        return hasActiveRight(rights, MtShopRightCodeConstant.VIP_CODE);
    }

    private boolean isHomepageRelevantRight(MtShopRight right) {
        if (right == null) {
            return false;
        }
        return StringUtils.equalsAny(
                right.getServiceCode(),
                MtShopRightCodeConstant.VIP_CODE,
                MtShopRightCodeConstant.H5_PROMOTION,
                MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY
        );
    }

    private boolean hasActiveRight(List<MtShopRight> rights, String serviceCode) {
        if (CollectionUtils.isEmpty(rights) || StringUtils.isBlank(serviceCode)) {
            return false;
        }
        return rights.stream().anyMatch(item -> StringUtils.equals(serviceCode, item.getServiceCode()) && isRightActive(item));
    }

    private boolean isRightActive(MtShopRight right) {
        if (right == null || right.getType() == null) {
            return false;
        }
        if (RIGHT_TYPE_DURATION == right.getType()) {
            return right.getExpireAt() != null && right.getExpireAt().isAfter(LocalDateTime.now());
        }
        return right.getRemainingTimes() != null && right.getRemainingTimes() > 0;
    }

    private int resolveHomepageRightPriority(String serviceCode) {
        if (StringUtils.equals(serviceCode, MtShopRightCodeConstant.VIP_CODE)) {
            return 0;
        }
        if (StringUtils.equals(serviceCode, MtShopRightCodeConstant.H5_PROMOTION)) {
            return 1;
        }
        if (StringUtils.equals(serviceCode, MtShopRightCodeConstant.IM_AUTO_REPLY_ONLY)) {
            return 2;
        }
        return 3;
    }

    private AdAppBuyInfoResponse fetchBuyInfo(String accessToken) {
        return mtAppBuyInfoOpenApiClient.queryBuyInfo(accessToken).orElse(null);
    }

    private LocalDateTime toLocalDateTime(Long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), DEFAULT_ZONE_ID);
    }
}
