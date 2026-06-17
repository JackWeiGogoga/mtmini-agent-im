package com.gogoga.backend.service;

import com.meituan.sdk.MeituanClient;
import com.meituan.sdk.MeituanResponse;
import com.meituan.sdk.internal.exceptions.MtSdkException;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptRequest;
import com.meituan.sdk.model.wmoperNg.waimaiad.decrypt.DecryptResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 美团广告加密值解密服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtWaimaiAdDecryptService {

    /**
     * 真实联调确认过的“0”加密值。
     */
    private static final Set<String> KNOWN_ZERO_ENCRYPTED_VALUES = Set.of(
            "UdK7p6F4o8KhAXeeeni8Pw=="
    );

    private final MeituanClient meituanClient;

    public String decryptValue(Long shopId, String accessToken, String encryptedValue, String scene) {
        return decryptValue(shopId, accessToken, encryptedValue, scene, null);
    }

    public String decryptValue(
            Long shopId,
            String accessToken,
            String encryptedValue,
            String scene,
            Map<String, String> localCache
    ) {
        if (StringUtils.isBlank(encryptedValue)) {
            return encryptedValue;
        }

        String normalized = encryptedValue.trim();
        if (KNOWN_ZERO_ENCRYPTED_VALUES.contains(normalized)) {
            return "0";
        }
        if (localCache != null && localCache.containsKey(normalized)) {
            return localCache.get(normalized);
        }

        try {
            DecryptRequest request = new DecryptRequest();
            request.setEncryptedValue(normalized);
            MeituanResponse<DecryptResponse> response = meituanClient.invokeApi(request, accessToken);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("解密{}失败，shopId={}, response={}", scene, shopId, response);
                throw new IllegalStateException("解密" + scene + "失败");
            }

            DecryptResponse payload = response.getData();
            if (payload.getResult() == null || !Boolean.TRUE.equals(payload.getResult().getSuccess())) {
                log.warn("解密{}业务失败，shopId={}, result={}", scene, shopId, payload.getResult());
                throw new IllegalStateException("解密" + scene + "业务失败");
            }
            if (StringUtils.isBlank(payload.getValue())) {
                throw new IllegalStateException("解密" + scene + "为空");
            }

            String decryptedValue = payload.getValue().trim();
            if (localCache != null) {
                localCache.put(normalized, decryptedValue);
            }
            return decryptedValue;
        } catch (MtSdkException e) {
            log.error("解密{}异常，shopId={}", scene, shopId, e);
            throw new IllegalStateException("解密" + scene + "异常", e);
        }
    }
}
