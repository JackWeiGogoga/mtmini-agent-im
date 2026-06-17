package com.gogoga.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.gogoga.backend.config.YouzanProperties;
import com.gogoga.backend.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 有赞 token 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YouzanTokenClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final YouzanProperties properties;

    public YouzanTokenResponse exchangeCode(String code) {
        Map<String, Object> payload = basePayload("authorization_code");
        payload.put("code", code);
        return postToken(payload);
    }

    public YouzanTokenResponse refreshToken(String refreshToken) {
        Map<String, Object> payload = basePayload("refresh_token");
        payload.put("refresh_token", refreshToken);
        return postToken(payload);
    }

    private Map<String, Object> basePayload(String authorizeType) {
        if (StringUtils.isAnyBlank(properties.getClientId(), properties.getClientSecret())) {
            throw new IllegalStateException("Youzan client credentials are not configured");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authorize_type", authorizeType);
        payload.put("client_id", properties.getClientId());
        payload.put("client_secret", properties.getClientSecret());
        return payload;
    }

    private YouzanTokenResponse postToken(Map<String, Object> payload) {
        String bodyJson = JsonUtil.toJson(payload);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(properties.getToken().getTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getToken().getTimeoutMillis()))
                .writeTimeout(Duration.ofMillis(properties.getToken().getTimeoutMillis()))
                .callTimeout(properties.getToken().getTimeoutMillis(), TimeUnit.MILLISECONDS)
                .build();
        Request request = new Request.Builder()
                .url(properties.getTokenUrl())
                .post(RequestBody.create(bodyJson, JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            YouzanTokenResponse parsed = parseResponse(responseBody);
            if (!response.isSuccessful()) {
                parsed.setSuccess(false);
                parsed.setCode(String.valueOf(response.code()));
                parsed.setMessage(StringUtils.defaultIfBlank(parsed.getMessage(), "有赞 token 接口请求失败"));
            }
            return parsed;
        } catch (IOException e) {
            log.warn("调用有赞 token 接口异常，url={}", properties.getTokenUrl(), e);
            YouzanTokenResponse result = new YouzanTokenResponse();
            result.setSuccess(false);
            result.setCode("NETWORK_ERROR");
            result.setMessage(e.getMessage());
            return result;
        }
    }

    private YouzanTokenResponse parseResponse(String responseBody) {
        YouzanTokenResponse result = new YouzanTokenResponse();
        result.setRawResponseJson(responseBody);
        JsonNode root = JsonUtil.readTree(responseBody);
        if (root == null) {
            result.setSuccess(false);
            result.setCode("INVALID_JSON");
            result.setMessage("有赞 token 响应不是合法 JSON");
            return result;
        }

        JsonNode data = root.hasNonNull("data") && root.get("data").isObject() ? root.get("data") : root;
        result.setAccessToken(text(data, "access_token", "accessToken"));
        result.setRefreshToken(text(data, "refresh_token", "refreshToken"));
        result.setScope(text(data, "scope"));
        result.setAuthorityId(text(data, "authority_id", "authorityId", "authorize_id", "authorizeId"));
        result.setKdtId(firstNonNull(longValue(data, "kdt_id", "kdtId"), parseLong(result.getAuthorityId())));
        applyAccessTokenExpiry(result, data);
        applyRefreshTokenExpiry(result, data);
        result.setCode(text(root, "code", "error_code", "errorCode"));
        result.setMessage(text(root, "message", "msg", "error_msg", "errorMsg"));

        boolean explicitSuccess = !root.has("success") || root.get("success").asBoolean(true);
        boolean hasToken = StringUtils.isNotBlank(result.getAccessToken())
                && StringUtils.isNotBlank(result.getRefreshToken());
        result.setSuccess(explicitSuccess && hasToken);
        if (!result.isSuccess() && StringUtils.isBlank(result.getMessage())) {
            result.setMessage("有赞 token 响应缺少 access_token 或 refresh_token");
        }
        return result;
    }

    private void applyAccessTokenExpiry(YouzanTokenResponse result, JsonNode data) {
        Long expires = longValue(data, "expires", "expires_at", "expiresAt");
        if (isEpochMillis(expires)) {
            result.setExpiresAtMillis(expires);
        } else if (expires != null) {
            result.setExpiresIn(expires);
        }
        result.setExpiresIn(firstNonNull(result.getExpiresIn(), longValue(data, "expires_in", "expiresIn")));
    }

    private void applyRefreshTokenExpiry(YouzanTokenResponse result, JsonNode data) {
        Long refreshExpires = longValue(data, "refresh_expires", "refresh_expires_at", "refreshExpiresAt");
        if (isEpochMillis(refreshExpires)) {
            result.setRefreshExpiresAtMillis(refreshExpires);
        } else if (refreshExpires != null) {
            result.setRefreshExpiresIn(refreshExpires);
        }
        result.setRefreshExpiresIn(firstNonNull(
                result.getRefreshExpiresIn(),
                longValue(data, "refresh_expires_in", "refreshExpiresIn")
        ));
    }

    private boolean isEpochMillis(Long value) {
        return value != null && value > 100_000_000_000L;
    }

    private String text(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private Long longValue(JsonNode node, String... names) {
        String value = text(node, names);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }
}
