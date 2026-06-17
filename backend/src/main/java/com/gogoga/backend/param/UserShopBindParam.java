package com.gogoga.backend.param;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户绑定门店请求参数
 */
@Data
public class UserShopBindParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "店铺ID不能为空")
    @Size(max = 64, message = "店铺ID长度不能超过64个字符")
    private String poiId;

    @Size(max = 50, message = "目标用户ID长度不能超过50个字符")
    private String targetUserId;

    @Size(max = 20, message = "绑定域长度不能超过20个字符")
    private String bindingDomain;
}
