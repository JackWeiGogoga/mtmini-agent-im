package com.gogoga.backend.service;

import com.gogoga.backend.entity.SalesUserProfile;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.SalesUserProfileMapper;
import com.gogoga.backend.util.UserContext;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class SalesReceiptCodePreviewService {

    private static final String PREVIEW_PATH = "/api/system/sales/receipt-code/preview";

    private final SalesUserProfileMapper salesUserProfileMapper;
    private final CosStorageService cosStorageService;

    public String buildPreviewUrl(String salesUserId, SalesUserProfile profile) {
        if (StringUtils.isBlank(salesUserId) || !hasReceiptCode(profile)) {
            return null;
        }
        return PREVIEW_PATH + "?salesUserId=" + URLEncoder.encode(salesUserId, StandardCharsets.UTF_8);
    }

    public PreviewPayload loadPreview(String salesUserId) {
        String currentUserId = requireCurrentUserId();
        String targetSalesUserId = StringUtils.defaultIfBlank(StringUtils.trimToNull(salesUserId), currentUserId);
        if (!UserContext.isSuperAdmin() && !StringUtils.equals(currentUserId, targetSalesUserId)) {
            throw new BusinessException(403, "无权查看该收款码");
        }
        SalesUserProfile profile = salesUserProfileMapper.selectByUserId(targetSalesUserId);
        if (!hasReceiptCode(profile)) {
            throw new BusinessException(404, "收款码不存在");
        }
        if (StringUtils.isNotBlank(profile.getReceiptCodeObjectKey())) {
            CosStorageService.StoredObjectContent objectContent =
                    cosStorageService.loadReceiptCodeObject(profile.getReceiptCodeObjectKey());
            return PreviewPayload.builder()
                    .content(objectContent.getContent())
                    .contentType(objectContent.getContentType())
                    .fileName(objectContent.getFileName())
                    .build();
        }
        return PreviewPayload.builder()
                .redirectUrl(StringUtils.trimToNull(profile.getReceiptCodeUrl()))
                .build();
    }

    private boolean hasReceiptCode(SalesUserProfile profile) {
        return profile != null && (
                StringUtils.isNotBlank(profile.getReceiptCodeObjectKey())
                        || StringUtils.isNotBlank(profile.getReceiptCodeUrl())
        );
    }

    private String requireCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException("当前用户未登录");
        }
        return userId;
    }

    @Getter
    @Builder
    public static class PreviewPayload {

        private final byte[] content;

        private final String contentType;

        private final String fileName;

        private final String redirectUrl;
    }
}
