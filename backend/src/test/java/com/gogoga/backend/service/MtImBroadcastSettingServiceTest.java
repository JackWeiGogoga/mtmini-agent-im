package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImBroadcastSetting;
import com.gogoga.backend.entity.mt.MtImBroadcastSettingHistory;
import com.gogoga.backend.mapper.MtImBroadcastSettingHistoryMapper;
import com.gogoga.backend.mapper.MtImBroadcastSettingMapper;
import com.gogoga.backend.param.ImBroadcastSettingUpdateParam;
import com.gogoga.backend.vo.ImBroadcastSettingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MtImBroadcastSettingServiceTest {

    @Mock private MtImBroadcastSettingMapper settingMapper;
    @Mock private MtImBroadcastSettingHistoryMapper historyMapper;
    @Mock private SensitiveWordService sensitiveWordService;

    private MtImBroadcastSettingService service;

    @BeforeEach
    void setUp() {
        service = new MtImBroadcastSettingService(settingMapper, historyMapper, sensitiveWordService, new MtProperties());
    }

    private ImBroadcastSettingUpdateParam param(LocalTime sendTime) {
        ImBroadcastSettingUpdateParam param = new ImBroadcastSettingUpdateParam();
        param.setWeekDays(List.of(1, 3));
        param.setRepeatMode("EVERY");
        param.setSendTime(sendTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        param.setContent("下午茶买一送一");
        return param;
    }

    private MtImBroadcastSetting stored(boolean enabled) {
        MtImBroadcastSetting setting = new MtImBroadcastSetting();
        setting.setId(1L);
        setting.setShopId(100L);
        setting.setEnabled(enabled);
        setting.setWeekDays("1,3");
        setting.setRepeatMode("ONCE");
        setting.setSendTime(LocalTime.of(10, 0));
        setting.setContent("下午茶买一送一");
        setting.setTotalSentCount(2L);
        return setting;
    }

    @Test
    void skipsTodayWhenConfiguredTimeAlreadyPassed() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);

        service.saveSettings(100L, param(LocalTime.now().minusMinutes(5)), MtImBroadcastSettingService.CHANGE_SOURCE_H5);

        ArgumentCaptor<MtImBroadcastSetting> captor = ArgumentCaptor.forClass(MtImBroadcastSetting.class);
        verify(settingMapper).insert(captor.capture());
        assertEquals(LocalDate.now(), captor.getValue().getLastTriggerDate());
    }

    @Test
    void allowsTodayWhenConfiguredTimeIsStillAhead() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);

        service.saveSettings(100L, param(LocalTime.now().plusMinutes(30)), MtImBroadcastSettingService.CHANGE_SOURCE_H5);

        ArgumentCaptor<MtImBroadcastSetting> captor = ArgumentCaptor.forClass(MtImBroadcastSetting.class);
        verify(settingMapper).insert(captor.capture());
        assertNull(captor.getValue().getLastTriggerDate());
    }

    @Test
    void echoesLastConfigAfterOnceModeExpired() {
        when(settingMapper.selectByShopId(100L)).thenReturn(stored(false));

        ImBroadcastSettingVO vo = service.getSettings(100L);

        assertFalse(vo.getConfigured());
        assertEquals(List.of(1, 3), vo.getWeekDays());
        assertEquals("10:00:00", vo.getSendTime());
        assertEquals("下午茶买一送一", vo.getContent());
        assertEquals(2L, vo.getTotalSentCount());
    }

    @Test
    void reportsConfiguredWhenScheduleStillEnabled() {
        when(settingMapper.selectByShopId(100L)).thenReturn(stored(true));

        ImBroadcastSettingVO vo = service.getSettings(100L);

        assertTrue(vo.getConfigured());
        assertEquals("10:00:00", vo.getSendTime());
    }

    @Test
    void returnsEmptyViewWhenShopNeverConfigured() {
        when(settingMapper.selectByShopId(anyLong())).thenReturn(null);

        ImBroadcastSettingVO vo = service.getSettings(100L);

        assertFalse(vo.getConfigured());
        assertTrue(vo.getWeekDays().isEmpty());
        assertEquals(0L, vo.getTotalSentCount());
        assertNull(vo.getContent());
    }

    @Test
    void clearsScheduleOnDelete() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);

        service.deleteSettings(100L, MtImBroadcastSettingService.CHANGE_SOURCE_ADMIN);

        verify(settingMapper).clearSchedule(100L);
    }

    /** 「一次」失效与显式删除都是 enabled=0，运营侧必须能区分；删除会清空字段，据此判定。 */
    @Test
    void marksOnceCompletedWhenScheduleExpiredButFieldsRemain() {
        MtImBroadcastSetting expired = stored(false);
        expired.setLastTriggerDate(LocalDate.now());
        when(settingMapper.selectByShopId(100L)).thenReturn(expired);

        ImBroadcastSettingVO vo = service.getSettings(100L);

        assertEquals("ONCE_COMPLETED", vo.getStatus());
        assertEquals(LocalDate.now(), vo.getLastTriggerDate());
        assertFalse(vo.getConfigured());
    }

    @Test
    void marksNoneWhenScheduleWasExplicitlyDeleted() {
        MtImBroadcastSetting deleted = stored(false);
        deleted.setWeekDays("");
        deleted.setContent("");
        deleted.setLastTriggerDate(null);
        when(settingMapper.selectByShopId(100L)).thenReturn(deleted);

        assertEquals("NONE", service.getSettings(100L).getStatus());
    }

    @Test
    void marksActiveWhenScheduleStillRunning() {
        when(settingMapper.selectByShopId(100L)).thenReturn(stored(true));

        assertEquals("ACTIVE", service.getSettings(100L).getStatus());
    }

    @Test
    void marksNoneWhenShopNeverConfigured() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);

        assertEquals("NONE", service.getSettings(100L).getStatus());
    }

    @Test
    void recordsSaveHistoryWithFullContentAndSource() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(3);

        service.saveSettings(100L, param(LocalTime.of(9, 0)), MtImBroadcastSettingService.CHANGE_SOURCE_H5);

        ArgumentCaptor<MtImBroadcastSettingHistory> captor =
                ArgumentCaptor.forClass(MtImBroadcastSettingHistory.class);
        verify(historyMapper).insert(captor.capture());
        MtImBroadcastSettingHistory history = captor.getValue();
        assertEquals(4, history.getVersionNo());
        assertEquals("SAVE", history.getChangeType());
        assertEquals("H5", history.getChangeSource());
        assertEquals("下午茶买一送一", history.getContent());
        assertTrue(history.getEnabled());
    }

    /** 删除会清空业务表字段，历史行必须留住被删掉那一版的内容，否则记录毫无信息量。 */
    @Test
    void recordsDeletedConfigSnapshotInHistory() {
        when(settingMapper.selectByShopId(100L)).thenReturn(stored(true));
        when(historyMapper.selectMaxVersionNoByShopId(100L)).thenReturn(0);

        service.deleteSettings(100L, MtImBroadcastSettingService.CHANGE_SOURCE_ADMIN);

        ArgumentCaptor<MtImBroadcastSettingHistory> captor =
                ArgumentCaptor.forClass(MtImBroadcastSettingHistory.class);
        verify(historyMapper).insert(captor.capture());
        MtImBroadcastSettingHistory history = captor.getValue();
        assertEquals("DELETE", history.getChangeType());
        assertEquals("ADMIN", history.getChangeSource());
        assertEquals("1,3", history.getWeekDays());
        assertEquals("下午茶买一送一", history.getContent());
        assertFalse(history.getEnabled());
    }

    @Test
    void skipsHistoryWhenDeletingShopThatNeverConfigured() {
        when(settingMapper.selectByShopId(100L)).thenReturn(null);

        service.deleteSettings(100L, MtImBroadcastSettingService.CHANGE_SOURCE_ADMIN);

        verify(historyMapper, never()).insert(any());
    }
}
