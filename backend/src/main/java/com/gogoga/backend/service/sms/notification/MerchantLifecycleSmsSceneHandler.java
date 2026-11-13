package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationSceneEnum;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.service.MtShopService;
import com.gogoga.backend.service.sms.ShopContactPhoneParser;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 三类商家生命周期通知的收件人准备和业务复核。
 */
@Component
@RequiredArgsConstructor
public class MerchantLifecycleSmsSceneHandler implements SmsNotificationSceneHandler {

    private final MtShopService mtShopService;
    private final MtShopRightMapper mtShopRightMapper;
    private final ShopContactPhoneParser phoneParser;
    private final SmsProperties smsProperties;

    @Override
    public boolean supports(SmsNotificationSceneEnum scene) {
        return scene == SmsNotificationSceneEnum.PURCHASE_GUIDE
                || scene == SmsNotificationSceneEnum.POWER_EXHAUSTED
                || scene == SmsNotificationSceneEnum.APP_EXPIRED;
    }

    @Override
    public SmsScenePreparation prepare(SmsNotificationEvent event) {
        if (SmsNotificationSceneEnum.APP_EXPIRED.name().equals(event.getScene()) && !isExpiryEventValid(event)) {
            return SmsScenePreparation.cancelled("APP_RIGHT_RENEWED_OR_EXPIRY_CHANGED");
        }
        MtShop shop = mtShopService.findById(event.getShopId());
        if (shop == null) {
            return SmsScenePreparation.cancelled("SHOP_NOT_FOUND");
        }
        return SmsScenePreparation.ready(phoneParser.parse(shop));
    }

    @Override
    public boolean canSend(SmsNotificationEvent event, String recipientPhone) {
        if (SmsNotificationSceneEnum.APP_EXPIRED.name().equals(event.getScene()) && !isExpiryEventValid(event)) {
            return false;
        }
        MtShop shop = mtShopService.findById(event.getShopId());
        List<String> latestPhones = phoneParser.parse(shop);
        return latestPhones.contains(recipientPhone);
    }

    private boolean isExpiryEventValid(SmsNotificationEvent event) {
        Map<String, Object> context = JsonUtil.toMap(event.getContextJson());
        String expectedText = context == null ? null : String.valueOf(context.get("expectedExpireAt"));
        if (StringUtils.isBlank(expectedText)) {
            return false;
        }
        LocalDateTime expectedExpireAt;
        try {
            expectedExpireAt = LocalDateTime.parse(expectedText);
        } catch (RuntimeException e) {
            return false;
        }
        LocalDateTime currentMaxExpireAt = mtShopRightMapper.selectMaxAppRightExpireAt(event.getShopId());
        if (!expectedExpireAt.equals(currentMaxExpireAt)) {
            return false;
        }
        ZoneId zoneId = ZoneId.of(smsProperties.getBusiness().getZoneId());
        return !mtShopRightMapper.existsActiveAppRight(event.getShopId(), LocalDateTime.now(zoneId));
    }
}
