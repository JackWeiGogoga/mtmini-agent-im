package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * 美团收费接口算力扣减服务。
 *
 * <p>收费接口的主业务调用不应被算力记账异常反向阻断，因此这里采用 best-effort 模式。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPaidApiPowerConsumeService {

    public static final String CONSUME_SOURCE_AUTHORIZED_SHOP = "MT_PAID_API_AUTHORIZED_SHOP";
    public static final String CONSUME_SOURCE_ORDER_LIST = "MT_PAID_API_ORDER_LIST";
    public static final String CONSUME_SOURCE_ORDER_DETAIL = "MT_PAID_API_ORDER_DETAIL";

    private final MtPowerConsumeService mtPowerConsumeService;
    private final PowerProperties powerProperties;
    private final PlatformTransactionManager transactionManager;

    public void tryConsumeAuthorizedShopQuery(Long shopId, String scene, String requestKey) {
        tryConsume(
                shopId,
                CONSUME_SOURCE_AUTHORIZED_SHOP,
                "authorized-shop:" + StringUtils.defaultIfBlank(scene, "unknown"),
                "授权门店详情收费接口调用，scene=" + StringUtils.defaultIfBlank(scene, "unknown"),
                requestKey
        );
    }

    public void tryConsumeOrderListQuery(Long shopId, String dayInt, Integer orderStatus, Integer pageNo, Integer pageSize) {
        tryConsume(
                shopId,
                CONSUME_SOURCE_ORDER_LIST,
                "order-list:" + StringUtils.defaultString(dayInt) + ":" + orderStatus + ":" + pageNo + ":" + pageSize,
                "订单列表收费接口调用，dayInt=" + dayInt + ", orderStatus=" + orderStatus + ", pageNo=" + pageNo + ", pageSize=" + pageSize,
                dayInt + "-" + orderStatus + "-" + pageNo + "-" + pageSize
        );
    }

    public void tryConsumeOrderDetailQuery(Long shopId, String orderId) {
        tryConsume(
                shopId,
                CONSUME_SOURCE_ORDER_DETAIL,
                "order-detail:" + StringUtils.defaultString(orderId),
                "订单详情收费接口调用，orderId=" + orderId,
                orderId
        );
    }

    private void tryConsume(Long shopId, String sourceType, String sourceId, String remark, String requestKey) {
        if (shopId == null) {
            return;
        }
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(shopId);
        request.setSourceType(sourceType);
        request.setSourceId(StringUtils.left(sourceId, 64));
        request.setRequestNo(buildRequestNo(sourceType, requestKey));
        request.setPowerAmount(powerProperties.getBilling().calculateMeituanPaidApiPowerAmount());
        request.setRemark(remark);
        try {
            newRequiresNewTemplate().executeWithoutResult(status -> mtPowerConsumeService.consume(request));
        } catch (Exception e) {
            log.warn("美团收费接口算力记账失败，shopId={}, sourceType={}, sourceId={}, reason={}",
                    shopId, sourceType, sourceId, e.getMessage());
        }
    }

    private TransactionTemplate newRequiresNewTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private String buildRequestNo(String sourceType, String requestKey) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String safeRequestKey = StringUtils.defaultIfBlank(requestKey, "unknown");
        String requestNo = "MT-PAID-" + sourceType + "-" + safeRequestKey + "-" + suffix;
        return StringUtils.left(requestNo, 64);
    }
}
