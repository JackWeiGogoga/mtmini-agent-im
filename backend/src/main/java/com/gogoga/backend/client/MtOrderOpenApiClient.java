package com.gogoga.backend.client;

import com.gogoga.backend.service.MtPaidApiPowerConsumeService;
import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.order.wmoperOrderQueryOrderDetail.WmoperOrderQueryOrderDetailRequest;
import com.meituan.sdk.model.wmoperNg.order.wmoperOrderQueryOrderDetail.WmoperOrderQueryOrderDetailResponse;
import com.meituan.sdk.model.wmoperNg.order.wmoperngOrderList.WmoperngOrderListRequest;
import com.meituan.sdk.model.wmoperNg.order.wmoperngOrderList.WmoperngOrderListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 美团订单开放平台客户端。
 *
 * <p>仅用于授权后查询近期订单并从订单详情中补齐门店 poiId，
 * 不接收或保存美团外卖订单数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MtOrderOpenApiClient {

    private final MeituanClient meituanClient;
    private final MtPaidApiPowerConsumeService mtPaidApiPowerConsumeService;

    /**
     * 查询订单列表中的首个订单 ID。
     */
    public Optional<String> queryFirstOrderId(Long shopId,
                                              String accessToken,
                                              String dayInt,
                                              Integer orderStatus,
                                              Integer pageNo,
                                              Integer pageSize) {
        OrderListQueryResult result = queryOrderIds(shopId, accessToken, dayInt, orderStatus, pageNo, pageSize);
        return result.orderIds().stream().findFirst();
    }

    /**
     * 查询订单列表。
     */
    public OrderListQueryResult queryOrderIds(Long shopId,
                                              String accessToken,
                                              String dayInt,
                                              Integer orderStatus,
                                              Integer pageNo,
                                              Integer pageSize) {
        WmoperngOrderListRequest request = new WmoperngOrderListRequest();
        request.setDayInt(parseDayInt(dayInt));
        request.setOrderStatus(orderStatus);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        try {
            MeituanResponse<WmoperngOrderListResponse> response = meituanClient.invokeApi(request, accessToken);
            mtPaidApiPowerConsumeService.tryConsumeOrderListQuery(shopId, dayInt, orderStatus, pageNo, pageSize);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询美团订单列表失败，shopId={}, dayInt={}, pageNo={}, pageSize={}, response={}",
                        shopId, dayInt, pageNo, pageSize, response);
                return OrderListQueryResult.empty();
            }
            List<String> orderIds = response.getData().getOrderIds() == null
                    ? List.of()
                    : response.getData().getOrderIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .filter(StringUtils::isNotBlank)
                    .toList();
            return new OrderListQueryResult(orderIds, Boolean.TRUE.equals(response.getData().getHashMore()));
        } catch (MtSdkException e) {
            log.error("调用美团订单列表异常，shopId={}, dayInt={}, pageNo={}, pageSize={}",
                    shopId, dayInt, pageNo, pageSize, e);
            return OrderListQueryResult.empty();
        }
    }

    /**
     * 查询订单详情中的 poiId。
     */
    public Optional<String> queryPoiIdByOrderId(Long shopId, String accessToken, String orderId) {
        Optional<WmoperOrderQueryOrderDetailResponse> detail = queryOrderDetail(shopId, accessToken, orderId);
        if (detail.isEmpty() || detail.get().getPoiId() == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(detail.get().getPoiId()));
    }

    /**
     * 查询订单详情。
     */
    public Optional<WmoperOrderQueryOrderDetailResponse> queryOrderDetail(
            Long shopId,
            String accessToken,
            String orderId
    ) {
        Long parsedOrderId = parseOrderId(orderId);
        if (parsedOrderId == null) {
            log.warn("查询美团订单详情跳过，shopId={}, orderId不是数字，orderId={}", shopId, orderId);
            return Optional.empty();
        }

        WmoperOrderQueryOrderDetailRequest request = new WmoperOrderQueryOrderDetailRequest();
        request.setOrderId(parsedOrderId);
        try {
            MeituanResponse<WmoperOrderQueryOrderDetailResponse> response = meituanClient.invokeApi(request, accessToken);
            mtPaidApiPowerConsumeService.tryConsumeOrderDetailQuery(shopId, orderId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("查询美团订单详情失败，shopId={}, orderId={}, response={}", shopId, orderId, response);
                return Optional.empty();
            }
            return Optional.of(response.getData());
        } catch (MtSdkException e) {
            log.error("调用美团订单详情异常，shopId={}, orderId={}", shopId, orderId, e);
            return Optional.empty();
        }
    }

    private Integer parseDayInt(String dayInt) {
        if (StringUtils.isBlank(dayInt)) {
            throw new IllegalArgumentException("dayInt must not be blank");
        }
        return Integer.valueOf(dayInt);
    }

    private Long parseOrderId(String orderId) {
        if (StringUtils.isBlank(orderId)) {
            return null;
        }
        try {
            return Long.valueOf(orderId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record OrderListQueryResult(List<String> orderIds, boolean hasMore) {

        public static OrderListQueryResult empty() {
            return new OrderListQueryResult(List.of(), false);
        }
    }
}
