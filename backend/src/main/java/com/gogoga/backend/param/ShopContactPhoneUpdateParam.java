package com.gogoga.backend.param;

import lombok.Data;

/**
 * 门店自定义联系方式更新参数。
 *
 * <p>留空表示清除自定义配置，回退到门店电话。</p>
 */
@Data
public class ShopContactPhoneUpdateParam {

    private String customContactPhone;
}
