package com.gogoga.backend.service;

import com.gogoga.backend.config.YouzanProperties;
import com.gogoga.backend.constant.MtShopRightCodeConstant;
import com.gogoga.backend.constant.YouzanConstant;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.entity.youzan.YzAppOrder;
import com.gogoga.backend.entity.youzan.YzOrderRightUseRecord;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.mapper.YzAppOrderMapper;
import com.gogoga.backend.mapper.YzOrderRightUseRecordMapper;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminYouzanOrderRedeemVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminYouzanAppOrderServiceTest {

    @Mock
    private YzAppOrderMapper yzAppOrderMapper;

    @Mock
    private YzOrderRightUseRecordMapper yzOrderRightUseRecordMapper;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private MtShopRightMapper mtShopRightMapper;

    private AdminYouzanAppOrderService service;

    @BeforeEach
    void setUp() {
        YouzanProperties properties = new YouzanProperties();
        service = new AdminYouzanAppOrderService(
                yzAppOrderMapper,
                yzOrderRightUseRecordMapper,
                mtShopService,
                mtShopRightMapper,
                properties
        );
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("op-1");
        loginUser.setUserName("有赞运营");
        loginUser.setRoles(Set.of("youzan_operator"));
        UserContext.setLoginUser(loginUser);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRedeemPaidOrderAndCarryOverExistingPromotionRight() {
        YzAppOrder order = paidOrder();
        when(yzAppOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setName("美团门店");
        when(mtShopService.findById(100L)).thenReturn(shop);
        MtShopRight current = new MtShopRight();
        current.setId(200L);
        current.setShopId(100L);
        current.setServiceCode(MtShopRightCodeConstant.H5_PROMOTION);
        current.setType(2);
        current.setExpireAt(LocalDateTime.now().plusDays(3));
        when(mtShopRightMapper.selectByShopIdAndServiceCode(100L, MtShopRightCodeConstant.H5_PROMOTION))
                .thenReturn(current);
        doAnswer(invocation -> {
            YzOrderRightUseRecord record = invocation.getArgument(0);
            record.setId(300L);
            return 1;
        }).when(yzOrderRightUseRecordMapper).insert(any(YzOrderRightUseRecord.class));

        AdminYouzanOrderRedeemVO result = service.redeem(1L, 100L, "补权益");

        assertEquals(300L, result.getRecordId());
        assertEquals(MtShopRightCodeConstant.H5_PROMOTION, result.getRightCode());
        assertEquals(30, result.getIntervalDays());
        assertTrue(result.getNewExpireAt().isAfter(LocalDateTime.now().plusDays(32)));
        verify(mtShopRightMapper).updateById(any(MtShopRight.class));
        verify(yzAppOrderMapper).updateUseStatus(any(YzAppOrder.class));
    }

    @Test
    void shouldRejectInvalidInterval() {
        YzAppOrder order = paidOrder();
        order.setSkuIntervalText("一个月");
        when(yzAppOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.redeem(1L, 100L, null));

        assertEquals("有赞订单套餐有效期不是有效天数", exception.getMessage());
        verify(mtShopRightMapper, never()).insert(any());
    }

    @Test
    void shouldRejectAlreadyUsedOrder() {
        YzAppOrder order = paidOrder();
        order.setUseStatus(YouzanConstant.ORDER_USE_STATUS_USED);
        when(yzAppOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.redeem(1L, 100L, null));

        assertEquals("该有赞订单已核销", exception.getMessage());
    }

    @Test
    void shouldRejectUnpaidOrder() {
        YzAppOrder order = paidOrder();
        order.setStatus(10);
        when(yzAppOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.redeem(1L, 100L, null));

        assertEquals("仅支付成功的有赞订单可核销", exception.getMessage());
        verify(mtShopRightMapper, never()).insert(any());
    }

    @Test
    void shouldRejectMissingShop() {
        YzAppOrder order = paidOrder();
        when(yzAppOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        when(mtShopService.findById(100L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.redeem(1L, 100L, null));

        assertEquals("美团门店不存在", exception.getMessage());
        verify(mtShopRightMapper, never()).insert(any());
    }

    @Test
    void shouldRejectWhenSuccessfulUseRecordAlreadyExists() {
        YzAppOrder order = paidOrder();
        when(yzAppOrderMapper.selectByIdForUpdate(1L)).thenReturn(order);
        YzOrderRightUseRecord existing = new YzOrderRightUseRecord();
        existing.setId(9L);
        when(yzOrderRightUseRecordMapper.selectSuccessByOrderId(1L)).thenReturn(existing);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.redeem(1L, 100L, null));

        assertEquals("该有赞订单已核销", exception.getMessage());
        verify(mtShopRightMapper, never()).insert(any());
    }

    @Test
    void shouldRejectUserWithoutYouzanRole() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("user-1");
        loginUser.setRoles(Set.of("admin"));
        UserContext.setLoginUser(loginUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.redeem(1L, 100L, null));

        assertEquals(403, exception.getCode());
    }

    private YzAppOrder paidOrder() {
        YzAppOrder order = new YzAppOrder();
        order.setId(1L);
        order.setOrderNo("E001");
        order.setStatus(20);
        order.setUseStatus(YouzanConstant.ORDER_USE_STATUS_UNUSED);
        order.setSkuIntervalText("30");
        return order;
    }
}
