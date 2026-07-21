package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtImAutoReplySetting;
import com.gogoga.backend.entity.mt.MtImAutoReplySettingHistory;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtImAutoReplySettingHistoryMapper;
import com.gogoga.backend.mapper.MtImAutoReplySettingMapper;
import com.gogoga.backend.mapper.MtShopMapper;
import com.gogoga.backend.param.ShopOperationSettingsUpdateParam;
import com.gogoga.backend.vo.ShopOperationSettingsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private MtImAutoReplySettingMapper settingMapper;
    @Mock
    private MtImAutoReplySettingHistoryMapper historyMapper;

    @InjectMocks
    private ShopOperationSettingsService service;

    @Test
    void missingSettingDefaultsToDisabled() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);

        ShopOperationSettingsVO settings = service.buildSettings(100L);

        assertFalse(settings.getAutoReplyEnabled());
    }

    @Test
    void authorizationCanEnableAndRecordsAuthHistory() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(null);
        when(settingMapper.insert(any())).thenAnswer(invocation -> {
            MtImAutoReplySetting setting = invocation.getArgument(0);
            setting.setId(7L);
            return 1;
        });
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(null);

        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(true);
        ShopOperationSettingsVO result = service.updateSettings(
                100L, param, ShopOperationSettingsService.CHANGE_SOURCE_AUTH, "AUTH_IM_CONFIGURED");

        assertTrue(result.getAutoReplyEnabled());
        ArgumentCaptor<MtImAutoReplySettingHistory> history =
                ArgumentCaptor.forClass(MtImAutoReplySettingHistory.class);
        verify(historyMapper).insert(history.capture());
        assertEquals(ShopOperationSettingsService.CHANGE_SOURCE_AUTH, history.getValue().getChangeSource());
        assertEquals("AUTH_IM_CONFIGURED", history.getValue().getChangeReason());
        assertEquals(1, history.getValue().getVersionNo());
    }

    @Test
    void explicitH5ChangeRecordsH5History() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        MtImAutoReplySetting saved = enabledSetting();
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(saved);
        when(settingMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(3);

        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(false);
        service.updateSettings(100L, param, ShopOperationSettingsService.CHANGE_SOURCE_H5);

        ArgumentCaptor<MtImAutoReplySettingHistory> history =
                ArgumentCaptor.forClass(MtImAutoReplySettingHistory.class);
        verify(historyMapper).insert(history.capture());
        assertEquals(ShopOperationSettingsService.CHANGE_SOURCE_H5, history.getValue().getChangeSource());
        assertEquals(4, history.getValue().getVersionNo());
    }

    @Test
    void explicitAdminChangeRecordsAdminHistory() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());
        when(settingMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(1);
        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(false);

        service.updateSettings(100L, param, ShopOperationSettingsService.CHANGE_SOURCE_ADMIN);

        ArgumentCaptor<MtImAutoReplySettingHistory> history =
                ArgumentCaptor.forClass(MtImAutoReplySettingHistory.class);
        verify(historyMapper).insert(history.capture());
        assertEquals(ShopOperationSettingsService.CHANGE_SOURCE_ADMIN, history.getValue().getChangeSource());
    }

    @Test
    void rejectsUnknownShopWithoutReadingOrWritingAnotherShopSetting() {
        when(mtShopMapper.selectById(999L)).thenReturn(null);
        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(true);

        assertThrows(BusinessException.class,
                () -> service.updateSettings(999L, param, ShopOperationSettingsService.CHANGE_SOURCE_H5));

        verify(settingMapper, never()).selectByShopId(any());
        verify(settingMapper, never()).insert(any());
        verify(settingMapper, never()).updateById(any());
    }

    @Test
    void insufficientPowerDisablesSwitchWithSystemHistory() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());
        when(settingMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(2);

        ShopOperationSettingsVO result = service.disableImAutoReplyForInsufficientPower(100L);

        assertFalse(result.getAutoReplyEnabled());
        ArgumentCaptor<MtImAutoReplySettingHistory> history =
                ArgumentCaptor.forClass(MtImAutoReplySettingHistory.class);
        verify(historyMapper).insert(history.capture());
        assertEquals(ShopOperationSettingsService.CHANGE_SOURCE_SYSTEM, history.getValue().getChangeSource());
        assertEquals(ShopOperationSettingsService.CHANGE_REASON_POWER_BALANCE_NOT_ENOUGH,
                history.getValue().getChangeReason());
    }

    @Test
    void repeatedSameStateDoesNotWriteOrAppendHistory() {
        when(mtShopMapper.selectById(100L)).thenReturn(new MtShop());
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());
        ShopOperationSettingsUpdateParam param = new ShopOperationSettingsUpdateParam();
        param.setAutoReplyEnabled(true);

        ShopOperationSettingsVO result = service.updateSettings(
                100L, param, ShopOperationSettingsService.CHANGE_SOURCE_ADMIN);

        assertTrue(result.getAutoReplyEnabled());
        verify(settingMapper, never()).insert(any());
        verify(settingMapper, never()).updateById(any());
        verify(historyMapper, never()).insert(any());
    }

    @Test
    void customContactPhoneWinsOverShopPhones() {
        MtImAutoReplySetting setting = enabledSetting();
        setting.setCustomContactPhone("400-820-8820");
        when(settingMapper.selectByShopId(100L)).thenReturn(setting);

        assertEquals("400-820-8820", service.buildSettings(100L).getEffectiveContactPhone());
        assertEquals("400-820-8820", service.resolveEffectiveContactPhone(100L));
        // 自定义值命中时不必再查门店
        verify(mtShopMapper, never()).selectById(any());
    }

    @Test
    void fallsBackToShopPhoneThenContactPhone() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setPhone("020-88886666");
        shop.setContactPhone("13800000000");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());

        assertEquals("020-88886666", service.resolveEffectiveContactPhone(100L));

        shop.setPhone(null);
        assertEquals("13800000000", service.resolveEffectiveContactPhone(100L));

        shop.setContactPhone(null);
        assertNull(service.resolveEffectiveContactPhone(100L));
    }

    @Test
    void ignoresShopPhoneThatIsNotUsableAsANumber() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        // 美团同步与商家资料里的脏数据，不能直接拼进发给顾客的回复
        shop.setPhone("暂无");
        shop.setContactPhone("联系人张三 13800000000 工作日可拨");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());

        assertNull(service.resolveEffectiveContactPhone(100L));
    }

    @Test
    void updateCustomContactPhonePersistsAndRecordsHistory() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());
        when(settingMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(2);

        ShopOperationSettingsVO result = service.updateCustomContactPhone(
                100L, " 400-820-8820 ", ShopOperationSettingsService.CHANGE_SOURCE_H5);

        assertEquals("400-820-8820", result.getCustomContactPhone());
        // 开关不受联系方式变更影响
        assertTrue(result.getAutoReplyEnabled());
        ArgumentCaptor<MtImAutoReplySettingHistory> history =
                ArgumentCaptor.forClass(MtImAutoReplySettingHistory.class);
        verify(historyMapper).insert(history.capture());
        assertEquals(ShopOperationSettingsService.CHANGE_REASON_CONTACT_PHONE_UPDATED,
                history.getValue().getChangeReason());
        assertTrue(history.getValue().getAutoReplyEnabled());
    }

    @Test
    void blankCustomContactPhoneClearsConfigAndFallsBack() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        shop.setPhone("020-88886666");
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        MtImAutoReplySetting setting = enabledSetting();
        setting.setCustomContactPhone("400-820-8820");
        when(settingMapper.selectByShopId(100L)).thenReturn(setting);
        when(settingMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(1);

        ShopOperationSettingsVO result = service.updateCustomContactPhone(
                100L, "  ", ShopOperationSettingsService.CHANGE_SOURCE_H5);

        assertNull(result.getCustomContactPhone());
        assertEquals("020-88886666", result.getEffectiveContactPhone());
    }

    @Test
    void rejectsContactPhoneThatIsNotANumber() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        when(settingMapper.selectByShopId(100L)).thenReturn(enabledSetting());

        assertThrows(BusinessException.class, () -> service.updateCustomContactPhone(
                100L, "请加微信 abc", ShopOperationSettingsService.CHANGE_SOURCE_H5));
        assertThrows(BusinessException.class, () -> service.updateCustomContactPhone(
                100L, "123", ShopOperationSettingsService.CHANGE_SOURCE_H5));

        verify(settingMapper, never()).updateById(any());
        verify(historyMapper, never()).insert(any());
    }

    @Test
    void repeatedSameContactPhoneDoesNotWriteOrAppendHistory() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        when(mtShopMapper.selectById(100L)).thenReturn(shop);
        MtImAutoReplySetting setting = enabledSetting();
        setting.setCustomContactPhone("400-820-8820");
        when(settingMapper.selectByShopId(100L)).thenReturn(setting);

        service.updateCustomContactPhone(100L, "400-820-8820",
                ShopOperationSettingsService.CHANGE_SOURCE_H5);

        verify(settingMapper, never()).insert(any());
        verify(settingMapper, never()).updateById(any());
        verify(historyMapper, never()).insert(any());
    }

    private MtImAutoReplySetting enabledSetting() {
        MtImAutoReplySetting setting = new MtImAutoReplySetting();
        setting.setId(7L);
        setting.setShopId(100L);
        setting.setAutoReplyEnabled(true);
        return setting;
    }
}
