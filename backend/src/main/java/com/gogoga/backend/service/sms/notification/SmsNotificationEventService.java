package com.gogoga.backend.service.sms.notification;

import com.gogoga.backend.config.SmsProperties;
import com.gogoga.backend.entity.sms.SmsNotificationEvent;
import com.gogoga.backend.enums.SmsNotificationEventStatusEnum;
import com.gogoga.backend.mapper.SmsNotificationEventMapper;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用短信业务事件入队服务。
 */
@Service
@RequiredArgsConstructor
public class SmsNotificationEventService {

    private final SmsNotificationEventMapper eventMapper;
    private final SmsProperties smsProperties;
    private final SmsNotificationMetricsService metricsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public SmsNotificationEventEnqueueResult enqueue(SmsNotificationEventEnqueueRequest request) {
        if (!smsProperties.getBusiness().isEnabled()) {
            return null;
        }
        validate(request);

        LocalDateTime now = LocalDateTime.now();
        SmsNotificationEvent event = new SmsNotificationEvent();
        event.setScene(request.scene().name());
        event.setBizKey(request.bizKey());
        event.setShopId(request.shopId());
        event.setOrderId(request.orderId());
        event.setTemplateId(request.templateId());
        event.setTemplateParamsJson(JsonUtil.toJson(request.templateParams()));
        event.setContextJson(JsonUtil.toJson(request.context()));
        event.setStatus(SmsNotificationEventStatusEnum.PENDING.name());
        event.setRetryCount(0);
        event.setMaxRetryCount(smsProperties.getBusiness().getMaxRetryCount());
        event.setAvailableAt(request.availableAt() == null ? now : request.availableAt());
        event.setCreateTime(now);
        event.setUpdateTime(now);
        try {
            eventMapper.insert(event);
            metricsService.increment(event.getScene(), "enqueued");
            return new SmsNotificationEventEnqueueResult(event, true);
        } catch (DuplicateKeyException e) {
            metricsService.increment(event.getScene(), "duplicate");
            SmsNotificationEvent existing = eventMapper.selectBySceneAndBizKey(
                    event.getScene(),
                    event.getBizKey()
            );
            return new SmsNotificationEventEnqueueResult(existing, false);
        }
    }

    public List<Long> selectExecutableIds(LocalDateTime now) {
        return eventMapper.selectExecutableIds(now, smsProperties.getBusiness().getPollSize());
    }

    private void validate(SmsNotificationEventEnqueueRequest request) {
        if (request == null
                || request.scene() == null
                || StringUtils.isBlank(request.bizKey())
                || request.shopId() == null
                || StringUtils.isBlank(request.templateId())) {
            throw new IllegalArgumentException("短信事件场景、业务键、门店和模板 ID 不能为空");
        }
    }
}
