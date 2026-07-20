package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * H5 群发配置保存参数。
 */
@Data
public class ImBroadcastSettingUpdateParam {

    /**
     * 触发星期，1=周一 … 7=周日。
     */
    @NotEmpty(message = "请至少选择一个发送日期")
    private List<Integer> weekDays;

    /**
     * EVERY=每次（每周重复）；ONCE=一次（发送后失效）。
     */
    @NotBlank(message = "请选择发送频率")
    private String repeatMode;

    /**
     * 触发时间，HH:mm:ss。
     */
    @NotBlank(message = "请选择发送时间")
    private String sendTime;

    @NotBlank(message = "请填写发送的内容")
    @Size(max = 100, message = "发送内容最多100字")
    private String content;
}
