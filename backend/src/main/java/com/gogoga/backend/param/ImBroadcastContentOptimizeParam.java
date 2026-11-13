package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 群发文案优化入参。
 */
@Data
public class ImBroadcastContentOptimizeParam {

    /**
     * 待优化的文本框当前内容。上限放宽到 500，给用户在优化前先写长草稿的空间，
     * 最终产物仍会被收敛到群发文案的字数上限。
     */
    @NotBlank(message = "请先填写要优化的内容")
    @Size(max = 500, message = "待优化内容过长")
    private String content;
}
