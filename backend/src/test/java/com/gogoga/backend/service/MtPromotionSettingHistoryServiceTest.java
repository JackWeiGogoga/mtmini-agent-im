package com.gogoga.backend.service;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtPromotionSetting;
import com.gogoga.backend.entity.mt.MtPromotionSettingHistory;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.mapper.MtPromotionSettingHistoryMapper;
import com.gogoga.backend.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPromotionSettingHistoryServiceTest {

    @Mock
    private MtPromotionSettingHistoryMapper mtPromotionSettingHistoryMapper;

    @InjectMocks
    private MtPromotionSettingHistoryService mtPromotionSettingHistoryService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldRecordInitialStrategySnapshotWithOperatorAndNextVersion() {
        mockAdminUser();
        MtPromotionSetting next = buildSetting(1001L, true, true, "{\"rateMax\":\"10%\"}");
        when(mtPromotionSettingHistoryMapper.selectMaxVersionNoByShopId(1001L)).thenReturn(2);
        ArgumentCaptor<MtPromotionSettingHistory> captor = ArgumentCaptor.forClass(MtPromotionSettingHistory.class);

        mtPromotionSettingHistoryService.recordIfChanged(null, next, MtPromotionSettingHistoryService.SOURCE_ADMIN, "配置变更");

        verify(mtPromotionSettingHistoryMapper).insert(captor.capture());
        MtPromotionSettingHistory history = captor.getValue();
        assertEquals(1001L, history.getShopId());
        assertEquals(3, history.getVersionNo());
        assertEquals(true, history.getPromotionEnabled());
        assertEquals(true, history.getStrategyConfigured());
        assertEquals("{\"rateMax\":\"10%\"}", history.getStrategyConfigJson());
        assertEquals(
                MtPromotionSettingHistoryService.strategyConfigHash("{\"rateMax\":\"10%\"}"),
                history.getStrategyConfigHash()
        );
        assertEquals(MtPromotionSettingHistoryService.SOURCE_ADMIN, history.getChangeSource());
        assertEquals("admin-1", history.getOperatorUserId());
        assertEquals("管理员", history.getOperatorName());
        assertEquals("配置变更", history.getChangeReason());
    }

    @Test
    void shouldSkipWhenTrackedFieldsAreUnchanged() {
        MtPromotionSetting previous = buildSetting(1002L, true, true, "{\"rateMax\":\"10%\"}");
        MtPromotionSetting next = buildSetting(1002L, true, true, "{\"rateMax\":\"10%\"}");

        mtPromotionSettingHistoryService.recordIfChanged(previous, next, MtPromotionSettingHistoryService.SOURCE_H5, "无变化");

        verify(mtPromotionSettingHistoryMapper, never()).insert(any());
    }

    @Test
    void shouldRecordH5AndSystemSources() {
        MtPromotionSetting previous = buildSetting(1004L, true, true, "{\"rateMax\":\"10%\"}");
        MtPromotionSetting h5Next = buildSetting(1004L, true, true, "{\"rateMax\":\"9%\"}");
        MtPromotionSetting systemNext = buildSetting(1004L, false, true, "{\"rateMax\":\"9%\"}");
        when(mtPromotionSettingHistoryMapper.selectMaxVersionNoByShopId(1004L))
                .thenReturn(1)
                .thenReturn(2);
        ArgumentCaptor<MtPromotionSettingHistory> captor = ArgumentCaptor.forClass(MtPromotionSettingHistory.class);

        mtPromotionSettingHistoryService.recordIfChanged(
                previous,
                h5Next,
                MtPromotionSettingHistoryService.SOURCE_H5,
                "H5 保存"
        );
        mtPromotionSettingHistoryService.recordIfChanged(
                h5Next,
                systemNext,
                MtPromotionSettingHistoryService.SOURCE_SYSTEM,
                "余额不足"
        );

        verify(mtPromotionSettingHistoryMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<MtPromotionSettingHistory> histories = captor.getAllValues();
        assertEquals(MtPromotionSettingHistoryService.SOURCE_H5, histories.get(0).getChangeSource());
        assertEquals(2, histories.get(0).getVersionNo());
        assertEquals(MtPromotionSettingHistoryService.SOURCE_SYSTEM, histories.get(1).getChangeSource());
        assertEquals(3, histories.get(1).getVersionNo());
        assertEquals(false, histories.get(1).getPromotionEnabled());
    }

    @Test
    void shouldReturnEmptyHistoryForNullShopAndClampLimit() {
        assertEquals(List.of(), mtPromotionSettingHistoryService.listLatestByShopId(null, 20));

        mtPromotionSettingHistoryService.listLatestByShopId(1003L, 0);

        verify(mtPromotionSettingHistoryMapper).selectLatestByShopId(eq(1003L), eq(20));
    }

    @Test
    void shouldReturnNullHashForBlankStrategyConfig() {
        assertNull(MtPromotionSettingHistoryService.strategyConfigHash(" "));
    }

    private MtPromotionSetting buildSetting(Long shopId, boolean promotionEnabled, boolean strategyConfigured, String strategyConfigJson) {
        MtPromotionSetting setting = new MtPromotionSetting();
        setting.setId(shopId + 10);
        setting.setShopId(shopId);
        setting.setPromotionEnabled(promotionEnabled);
        setting.setStrategyConfigured(strategyConfigured);
        setting.setStrategyConfigJson(strategyConfigJson);
        return setting;
    }

    private void mockAdminUser() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setUserName("管理员");
        loginUser.setActivePortal(BackendPortalType.ADMIN);
        loginUser.setRoles(Set.of("admin"));
        UserContext.setLoginUser(loginUser);
    }
}
