package com.gogoga.backend.service;

import com.gogoga.backend.client.MtOrderOpenApiClient;
import com.gogoga.backend.entity.mt.MtShop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 授权后门店 poiId 自动补齐服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtShopPoiIdBackfillService {

    // 美团订单列表接口近 5 天可查；从昨天开始回看，因此最多回看 4 天。
    static final int AUTH_LOOKBACK_DAYS = 4;
    static final int ORDER_STATUS_ALL = -1;
    static final int PAGE_NO_FIRST = 1;
    static final int PAGE_SIZE_FIRST = 1;

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_INT_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MtOrderOpenApiClient mtOrderOpenApiClient;
    private final MtShopService mtShopService;

    public MtShop tryBackfillAfterAuthorization(MtShop shop, String accessToken) {
        if (shop == null) {
            log.info("跳过授权后 poiId 补齐，原因=门店不存在");
            return null;
        }
        if (StringUtils.isNotBlank(shop.getPoiId())) {
            log.info("跳过授权后 poiId 补齐，原因=poiId已存在, shopId={}, epoiId={}, poiId={}",
                    shop.getId(), shop.getEpoiId(), shop.getPoiId());
            return shop;
        }
        if (StringUtils.isBlank(accessToken)) {
            log.info("跳过授权后 poiId 补齐，原因=缺少accessToken, shopId={}, epoiId={}",
                    shop.getId(), shop.getEpoiId());
            return shop;
        }

        LocalDate today = LocalDate.now(DEFAULT_ZONE_ID);
        for (int offset = 1; offset <= AUTH_LOOKBACK_DAYS; offset++) {
            String dayInt = today.minusDays(offset).format(DAY_INT_FORMATTER);
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
            Optional<String> poiId = mtOrderOpenApiClient.queryPoiIdByOrderId(
                    shop.getId(), accessToken, orderId.get());
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
}
