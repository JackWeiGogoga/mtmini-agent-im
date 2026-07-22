package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.gogoga.backend.service.sms.notification.MerchantLifecycleSmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 单笔应用订单的权益、订单状态和通知事件事务边界。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MtAppOrderRightApplicationService {

    private final MtAppOrderMapper mtAppOrderMapper;
    private final MtShopRightService mtShopRightService;
    private final MerchantLifecycleSmsService lifecycleSmsService;

    @Transactional(rollbackFor = Exception.class)
    public MtAppOrderRightApplicationResult applyOne(
            MtAppOrder pendingOrder,
            Set<String> consumedBuyInfoCreditKeys
    ) {
        if (pendingOrder == null || pendingOrder.getId() == null) {
            return new MtAppOrderRightApplicationResult(false, consumedBuyInfoCreditKeys);
        }
        MtAppOrder current = mtAppOrderMapper.selectByIdForUpdate(pendingOrder.getId());
        if (current == null || !Integer.valueOf(0).equals(current.getType())) {
            return new MtAppOrderRightApplicationResult(false, consumedBuyInfoCreditKeys);
        }

        Set<String> workingCreditKeys = new HashSet<>();
        if (consumedBuyInfoCreditKeys != null) {
            workingCreditKeys.addAll(consumedBuyInfoCreditKeys);
        }
        boolean applied = mtShopRightService.applyOrderRights(current, workingCreditKeys);
        if (!applied) {
            return new MtAppOrderRightApplicationResult(false, consumedBuyInfoCreditKeys);
        }

        LocalDateTime now = LocalDateTime.now();
        current.setType(1);
        current.setUpdateTime(now);
        mtAppOrderMapper.updateById(current);
        try {
            lifecycleSmsService.enqueuePurchaseGuide(
                    current.getShopId(),
                    current.getOrderId(),
                    current.getDays(),
                    now
            );
        } catch (RuntimeException error) {
            log.error("购买短信触发异常，已 fail-open 不影响权益和订单, shopId={}, orderId={}, days={}, "
                            + "errorType={}, errorMessage={}",
                    current.getShopId(), current.getOrderId(), current.getDays(),
                    error.getClass().getSimpleName(),
                    StringUtils.abbreviate(StringUtils.defaultIfBlank(
                            error.getMessage(), error.getClass().getSimpleName()), 500),
                    error);
        }
        return new MtAppOrderRightApplicationResult(true, workingCreditKeys);
    }
}
