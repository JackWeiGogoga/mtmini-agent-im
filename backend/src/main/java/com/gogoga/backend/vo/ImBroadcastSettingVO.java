package com.gogoga.backend.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * H5 群发配置视图。
 */
@Data
public class ImBroadcastSettingVO {

    private Long shopId;

    /**
     * 是否已配置定时群发。未配置时 H5 底部按钮为「发送 / 取消」，已配置时为「发送 / 删除」。
     */
    private Boolean configured;

    private Boolean enabled;

    /**
     * NONE=未配置或已删除；ACTIVE=生效中；ONCE_COMPLETED=「一次」已执行完成。
     *
     * <p>{@link #configured} 只回答「当前有没有在跑的定时任务」，供 H5 决定按钮形态；
     * 运营需要区分「从没配过」和「刚跑完一次性群发」，用这个字段。</p>
     */
    private String status;

    private String statusName;

    /**
     * 最后一次触发日期，ONCE_COMPLETED 时即执行日期。
     */
    private LocalDate lastTriggerDate;

    /**
     * 触发星期，1=周一 … 7=周日。
     */
    private List<Integer> weekDays;

    private String repeatMode;

    /**
     * HH:mm:ss。
     */
    private String sendTime;

    private String content;

    /**
     * 累计成功群发条数，首页「已群发N条」取此值。
     */
    private Long totalSentCount;

    /**
     * 当前门店权益是否包含群发能力。
     *
     * <p>为 false 时 H5 把群发入口置灰并提示升级；后端保存接口同样会拒绝，前端置灰只是体验层。</p>
     */
    private Boolean rightGranted;
}
