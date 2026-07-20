package com.gogoga.backend.vo;

import lombok.Data;

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
}
