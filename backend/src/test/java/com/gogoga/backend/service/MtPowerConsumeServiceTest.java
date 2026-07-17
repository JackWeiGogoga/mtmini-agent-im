package com.gogoga.backend.service;

import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.PowerConsumeResultDTO;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPowerConsume;
import com.gogoga.backend.entity.mt.MtPowerConsumeDetail;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerAccountMapper;
import com.gogoga.backend.mapper.MtPowerConsumeDetailMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 门店算力消费服务测试
 *
 * @author system
 * @since 2026-03-23
 */
@ExtendWith(MockitoExtension.class)
class MtPowerConsumeServiceTest {

    @Mock
    private MtPowerAccountService mtPowerAccountService;

    @Mock
    private MtPowerAccountMapper mtPowerAccountMapper;

    @Mock
    private MtPowerConsumeMapper mtPowerConsumeMapper;

    @Mock
    private MtPowerConsumeDetailMapper mtPowerConsumeDetailMapper;

    @Mock
    private ShopOperationSettingsService shopOperationSettingsService;

    @InjectMocks
    private MtPowerConsumeService mtPowerConsumeService;

    @Test
    void shouldReturnDuplicateResultWhenRequestAlreadyExists() {
        MtPowerConsume existing = new MtPowerConsume();
        existing.setConsumeNo("PC001");
        existing.setBalanceAfter(180);
        when(mtPowerConsumeMapper.selectByShopIdAndRequestNo(1L, "REQ-1")).thenReturn(existing);

        PowerConsumeResultDTO result = mtPowerConsumeService.consume(buildRequest());

        assertTrue(result.isDuplicate());
        assertEquals("PC001", result.getConsumeNo());
        assertEquals(180, result.getBalanceAfter());
        verify(mtPowerAccountMapper, never()).deductBySource(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void shouldRejectConsumeWhenBalanceIsInsufficient() {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(50);
        account.setVersion(1);
        when(mtPowerConsumeMapper.selectByShopIdAndRequestNo(1L, "REQ-1")).thenReturn(null);
        when(mtPowerAccountMapper.selectByShopIdForUpdate(1L)).thenReturn(account);

        BusinessException exception = assertThrows(BusinessException.class, () -> mtPowerConsumeService.consume(buildRequest()));

        assertEquals("当前算力余额不足", exception.getMessage());
        verify(shopOperationSettingsService).disableCommentProcessingForInsufficientPower(1L);
        verify(mtPowerAccountMapper, never()).deductBySource(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), any());
        verify(mtPowerConsumeMapper, never()).insert(any());
    }

    @Test
    void shouldRecheckIdempotencyAfterAcquiringAccountLock() {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(300);
        account.setGrantBalance(100);
        account.setRechargeBalance(200);
        account.setVersion(2);
        MtPowerConsume existing = new MtPowerConsume();
        existing.setConsumeNo("PC-LOCKED");
        existing.setBalanceAfter(200);
        when(mtPowerConsumeMapper.selectByShopIdAndRequestNo(1L, "REQ-1"))
                .thenReturn(null, existing);
        when(mtPowerAccountMapper.selectByShopIdForUpdate(1L)).thenReturn(account);

        PowerConsumeResultDTO result = mtPowerConsumeService.consume(buildRequest());

        assertTrue(result.isDuplicate());
        assertEquals("PC-LOCKED", result.getConsumeNo());
        verify(mtPowerAccountService).ensureAccountInitialized(1L);
        verify(mtPowerAccountMapper).selectByShopIdForUpdate(1L);
        verify(mtPowerAccountMapper, never()).deductBySource(anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void shouldDeductGrantBalanceFirstAndCreateCrossSourceDetails() {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(300);
        account.setGrantBalance(80);
        account.setRechargeBalance(220);
        account.setVersion(2);
        when(mtPowerConsumeMapper.selectByShopIdAndRequestNo(1L, "REQ-1")).thenReturn(null);
        when(mtPowerAccountMapper.selectByShopIdForUpdate(1L)).thenReturn(account);
        when(mtPowerAccountMapper.deductBySource(eq(1L), eq(100), eq(80), eq(20), eq(2), any())).thenReturn(1);

        ArgumentCaptor<MtPowerConsume> consumeCaptor = ArgumentCaptor.forClass(MtPowerConsume.class);
        ArgumentCaptor<List<MtPowerConsumeDetail>> detailCaptor = detailListCaptor();
        when(mtPowerConsumeMapper.insert(consumeCaptor.capture())).thenAnswer(invocation -> {
            MtPowerConsume consume = consumeCaptor.getValue();
            consume.setId(99L);
            return 1;
        });

        PowerConsumeResultDTO result = mtPowerConsumeService.consume(buildRequest());

        assertTrue(!result.isDuplicate());
        assertEquals(200, result.getBalanceAfter());
        verify(mtPowerConsumeMapper).insert(any(MtPowerConsume.class));
        verify(mtPowerConsumeDetailMapper).insertBatch(detailCaptor.capture());
        List<MtPowerConsumeDetail> details = detailCaptor.getValue();
        assertEquals(2, details.size());
        assertEquals(MtPowerConsumeService.SOURCE_BALANCE_GRANT, details.get(0).getSourceBalanceType());
        assertEquals(80, details.get(0).getPowerAmount());
        assertEquals(80, details.get(0).getBalanceBefore());
        assertEquals(0, details.get(0).getBalanceAfter());
        assertEquals(MtPowerConsumeService.SOURCE_BALANCE_RECHARGE, details.get(1).getSourceBalanceType());
        assertEquals(20, details.get(1).getPowerAmount());
        assertEquals(220, details.get(1).getBalanceBefore());
        assertEquals(200, details.get(1).getBalanceAfter());
    }

    @Test
    void shouldCreateSingleDetailWhenRechargeBalanceOnly() {
        MtPowerAccount account = new MtPowerAccount();
        account.setBalance(300);
        account.setGrantBalance(0);
        account.setRechargeBalance(300);
        account.setVersion(2);
        when(mtPowerConsumeMapper.selectByShopIdAndRequestNo(1L, "REQ-1")).thenReturn(null);
        when(mtPowerAccountMapper.selectByShopIdForUpdate(1L)).thenReturn(account);
        when(mtPowerAccountMapper.deductBySource(eq(1L), eq(100), eq(0), eq(100), eq(2), any())).thenReturn(1);
        when(mtPowerConsumeMapper.insert(any(MtPowerConsume.class))).thenAnswer(invocation -> {
            MtPowerConsume consume = invocation.getArgument(0);
            consume.setId(100L);
            return 1;
        });

        ArgumentCaptor<List<MtPowerConsumeDetail>> detailCaptor = detailListCaptor();
        mtPowerConsumeService.consume(buildRequest());

        verify(mtPowerConsumeDetailMapper).insertBatch(detailCaptor.capture());
        List<MtPowerConsumeDetail> details = detailCaptor.getValue();
        assertEquals(1, details.size());
        assertEquals(MtPowerConsumeService.SOURCE_BALANCE_RECHARGE, details.get(0).getSourceBalanceType());
        assertEquals(100, details.get(0).getPowerAmount());
    }

    private PowerConsumeRequestDTO buildRequest() {
        PowerConsumeRequestDTO request = new PowerConsumeRequestDTO();
        request.setShopId(1L);
        request.setSourceType("AI_TASK");
        request.setSourceId("TASK-1");
        request.setRequestNo("REQ-1");
        request.setPowerAmount(100);
        request.setRemark("单元测试");
        return request;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<MtPowerConsumeDetail>> detailListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
