package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtCommentReplySetting;
import com.gogoga.backend.entity.mt.MtCommentReplySettingHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.mapper.MtCommentReplySettingHistoryMapper;
import com.gogoga.backend.mapper.MtCommentReplySettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopOperationSettingsServiceTest {

    @Mock
    private MtShopMapper mtShopMapper;
    @Mock
    private MtCommentReplySettingMapper mtCommentReplySettingMapper;
    @Mock
    private MtCommentReplySettingHistoryMapper mtCommentReplySettingHistoryMapper;

    @InjectMocks
    private ShopOperationSettingsService service;

    @Test
    void buildSettingsEnablesBothSwitchesByDefaultWhenNoSettingExists() {
        when(mtCommentReplySettingMapper.selectByShopId(100L)).thenReturn(null);

        ShopOperationSettingsVO settings = service.buildSettings(100L);

        assertTrue(settings.getAutoReplyEnabled());
        assertTrue(settings.getNegativeCommentAppealEnabled());
    }

    @Test
    void buildSettingsPreservesSavedNegativeCommentAppealChoice() {
        MtCommentReplySetting saved = new MtCommentReplySetting();
        saved.setShopId(100L);
        saved.setAutoReplyEnabled(true);
        saved.setNegativeCommentAppealEnabled(false);
        when(mtCommentReplySettingMapper.selectByShopId(100L)).thenReturn(saved);

        ShopOperationSettingsVO settings = service.buildSettings(100L);

        assertTrue(settings.getAutoReplyEnabled());
        assertFalse(settings.getNegativeCommentAppealEnabled());
    }

    @Test
    void insufficientPowerDisablesBothSwitchesAndAppendsSystemHistory() {
        MtCommentReplySetting saved = new MtCommentReplySetting();
        saved.setId(7L);
        saved.setShopId(100L);
        saved.setAutoReplyEnabled(true);
        saved.setNegativeCommentAppealEnabled(true);
        when(mtCommentReplySettingMapper.selectByShopId(100L)).thenReturn(saved);
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(mtCommentReplySettingMapper.updateById(any())).thenReturn(1);
        when(mtCommentReplySettingHistoryMapper.selectMaxVersionNoByShopId(100L)).thenReturn(2);

        ShopOperationSettingsVO result = service.disableCommentProcessingForInsufficientPower(100L);

        assertFalse(result.getAutoReplyEnabled());
        assertFalse(result.getNegativeCommentAppealEnabled());
        ArgumentCaptor<MtCommentReplySetting> settingCaptor = ArgumentCaptor.forClass(MtCommentReplySetting.class);
        verify(mtCommentReplySettingMapper).updateById(settingCaptor.capture());
        assertFalse(settingCaptor.getValue().getAutoReplyEnabled());
        assertFalse(settingCaptor.getValue().getNegativeCommentAppealEnabled());
        ArgumentCaptor<MtCommentReplySettingHistory> historyCaptor =
                ArgumentCaptor.forClass(MtCommentReplySettingHistory.class);
        verify(mtCommentReplySettingHistoryMapper).insert(historyCaptor.capture());
        assertTrue(ShopOperationSettingsService.CHANGE_SOURCE_SYSTEM.equals(historyCaptor.getValue().getChangeSource()));
        assertTrue(ShopOperationSettingsService.CHANGE_REASON_POWER_BALANCE_NOT_ENOUGH
                .equals(historyCaptor.getValue().getChangeReason()));
    }

    @Test
    void insufficientPowerShutdownIsIdempotentWhenBothSwitchesAreAlreadyOff() {
        MtCommentReplySetting saved = new MtCommentReplySetting();
        saved.setId(7L);
        saved.setShopId(100L);
        saved.setAutoReplyEnabled(false);
        saved.setNegativeCommentAppealEnabled(false);
        when(mtCommentReplySettingMapper.selectByShopId(100L)).thenReturn(saved);

        ShopOperationSettingsVO result = service.disableCommentProcessingForInsufficientPower(100L);

        assertFalse(result.getAutoReplyEnabled());
        assertFalse(result.getNegativeCommentAppealEnabled());
        verify(mtShopMapper, never()).selectById(any());
        verify(mtCommentReplySettingMapper, never()).updateById(any());
        verify(mtCommentReplySettingHistoryMapper, never()).insert(any());
    }
}
