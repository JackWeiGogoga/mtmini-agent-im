package com.gogoga.backend.param;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class UserShopBatchBindParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "门店ID列表不能为空")
    @Size(max = 500, message = "单次最多批量绑定500个门店ID")
    private List<@Size(max = 64, message = "店铺ID长度不能超过64个字符") String> poiIds;

    @Size(max = 50, message = "目标用户ID长度不能超过50个字符")
    private String targetUserId;

    @Size(max = 20, message = "绑定域长度不能超过20个字符")
    private String bindingDomain;
}
