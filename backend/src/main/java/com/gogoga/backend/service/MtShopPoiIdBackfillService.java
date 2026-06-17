package com.gogoga.backend.service;

import com.gogoga.backend.client.MtOrderOpenApiClient;
import com.gogoga.backend.entity.mt.MtAccessToken;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAccessTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 门店 poiId 自动补齐服务
 *
 * @author system
 * @since 2026-04-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtShopPoiIdBackfillService {

    // 美团订单列表接口近 5 天可查，当前补数从昨天开始回看，因此最多只能回看 4 天。
    static final int AUTH_LOOKBACK_DAYS = 4;
    static final int ORDER_STATUS_ALL = -1;
    static final int PAGE_NO_FIRST = 1;
    static final int PAGE_SIZE_FIRST = 1;

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_INT_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MtOrderOpenApiClient mtOrderOpenApiClient;
    private final MtShopService mtShopService;
    private final MtAccessTokenMapper mtAccessTokenMapper;

    public MtShop tryBackfillAfterAuthorization(MtShop shop, String accessToken) {
        if (shouldSkip(shop, "auth")) {
            return shop;
        }
        for (int offset = 1; offset <= AUTH_LOOKBACK_DAYS; offset++) {
            String dayInt = LocalDate.now(DEFAULT_ZONE_ID).minusDays(offset).format(DAY_INT_FORMATTER);
            Optional<String> orderId = mtOrderOpenApiClient.queryFirstOrderId(
                    shop.getId(),
                    accessToken,
                    dayInt,
                    ORDER_STATUS_ALL,
                    PAGE_NO_FIRST,
                    PAGE_SIZE_FIRST
            );
            if (orderId.isEmpty()) {
                continue;
            }
            Optional<String> poiId = mtOrderOpenApiClient.queryPoiIdByOrderId(shop.getId(), accessToken, orderId.get());
            if (poiId.isPresent()) {
                MtShop updated = mtShopService.updatePoiIdIfMissing(shop, poiId.get());
                log.info("授权后补齐门店 poiId 成功，shopId={}, epoiId={}, poiId={}, sourceOrderId={}",
                        shop.getId(), shop.getEpoiId(), updated.getPoiId(), orderId.get());
                return updated;
            }
        }
        log.info("授权后未找到可用于补齐 poiId 的订单，shopId={}, epoiId={}, lookbackDays={}",
                shop.getId(), shop.getEpoiId(), AUTH_LOOKBACK_DAYS);
        return shop;
    }

    public MtShop tryBackfillFromOrderCallback(MtShop shop, String orderId) {
        if (shouldSkip(shop, "orderCallback")) {
            return shop;
        }
        if (StringUtils.isBlank(orderId)) {
            log.info("订单回调跳过 poiId 补齐，原因=缺少orderId, shopId={}, epoiId={}", shop.getId(), shop.getEpoiId());
            return shop;
        }
        MtAccessToken accessToken = mtAccessTokenMapper.selectByShopId(shop.getId());
        if (accessToken == null || StringUtils.isBlank(accessToken.getAccessToken())) {
            log.info("订单回调跳过 poiId 补齐，原因=缺少accessToken, shopId={}, epoiId={}", shop.getId(), shop.getEpoiId());
            return shop;
        }
        Optional<String> poiId = mtOrderOpenApiClient.queryPoiIdByOrderId(shop.getId(), accessToken.getAccessToken(), orderId);
        if (poiId.isEmpty()) {
            log.info("订单回调未从订单详情中获取到 poiId，shopId={}, epoiId={}, orderId={}",
                    shop.getId(), shop.getEpoiId(), orderId);
            return shop;
        }
        MtShop updated = mtShopService.updatePoiIdIfMissing(shop, poiId.get());
        log.info("订单回调补齐门店 poiId 成功，shopId={}, epoiId={}, poiId={}, orderId={}",
                shop.getId(), shop.getEpoiId(), updated.getPoiId(), orderId);
        return updated;
    }

    private boolean shouldSkip(MtShop shop, String source) {
        if (shop == null) {
            log.info("跳过 poiId 补齐，原因=门店不存在, source={}", source);
            return true;
        }
        if (StringUtils.isNotBlank(shop.getPoiId())) {
            log.info("跳过 poiId 补齐，原因=poiId已存在, source={}, shopId={}, epoiId={}, poiId={}",
                    source, shop.getId(), shop.getEpoiId(), shop.getPoiId());
            return true;
        }
        return false;
    }
}
