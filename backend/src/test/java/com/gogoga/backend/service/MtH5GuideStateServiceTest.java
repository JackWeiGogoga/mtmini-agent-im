package com.gogoga.backend.service;

import com.gogoga.backend.constant.MtH5GuideCodeConstant;
import com.gogoga.backend.entity.mt.MtH5GuideState;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtH5GuideStateMapper;
import com.gogoga.backend.vo.MtH5GuideStateVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H5 引导状态服务测试
 *
 * @author system
 * @since 2026-05-12
 */
@ExtendWith(MockitoExtension.class)
class MtH5GuideStateServiceTest {

    @Mock
    private MtH5ShopService mtH5ShopService;

    @Mock
    private MtH5GuideStateMapper mtH5GuideStateMapper;

    @InjectMocks
    private MtH5GuideStateService mtH5GuideStateService;

    @Test
    void shouldReturnIncompleteWhenStateDoesNotExist() {
        when(mtH5GuideStateMapper.selectByShopIdAndGuideCode(1L, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE))
                .thenReturn(null);

        MtH5GuideStateVO state = mtH5GuideStateService.getGuideState(1L, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);

        assertEquals(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE, state.getGuideCode());
        assertFalse(state.getCompleted());
        assertNull(state.getCompletedAt());
    }

    @Test
    void shouldReturnCompletedState() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 5, 12, 10, 30);
        MtH5GuideState stored = new MtH5GuideState();
        stored.setShopId(1L);
        stored.setGuideCode(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);
        stored.setCompleted(true);
        stored.setCompletedAt(completedAt);
        when(mtH5GuideStateMapper.selectByShopIdAndGuideCode(1L, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE))
                .thenReturn(stored);

        MtH5GuideStateVO state = mtH5GuideStateService.getGuideState(1L, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);

        assertTrue(state.getCompleted());
        assertEquals(completedAt, state.getCompletedAt());
    }

    @Test
    void shouldCompleteCurrentShopGuideIdempotently() {
        MtShop shop = new MtShop();
        shop.setId(8L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        MtH5GuideState stored = new MtH5GuideState();
        stored.setShopId(8L);
        stored.setGuideCode(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);
        stored.setCompleted(true);
        stored.setCompletedAt(LocalDateTime.of(2026, 5, 12, 11, 0));
        when(mtH5GuideStateMapper.selectByShopIdAndGuideCode(8L, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE))
                .thenReturn(stored);

        MtH5GuideStateVO state = mtH5GuideStateService.completeCurrentGuide(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);

        assertTrue(state.getCompleted());
        verify(mtH5GuideStateMapper).upsertCompleted(
                org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldRejectUnsupportedGuideCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtH5GuideStateService.completeCurrentGuide("UNKNOWN_GUIDE")
        );

        assertEquals(400, exception.getCode());
        verify(mtH5GuideStateMapper, never()).upsertCompleted(any(), any(), any());
    }

    @Test
    void shouldPropagateCurrentShopLoginFailureWithoutWriting() {
        BusinessException loginException = new BusinessException(401, "请先登录门店账号");
        when(mtH5ShopService.getCurrentShop()).thenThrow(loginException);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> mtH5GuideStateService.completeCurrentGuide(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE)
        );

        assertEquals(401, exception.getCode());
        verify(mtH5GuideStateMapper, never()).upsertCompleted(any(), any(), any());
    }

    @Test
    void shouldUseCurrentShopWhenReadingCurrentGuideState() {
        MtShop shop = new MtShop();
        shop.setId(9L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        when(mtH5GuideStateMapper.selectByShopIdAndGuideCode(9L, MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE))
                .thenReturn(null);

        mtH5GuideStateService.getCurrentGuideState(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE);

        ArgumentCaptor<Long> shopIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mtH5GuideStateMapper).selectByShopIdAndGuideCode(
                shopIdCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(MtH5GuideCodeConstant.CURRENT_HOME_FIRST_AUTH_GUIDE)
        );
        assertEquals(9L, shopIdCaptor.getValue());
    }
}
