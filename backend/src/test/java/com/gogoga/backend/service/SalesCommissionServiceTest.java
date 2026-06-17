package com.gogoga.backend.service;

import com.gogoga.backend.entity.SalesCommissionSnapshot;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.SalesCommissionSnapshotMapper;
import com.gogoga.backend.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesCommissionServiceTest {

    @Mock
    private SalesCommissionSnapshotMapper salesCommissionSnapshotMapper;

    @InjectMocks
    private SalesCommissionService salesCommissionService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreateSnapshotFromFrozenRechargeContext() {
        MtPowerRecharge recharge = new MtPowerRecharge();
        recharge.setId(1L);
        recharge.setRechargeNo("PR001");
        recharge.setShopId(9L);
        recharge.setPowerAmount(10_000_000);
        recharge.setTotalAmountFen(8_000);
        recharge.setDefaultCommissionRatio(30);
        recharge.setSalesUserId("sales-1");
        recharge.setSalesUserPhone("13800000000");
        recharge.setRedemptionCode("TX58J2");

        MtShop shop = new MtShop();
        shop.setPoiId("poi-9");
        shop.setPoiName("测试门店");
        shop.setPhone("18812345678");

        when(salesCommissionSnapshotMapper.selectByRechargeId(1L)).thenReturn(null);

        salesCommissionService.createSnapshotForPaidRecharge(recharge, shop);

        ArgumentCaptor<SalesCommissionSnapshot> captor = ArgumentCaptor.forClass(SalesCommissionSnapshot.class);
        verify(salesCommissionSnapshotMapper).insert(captor.capture());
        SalesCommissionSnapshot snapshot = captor.getValue();
        assertEquals("PR001", snapshot.getRechargeNo());
        assertEquals(30, snapshot.getCommissionRatio());
        assertEquals(2_400, snapshot.getCommissionAmountFen());
        assertEquals("UNSETTLED", snapshot.getSettlementStatus());
        assertEquals("TX58J2", snapshot.getRedemptionCode());
    }

    @Test
    void shouldRejectRatioUpdateForSettledSnapshot() {
        mockSuperAdmin();
        SalesCommissionSnapshot snapshot = new SalesCommissionSnapshot();
        snapshot.setId(2L);
        snapshot.setPaidAmountFen(8_000);
        snapshot.setSettlementStatus("SETTLED");
        when(salesCommissionSnapshotMapper.selectById(2L)).thenReturn(snapshot);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> salesCommissionService.updateCommissionRatio(2L, 25)
        );

        assertEquals("已结算订单不允许修改佣金比例", exception.getMessage());
    }

    @Test
    void shouldRejectReopenSettledSnapshot() {
        mockSuperAdmin();
        SalesCommissionSnapshot snapshot = new SalesCommissionSnapshot();
        snapshot.setId(3L);
        snapshot.setSettlementStatus("SETTLED");
        when(salesCommissionSnapshotMapper.selectById(3L)).thenReturn(snapshot);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> salesCommissionService.updateSettlementStatus(3L, "UNSETTLED")
        );

        assertEquals("已结算订单不允许重新打开", exception.getMessage());
    }

    @Test
    void shouldUpdateRatioForUnsettledSnapshot() {
        mockSuperAdmin();
        SalesCommissionSnapshot snapshot = new SalesCommissionSnapshot();
        snapshot.setId(4L);
        snapshot.setPaidAmountFen(40_000);
        snapshot.setSettlementStatus("UNSETTLED");
        when(salesCommissionSnapshotMapper.selectById(4L)).thenReturn(snapshot);
        when(salesCommissionSnapshotMapper.updateCommissionRatio(eq(4L), eq(15), eq(6_000))).thenReturn(1);

        salesCommissionService.updateCommissionRatio(4L, 15);

        verify(salesCommissionSnapshotMapper).updateCommissionRatio(4L, 15, 6_000);
    }

    private void mockSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);
    }
}
