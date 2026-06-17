package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerPaymentCreateResultDTO;
import com.gogoga.backend.dto.PowerPaymentQueryResultDTO;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtPowerRecharge;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.mapper.MtPowerRechargeMapper;
import com.gogoga.backend.service.SalesRedemptionCodeService.ResolvedRedemptionCode;
import com.gogoga.backend.vo.MtH5PowerRechargeCreateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPowerRechargeServiceTest {

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private MtPowerRechargeMapper mtPowerRechargeMapper;

    @Mock
    private MtPowerGrantMapper mtPowerGrantMapper;

    @Mock
    private MtPowerPayService mtPowerPayService;

    @Mock
    private MtShopService mtShopService;

    @Mock
    private SalesRedemptionCodeService salesRedemptionCodeService;

    @Mock
    private SalesCommissionService salesCommissionService;

    private PowerProperties powerProperties;
    private MtPowerRechargeService mtPowerRechargeService;

    @BeforeEach
    void setUp() {
        powerProperties = new PowerProperties();
        powerProperties.getWechatPay().setAppId("wx-app-1");
        powerProperties.getWechatPay().setMchId("mch-1");
        powerProperties.validate();
        mtPowerRechargeService = new MtPowerRechargeService(
                powerProperties,
                mtPowerAccountService,
                mtPowerRechargeMapper,
                mtPowerGrantMapper,
                mtPowerPayService,
                mtShopService,
                salesRedemptionCodeService,
                salesCommissionService
        );
    }

    @Test
    void shouldFreezeRedemptionContextAndThirtyPercentCommissionForFirstNinetyDays() {
        MtShop shop = buildShop(LocalDateTime.now().minusDays(1));
        MtPowerAccount account = new MtPowerAccount();
        account.setShopId(shop.getId());
        account.setTotalRecharge(0);
        ResolvedRedemptionCode resolvedCode = ResolvedRedemptionCode.builder()
                .id(1L)
                .code("TX58J2")
                .salesUserId("sales-1")
                .salesUserPhone("13800000000")
                .build();

        when(mtPowerAccountService.getOrCreateAccount(shop.getId())).thenReturn(account);
        when(mtPowerRechargeMapper.selectLatestByShopId(shop.getId())).thenReturn(null);
        when(mtPowerRechargeMapper.selectFirstSalesRechargeByShopId(shop.getId())).thenReturn(null);
        when(salesRedemptionCodeService.requireResolvedCode("TX58J2")).thenReturn(resolvedCode);
        doAnswer(invocation -> {
            MtPowerRecharge recharge = invocation.getArgument(0);
            recharge.setId(101L);
            return 1;
        }).when(mtPowerRechargeMapper).insert(any(MtPowerRecharge.class));
        PowerPaymentCreateResultDTO payResult = new PowerPaymentCreateResultDTO();
        payResult.setMockEnabled(true);
        payResult.setRawPayload("{\"mock\":true}");
        when(mtPowerPayService.createRechargeOrder(any(MtPowerRecharge.class), eq(shop), eq("127.0.0.1")))
                .thenReturn(payResult);

        MtH5PowerRechargeCreateVO result = mtPowerRechargeService.createRecharge(shop, 10_000_000, "TX58J2", "127.0.0.1");

        ArgumentCaptor<MtPowerRecharge> captor = ArgumentCaptor.forClass(MtPowerRecharge.class);
        verify(mtPowerRechargeMapper).insert(captor.capture());
        MtPowerRecharge frozenRecharge = captor.getValue();
        assertEquals("TX58J2", frozenRecharge.getRedemptionCode());
        assertEquals("sales-1", frozenRecharge.getSalesUserId());
        assertEquals(30, frozenRecharge.getDefaultCommissionRatio());
        assertEquals("REDEMPTION_FIRST_RECHARGE", frozenRecharge.getDiscountContextType());
        assertEquals(10_000_000, frozenRecharge.getPowerAmount());
        assertEquals(10_000, frozenRecharge.getOriginalAmountFen());
        assertEquals(8_000, frozenRecharge.getTotalAmountFen());
        assertEquals(frozenRecharge.getRechargeNo(), result.getRechargeNo());
        assertEquals(Boolean.TRUE, result.getMockPayEnabled());
        assertTrue(frozenRecharge.getCommissionWindowStartAt() != null);
        verify(salesRedemptionCodeService).recordRechargeUse(
                eq(resolvedCode),
                eq(101L),
                eq(frozenRecharge.getRechargeNo()),
                eq(shop.getId()),
                eq(shop.getPoiId()),
                eq(shop.getPoiName())
        );
    }

    @Test
    void shouldFreezeFifteenPercentCommissionAfterNinetyDayWindow() {
        MtShop shop = buildShop(LocalDateTime.now().minusDays(10));
        MtPowerAccount account = new MtPowerAccount();
        account.setShopId(shop.getId());
        account.setTotalRecharge(1);
        MtPowerRecharge firstSalesRecharge = new MtPowerRecharge();
        firstSalesRecharge.setCreateTime(LocalDateTime.now().minusDays(91));
        firstSalesRecharge.setCommissionWindowStartAt(LocalDateTime.now().minusDays(91));
        ResolvedRedemptionCode resolvedCode = ResolvedRedemptionCode.builder()
                .id(2L)
                .code("AB12CD")
                .salesUserId("sales-2")
                .salesUserPhone("13900000000")
                .build();

        when(mtPowerAccountService.getOrCreateAccount(shop.getId())).thenReturn(account);
        when(mtPowerRechargeMapper.selectLatestByShopId(shop.getId())).thenReturn(null);
        when(mtPowerRechargeMapper.selectFirstSalesRechargeByShopId(shop.getId())).thenReturn(firstSalesRecharge);
        when(salesRedemptionCodeService.requireResolvedCode("AB12CD")).thenReturn(resolvedCode);
        doAnswer(invocation -> {
            MtPowerRecharge recharge = invocation.getArgument(0);
            recharge.setId(202L);
            return 1;
        }).when(mtPowerRechargeMapper).insert(any(MtPowerRecharge.class));
        PowerPaymentCreateResultDTO payResult = new PowerPaymentCreateResultDTO();
        payResult.setMockEnabled(true);
        payResult.setRawPayload("{\"mock\":true}");
        when(mtPowerPayService.createRechargeOrder(any(MtPowerRecharge.class), eq(shop), eq("127.0.0.1")))
                .thenReturn(payResult);

        mtPowerRechargeService.createRecharge(shop, 50_000_000, "AB12CD", "127.0.0.1");

        ArgumentCaptor<MtPowerRecharge> captor = ArgumentCaptor.forClass(MtPowerRecharge.class);
        verify(mtPowerRechargeMapper).insert(captor.capture());
        MtPowerRecharge frozenRecharge = captor.getValue();
        assertEquals(15, frozenRecharge.getDefaultCommissionRatio());
        assertEquals("REDEMPTION_REGULAR", frozenRecharge.getDiscountContextType());
        assertEquals(50_000, frozenRecharge.getOriginalAmountFen());
        assertEquals(40_000, frozenRecharge.getTotalAmountFen());
    }

    @Test
    void shouldAllowNewFirstRechargeWhenLatestRechargeIsPending() {
        MtShop shop = buildShop(LocalDateTime.now().minusDays(1));
        MtPowerAccount account = new MtPowerAccount();
        account.setShopId(shop.getId());
        account.setTotalRecharge(0);
        MtPowerRecharge pendingRecharge = buildRecharge();
        pendingRecharge.setPayStatus(1);

        when(mtPowerAccountService.getOrCreateAccount(shop.getId())).thenReturn(account);
        when(mtPowerRechargeMapper.selectLatestByShopId(shop.getId())).thenReturn(pendingRecharge);
        doAnswer(invocation -> {
            MtPowerRecharge recharge = invocation.getArgument(0);
            recharge.setId(303L);
            return 1;
        }).when(mtPowerRechargeMapper).insert(any(MtPowerRecharge.class));
        PowerPaymentCreateResultDTO payResult = new PowerPaymentCreateResultDTO();
        payResult.setMockEnabled(true);
        payResult.setRawPayload("{\"mock\":true}");
        when(mtPowerPayService.createRechargeOrder(any(MtPowerRecharge.class), eq(shop), eq("127.0.0.1")))
                .thenReturn(payResult);

        mtPowerRechargeService.createRecharge(shop, 10_000_000, null, "127.0.0.1");

        ArgumentCaptor<MtPowerRecharge> captor = ArgumentCaptor.forClass(MtPowerRecharge.class);
        verify(mtPowerRechargeMapper).insert(captor.capture());
        MtPowerRecharge newRecharge = captor.getValue();
        assertEquals("FIRST_RECHARGE", newRecharge.getDiscountContextType());
        assertEquals(10_000, newRecharge.getOriginalAmountFen());
        assertEquals(8_000, newRecharge.getTotalAmountFen());
    }

    @Test
    void shouldRejectSuccessfulPaymentWhenAmountDoesNotMatchRecharge() {
        MtPowerRecharge recharge = buildRecharge();
        PowerPaymentQueryResultDTO queryResult = buildSuccessfulPaymentResult(recharge);
        queryResult.setTotalAmountFen(recharge.getTotalAmountFen() - 1);

        when(mtPowerRechargeMapper.selectByRechargeNo(recharge.getRechargeNo())).thenReturn(recharge);
        when(mtPowerPayService.queryRechargeOrder(recharge)).thenReturn(queryResult);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtPowerRechargeService.getRechargeResult(recharge.getShopId(), recharge.getRechargeNo())
        );

        assertEquals("支付结果金额不匹配", exception.getMessage());
        verify(mtPowerRechargeMapper, never()).markSuccess(any(), any(), any(), any(), any());
        verify(mtPowerAccountService, never()).addPaidRecharge(any(), any());
    }

    @Test
    void shouldCreditAccountOnlyAfterPaymentResultMatchesRecharge() {
        MtPowerRecharge recharge = buildRecharge();
        MtPowerRecharge paidRecharge = buildRecharge();
        paidRecharge.setPayStatus(2);
        paidRecharge.setPaidAt(LocalDateTime.now());
        PowerPaymentQueryResultDTO queryResult = buildSuccessfulPaymentResult(recharge);
        MtPowerAccount account = new MtPowerAccount();
        account.setShopId(recharge.getShopId());
        account.setBalance(recharge.getPowerAmount());
        account.setTotalRecharge(1);

        when(mtPowerRechargeMapper.selectByRechargeNo(recharge.getRechargeNo())).thenReturn(recharge, paidRecharge);
        when(mtPowerPayService.queryRechargeOrder(recharge)).thenReturn(queryResult);
        when(mtPowerRechargeMapper.markSuccess(eq(recharge.getId()), eq(queryResult.getTransactionId()), eq(queryResult.getRawPayload()), any(), any()))
                .thenReturn(1);
        when(mtPowerAccountService.addPaidRecharge(recharge.getShopId(), recharge.getPowerAmount())).thenReturn(account);
        when(mtPowerRechargeMapper.selectById(recharge.getId())).thenReturn(paidRecharge);
        when(mtShopService.findById(recharge.getShopId())).thenReturn(buildShop(LocalDateTime.now()));

        mtPowerRechargeService.getRechargeResult(recharge.getShopId(), recharge.getRechargeNo());

        verify(mtPowerRechargeMapper).markSuccess(eq(recharge.getId()), eq(queryResult.getTransactionId()), eq(queryResult.getRawPayload()), any(), any());
        verify(mtPowerAccountService).addPaidRecharge(recharge.getShopId(), recharge.getPowerAmount());
        verify(mtPowerAccountService, never()).addAdminGrant(any(), any());
        verify(salesCommissionService).createSnapshotForPaidRecharge(eq(paidRecharge), any());
    }

    @Test
    void shouldGrantBonusForFirstRechargeAtLeastTenMillionPower() {
        MtPowerRecharge recharge = buildRecharge();
        MtPowerRecharge paidRecharge = buildRecharge();
        paidRecharge.setPayStatus(2);
        paidRecharge.setPaidAt(LocalDateTime.now());
        PowerPaymentQueryResultDTO queryResult = buildSuccessfulPaymentResult(recharge);
        MtPowerAccount accountAfterPaidRecharge = new MtPowerAccount();
        accountAfterPaidRecharge.setShopId(recharge.getShopId());
        accountAfterPaidRecharge.setBalance(recharge.getPowerAmount());
        accountAfterPaidRecharge.setTotalRecharge(recharge.getPowerAmount());
        MtPowerAccount accountAfterBonus = new MtPowerAccount();
        accountAfterBonus.setShopId(recharge.getShopId());
        accountAfterBonus.setBalance(11_000_000);
        accountAfterBonus.setTotalRecharge(recharge.getPowerAmount());
        accountAfterBonus.setTotalGrant(1_000_000);

        when(mtPowerRechargeMapper.selectByRechargeNo(recharge.getRechargeNo())).thenReturn(recharge, paidRecharge);
        when(mtPowerPayService.queryRechargeOrder(recharge)).thenReturn(queryResult);
        when(mtPowerRechargeMapper.markSuccess(eq(recharge.getId()), eq(queryResult.getTransactionId()), eq(queryResult.getRawPayload()), any(), any()))
                .thenReturn(1);
        when(mtPowerAccountService.addPaidRecharge(recharge.getShopId(), recharge.getPowerAmount()))
                .thenReturn(accountAfterPaidRecharge);
        when(mtPowerAccountService.addAdminGrant(recharge.getShopId(), 1_000_000)).thenReturn(accountAfterBonus);
        when(mtPowerRechargeMapper.selectById(recharge.getId())).thenReturn(paidRecharge);
        when(mtShopService.findById(recharge.getShopId())).thenReturn(buildShop(LocalDateTime.now()));

        mtPowerRechargeService.getRechargeResult(recharge.getShopId(), recharge.getRechargeNo());

        ArgumentCaptor<MtPowerGrant> grantCaptor = ArgumentCaptor.forClass(MtPowerGrant.class);
        verify(mtPowerGrantMapper).insert(grantCaptor.capture());
        MtPowerGrant grant = grantCaptor.getValue();
        assertEquals(recharge.getShopId(), grant.getShopId());
        assertEquals(1_000_000, grant.getPowerAmount());
        assertEquals("首充满额赠送", grant.getGrantReason());
        assertTrue(grant.getRemark().contains(recharge.getRechargeNo()));
        verify(mtPowerAccountService).addPaidRecharge(recharge.getShopId(), recharge.getPowerAmount());
        verify(mtPowerAccountService).addAdminGrant(recharge.getShopId(), 1_000_000);
    }

    private MtShop buildShop(LocalDateTime createTime) {
        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiId("poi-001");
        shop.setPoiName("测试门店");
        shop.setCreateTime(createTime);
        return shop;
    }

    private MtPowerRecharge buildRecharge() {
        MtPowerRecharge recharge = new MtPowerRecharge();
        recharge.setId(301L);
        recharge.setRechargeNo("PR202604270001");
        recharge.setShopId(1L);
        recharge.setPowerAmount(10_000_000);
        recharge.setTotalAmountFen(10_000);
        recharge.setPayStatus(1);
        recharge.setPaymentOrderNo("PWP202604270001");
        return recharge;
    }

    private PowerPaymentQueryResultDTO buildSuccessfulPaymentResult(MtPowerRecharge recharge) {
        PowerPaymentQueryResultDTO queryResult = new PowerPaymentQueryResultDTO();
        queryResult.setRechargeNo(recharge.getRechargeNo());
        queryResult.setPaymentOrderNo(recharge.getPaymentOrderNo());
        queryResult.setTransactionId("4200000000202604270001");
        queryResult.setAppId(powerProperties.getWechatPay().getAppId());
        queryResult.setMchId(powerProperties.getWechatPay().getMchId());
        queryResult.setTradeType("MWEB");
        queryResult.setTradeState("SUCCESS");
        queryResult.setTotalAmountFen(recharge.getTotalAmountFen());
        queryResult.setPayerAmountFen(recharge.getTotalAmountFen());
        queryResult.setCurrency("CNY");
        queryResult.setPayerCurrency("CNY");
        queryResult.setPayStatus(2);
        queryResult.setSuccess(true);
        queryResult.setFinished(true);
        queryResult.setRawPayload("{\"trade_state\":\"SUCCESS\"}");
        return queryResult;
    }
}
