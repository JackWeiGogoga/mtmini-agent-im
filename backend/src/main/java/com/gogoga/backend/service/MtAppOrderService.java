package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtAppOrder;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.AppOrderListRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.AppOrderListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 美团订阅订单拉取服务
 *
 * @author system
 * @since 2026-03-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtAppOrderService {

    private final MtAppOrderMapper mtAppOrderMapper;
    private final MtShopService mtShopService;
    private final MeituanClient meituanClient;
    private final MtProperties mtProperties;
    private final MtShopRightService mtShopRightService;

    /**
     * 拉取近期订阅订单
     */
    public void pullRecentOrders() {
        if (mtProperties.getAppOrder().getAppId() == null) {
            log.warn("未配置 mt.app-order.app-id，跳过拉取订阅订单");
            return;
        }

        long endTime = Instant.now().getEpochSecond();
        long startTime = endTime - mtProperties.getAppOrder().getLookbackSeconds();
        Long latestOrderTime = mtAppOrderMapper.selectLatestOrderTime();
        if (latestOrderTime != null && latestOrderTime.longValue() > 0L) {
            startTime = latestOrderTime.longValue();
        }

        int pageNum = 1;
        int pageSize = mtProperties.getAppOrder().getPageSize();
        int totalFetched = 0;
        int processedPages = 0;
        log.info("开始拉取近期订阅订单，appId={}, startTime={}, endTime={}, latestOrderTime={}, pageSize={}",
                mtProperties.getAppOrder().getAppId(), startTime, endTime, latestOrderTime, pageSize);
        while (true) {
            List<MtAppOrder> page = fetchPage(startTime, endTime, pageNum, pageSize);
            if (CollectionUtils.isEmpty(page)) {
                log.info("拉取订阅订单返回空页，pageNum={}, processedPages={}, totalFetched={}",
                        pageNum, processedPages, totalFetched);
                break;
            }
            processedPages++;
            totalFetched += page.size();
            log.info("拉取订阅订单成功，pageNum={}, pageSize={}, fetchedCount={}", pageNum, pageSize, page.size());

            for (MtAppOrder order : page) {
                saveOrUpdate(order);
            }

            if (page.size() < pageSize) {
                log.info("拉取订阅订单到达末页，pageNum={}, fetchedCount={}, totalFetched={}",
                        pageNum, page.size(), totalFetched);
                break;
            }
            pageNum++;
        }

        applyPendingRights();
        log.info("拉取近期订阅订单完成，processedPages={}, totalFetched={}", processedPages, totalFetched);
    }

    /**
     * 保存或更新订阅订单
     *
     * @param incoming 订阅订单
     */
    public void saveOrUpdate(MtAppOrder incoming) {
        if (incoming == null || StringUtils.isBlank(incoming.getOrderId()) || StringUtils.isBlank(incoming.getVersion())) {
            return;
        }

        MtAppOrder existing = mtAppOrderMapper.selectByOrderId(incoming.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        MtAppOrder target = existing == null ? new MtAppOrder() : existing;
        if (existing == null) {
            target.setCreateTime(now);
            target.setType(0);
        }

        target.setOrderId(incoming.getOrderId());
        target.setOrderViewId(incoming.getOrderViewId());
        target.setAppId(incoming.getAppId());
        target.setServiceCode(incoming.getServiceCode());
        target.setPayStatus(incoming.getPayStatus());
        target.setVersion(incoming.getVersion());
        target.setVersionName(incoming.getVersionName());
        target.setDays(incoming.getDays());
        target.setStartTime(incoming.getStartTime());
        target.setEndTime(incoming.getEndTime());
        target.setRightStatus(incoming.getRightStatus());
        target.setRealPayPrice(incoming.getRealPayPrice());
        target.setOrderTime(incoming.getOrderTime());
        target.setUpdateTime(now);

        enrichByShop(target);

        if (existing == null) {
            mtAppOrderMapper.insert(target);
            return;
        }
        mtAppOrderMapper.updateById(target);
    }

    /**
     * 应用待补偿的门店权益
     */
    public void applyPendingRights() {
        List<MtAppOrder> pendingOrders = mtAppOrderMapper.selectPendingRightOrders();
        if (CollectionUtils.isEmpty(pendingOrders)) {
            log.info("没有待补偿的订阅订单权益需要处理");
            return;
        }

        int appliedCount = 0;
        Set<String> consumedBuyInfoCreditKeys = new HashSet<>();
        for (MtAppOrder pendingOrder : pendingOrders) {
            log.info("开始处理待补偿订阅订单权益，orderId={}, shopId={}, serviceCode={}, version={}, days={}, endTime={}, type={}",
                    pendingOrder.getOrderId(),
                    pendingOrder.getShopId(),
                    pendingOrder.getServiceCode(),
                    pendingOrder.getVersion(),
                    pendingOrder.getDays(),
                    pendingOrder.getEndTime(),
                    pendingOrder.getType());
            boolean applied = mtShopRightService.applyOrderRights(pendingOrder, consumedBuyInfoCreditKeys);
            if (!applied) {
                log.info("订阅订单权益暂未应用，orderId={}, shopId={}, version={}, days={}, endTime={}",
                        pendingOrder.getOrderId(), pendingOrder.getShopId(), pendingOrder.getVersion(),
                        pendingOrder.getDays(), pendingOrder.getEndTime());
                continue;
            }
            pendingOrder.setType(1);
            pendingOrder.setUpdateTime(LocalDateTime.now());
            mtAppOrderMapper.updateById(pendingOrder);
            log.info("订阅订单权益处理完成并标记已应用，orderId={}, shopId={}, version={}, consumedBuyInfoCreditCount={}",
                    pendingOrder.getOrderId(), pendingOrder.getShopId(), pendingOrder.getVersion(),
                    consumedBuyInfoCreditKeys.size());
            appliedCount++;
        }
        log.info("处理待补偿订阅订单权益完成，pendingCount={}, appliedCount={}", pendingOrders.size(), appliedCount);
    }

    private void enrichByShop(MtAppOrder order) {
        if (StringUtils.isBlank(order.getServiceCode())) {
            return;
        }
        MtShop shop = mtShopService.findByEpoiId(order.getServiceCode());
        if (shop == null) {
            return;
        }
        order.setShopId(shop.getId());
        order.setAddress(shop.getAddress());
        order.setShopName(shop.getName());
        order.setPhone(StringUtils.defaultIfBlank(shop.getPhone(), shop.getContactPhone()));
    }

    private List<MtAppOrder> fetchPage(long startTime, long endTime, int pageNum, int pageSize) {
        AppOrderListRequest request = new AppOrderListRequest();
        request.setAppId(mtProperties.getAppOrder().getAppId().longValue());
        request.setStartTime(Long.valueOf(startTime).intValue());
        request.setEndTime(Long.valueOf(endTime).intValue());
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);

        try {
            MeituanResponse<AppOrderListResponse> response = meituanClient.invokeApi(request);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("拉取美团订阅订单失败，pageNum={}, code={}, msg={}", pageNum,
                        response == null ? null : response.getCode(),
                        response == null ? null : response.getMsg());
                return new ArrayList<>();
            }

            AppOrderListResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("拉取美团订阅订单业务失败，pageNum={}, result={}", pageNum, payload.getResult());
                return new ArrayList<>();
            }

            List<com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.Order> orders = payload.getOrder();
            if (orders == null) {
                return new ArrayList<>();
            }

            List<MtAppOrder> result = new ArrayList<>(orders.size());
            for (com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.Order order : orders) {
                result.add(toMtAppOrder(order));
            }
            return result;
        } catch (MtSdkException e) {
            log.error("拉取美团订阅订单异常，pageNum={}", pageNum, e);
            return new ArrayList<>();
        }
    }

    private MtAppOrder toMtAppOrder(com.meituan.sdk.model.wmoperNg.waimaiad.appOrderList.Order order) {
        MtAppOrder appOrder = new MtAppOrder();
        appOrder.setOrderId(order.getOrderId() == null ? null : String.valueOf(order.getOrderId()));
        appOrder.setOrderViewId(order.getOrderViewId() == null ? null : String.valueOf(order.getOrderViewId()));
        appOrder.setAppId(order.getAppId() == null ? null : String.valueOf(order.getAppId()));
        appOrder.setServiceCode(order.getServiceCode());
        appOrder.setPayStatus(order.getPayStatus() == null ? null : order.getPayStatus().intValue());
        appOrder.setVersion(order.getVersion());
        appOrder.setVersionName(order.getVersionName());
        appOrder.setDays(order.getDays() == null ? null : order.getDays().intValue());
        appOrder.setStartTime(order.getStartTime());
        appOrder.setEndTime(order.getEndTime());
        appOrder.setRightStatus(order.getRightStatus() == null ? null : String.valueOf(order.getRightStatus()));
        appOrder.setRealPayPrice(order.getRealPayPrice() == null ? null : order.getRealPayPrice().intValue());
        appOrder.setOrderTime(order.getOrderTime());
        return appOrder;
    }
}
