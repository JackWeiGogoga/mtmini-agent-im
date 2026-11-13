package com.gogoga.backend.service;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtImBroadcastSetting;
import com.gogoga.backend.entity.mt.MtImBroadcastTask;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.mapper.MtImBroadcastSettingMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MtImBroadcastScheduleServiceTest {

    @Mock private MtImBroadcastSettingMapper settingMapper;
    @Mock private MtImBroadcastTaskMapper taskMapper;
    @Mock private MtAsyncTaskService asyncTaskService;

    private MtProperties mtProperties;
    private MtImBroadcastScheduleService service;

    @BeforeEach
    void setUp() {
        mtProperties = new MtProperties();
        mtProperties.getImBroadcast().setEnabled(true);
        mtProperties.getImBroadcast().setTriggerJitterSeconds(0);
        service = new MtImBroadcastScheduleService(mtProperties, settingMapper, taskMapper, asyncTaskService);
    }

    private MtImBroadcastSetting setting(String weekDays, LocalTime sendTime, String repeatMode) {
        MtImBroadcastSetting setting = new MtImBroadcastSetting();
        setting.setId(1L);
        setting.setShopId(100L);
        setting.setEnabled(true);
        setting.setWeekDays(weekDays);
        setting.setSendTime(sendTime);
        setting.setRepeatMode(repeatMode);
        setting.setContent("下午茶买一送一");
        return setting;
    }

    private String todayWeekDay() {
        return String.valueOf(LocalDate.now().getDayOfWeek().getValue());
    }

    private String otherWeekDay() {
        int today = LocalDate.now().getDayOfWeek().getValue();
        return String.valueOf(today == 7 ? 1 : today + 1);
    }

    @Test
    void createsTaskAndEnqueuesPlanWhenDueAndWeekDayMatches() {
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(setting(todayWeekDay(), LocalTime.MIDNIGHT, "EVERY")))
                .thenReturn(List.of());
        when(settingMapper.markTriggered(anyLong(), any(), any())).thenReturn(1);

        service.dispatchDueBroadcasts();

        ArgumentCaptor<MtImBroadcastTask> captor = ArgumentCaptor.forClass(MtImBroadcastTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getShopId());
        assertEquals("PENDING", captor.getValue().getStatus());
        verify(asyncTaskService).enqueueTask(
                eq(MtAsyncTaskTypeEnum.IM_BROADCAST_PLAN), any(), eq("100"), any(), any(), anyInt());
    }

    @Test
    void skipsWhenWeekDayDoesNotMatch() {
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(setting(otherWeekDay(), LocalTime.MIDNIGHT, "EVERY")))
                .thenReturn(List.of());

        service.dispatchDueBroadcasts();

        verify(taskMapper, never()).insert(any());
    }

    @Test
    void skipsBeforeConfiguredSendTime() {
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(setting(todayWeekDay(), LocalTime.MAX, "EVERY")))
                .thenReturn(List.of());

        service.dispatchDueBroadcasts();

        verify(taskMapper, never()).insert(any());
    }

    @Test
    void skipsWhenAlreadyTriggeredToday() {
        MtImBroadcastSetting alreadyTriggered = setting(todayWeekDay(), LocalTime.MIDNIGHT, "EVERY");
        alreadyTriggered.setLastTriggerDate(LocalDate.now());
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(alreadyTriggered))
                .thenReturn(List.of());

        service.dispatchDueBroadcasts();

        verify(taskMapper, never()).insert(any());
    }

    @Test
    void skipsWhenAnotherInstanceWinsTriggerRace() {
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(setting(todayWeekDay(), LocalTime.MIDNIGHT, "EVERY")))
                .thenReturn(List.of());
        // 并发实例已抢先更新触发水位。
        when(settingMapper.markTriggered(anyLong(), any(), any())).thenReturn(0);

        service.dispatchDueBroadcasts();

        verify(taskMapper, never()).insert(any());
    }

    @Test
    void disablesSettingImmediatelyForOnceMode() {
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(setting(todayWeekDay(), LocalTime.MIDNIGHT, "ONCE")))
                .thenReturn(List.of());
        when(settingMapper.markTriggered(anyLong(), any(), any())).thenReturn(1);

        service.dispatchDueBroadcasts();

        verify(settingMapper).disable(1L);
    }

    @Test
    void keepsSettingEnabledForEveryMode() {
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(setting(todayWeekDay(), LocalTime.MIDNIGHT, "EVERY")))
                .thenReturn(List.of());
        when(settingMapper.markTriggered(anyLong(), any(), any())).thenReturn(1);

        service.dispatchDueBroadcasts();

        verify(settingMapper, never()).disable(anyLong());
    }

    @Test
    void doesNotScanWhenGlobalSwitchDisabled() {
        mtProperties.getImBroadcast().setEnabled(false);

        service.dispatchDueBroadcasts();

        verify(settingMapper, never()).selectEnabledAfterId(anyLong(), anyInt());
    }

    @Test
    void continuesScanWhenSingleShopTriggerFails() {
        MtImBroadcastSetting failing = setting(todayWeekDay(), LocalTime.MIDNIGHT, "EVERY");
        MtImBroadcastSetting healthy = setting(todayWeekDay(), LocalTime.MIDNIGHT, "EVERY");
        healthy.setId(2L);
        healthy.setShopId(200L);
        when(settingMapper.selectEnabledAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(failing, healthy))
                .thenReturn(List.of());
        when(settingMapper.markTriggered(eq(1L), any(), any())).thenThrow(new IllegalStateException("db down"));
        when(settingMapper.markTriggered(eq(2L), any(), any())).thenReturn(1);

        service.dispatchDueBroadcasts();

        verify(taskMapper).insert(any());
    }
}
