package com.gogoga.backend.client.request;

import com.google.gson.reflect.TypeToken;
import com.meituan.sdk.MeituanRequest;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.annotations.ApiMeta;
import com.meituan.sdk.internal.utils.JsonUtil;
import com.meituan.sdk.model.wmoperNg.poi.wmoperngQueryPoiDetail.Data;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 美团店铺详情请求
 *
 * @author system
 * @since 2026-03-22
 */
@ApiMeta(
        path = "/wmoper/ng/poi/detail",
        businessId = 16,
        apiVersion = "10003",
        apiName = "wmoperng_query_poi_detail",
        needAuth = true
)
public class MtPoiDetailRequest implements MeituanRequest<List<Data>> {

    @Override
    public MeituanResponse<List<Data>> deserializeResponse(String response) {
        Type responseType = new TypeToken<MeituanResponse<List<Data>>>() {
        }.getType();
        return JsonUtil.fromJson(response, responseType);
    }

    @Override
    public String serializeToJson() {
        return null;
    }
}
