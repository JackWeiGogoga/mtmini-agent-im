package com.gogoga.backend.service;

import com.gogoga.backend.config.CosProperties;
import com.gogoga.backend.exception.BusinessException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CosStorageService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final CosProperties properties;
    private final ObjectProvider<COSClient> cosClientProvider;

    public StoredObject uploadReceiptCode(String userId, MultipartFile file) {
        ensureEnabled();
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException("上传收款码失败，缺少用户标识");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择收款码图片");
        }
        validateReceiptCodeFile(file);

        String objectKey = buildReceiptCodeObjectKey(userId, file);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(StringUtils.trimToEmpty(file.getContentType()));

        try (InputStream inputStream = file.getInputStream()) {
            COSClient cosClient = requireCosClient();
            cosClient.putObject(new PutObjectRequest(properties.getBucket(), objectKey, inputStream, metadata));
            return StoredObject.builder()
                    .objectKey(objectKey)
                    .accessUrl(resolveReceiptCodeAccessUrl(objectKey, null))
                    .build();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败", e);
        } catch (Exception e) {
            log.error("上传收款码到 COS 失败, userId={}, objectKey={}", userId, objectKey, e);
            throw new BusinessException("上传收款码失败，请稍后重试", e);
        }
    }

    public StoredObject uploadObject(String keyPrefix, String ownerKey, MultipartFile file) {
        ensureEnabled();
        if (StringUtils.isBlank(ownerKey)) {
            throw new BusinessException("上传文件失败，缺少归属标识");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择文件");
        }
        String objectKey = buildObjectKey(keyPrefix, ownerKey, file);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(StringUtils.trimToEmpty(file.getContentType()));
        try (InputStream inputStream = file.getInputStream()) {
            requireCosClient().putObject(new PutObjectRequest(properties.getBucket(), objectKey, inputStream, metadata));
            return StoredObject.builder()
                    .objectKey(objectKey)
                    .accessUrl(resolveObjectAccessUrl(objectKey, null))
                    .build();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败", e);
        } catch (Exception e) {
            log.error("上传文件到 COS 失败, ownerKey={}, objectKey={}", ownerKey, objectKey, e);
            throw new BusinessException("上传文件失败，请稍后重试", e);
        }
    }

    public void deleteReceiptCodeObject(String objectKey) {
        if (StringUtils.isBlank(objectKey)) {
            return;
        }
        if (!properties.isEnabled()) {
            log.warn("COS 未启用，跳过删除收款码对象, objectKey={}", objectKey);
            return;
        }
        try {
            requireCosClient().deleteObject(properties.getBucket(), objectKey);
        } catch (Exception e) {
            log.error("删除 COS 收款码失败, objectKey={}", objectKey, e);
            throw new BusinessException("删除收款码文件失败，请稍后重试", e);
        }
    }

    public void deleteObject(String objectKey) {
        if (StringUtils.isBlank(objectKey)) {
            return;
        }
        if (!properties.isEnabled()) {
            log.warn("COS 未启用，跳过删除对象, objectKey={}", objectKey);
            return;
        }
        try {
            requireCosClient().deleteObject(properties.getBucket(), objectKey);
        } catch (Exception e) {
            log.error("删除 COS 对象失败, objectKey={}", objectKey, e);
            throw new BusinessException("删除文件失败，请稍后重试", e);
        }
    }

    public String resolveReceiptCodeAccessUrl(String objectKey, String fallbackUrl) {
        if (StringUtils.isBlank(objectKey)) {
            return StringUtils.trimToNull(fallbackUrl);
        }
        if (!properties.isEnabled()) {
            return StringUtils.trimToNull(fallbackUrl);
        }
        try {
            if (properties.getReceiptCode().isPrivateRead()) {
                return buildPresignedGetUrl(objectKey);
            }
            String customBase = StringUtils.trimToNull(properties.getPublicAccessUrlBase());
            if (customBase != null) {
                return customBase.endsWith("/") ? customBase + objectKey : customBase + "/" + objectKey;
            }
            URL url = requireCosClient().getObjectUrl(properties.getBucket(), objectKey);
            return url == null ? StringUtils.trimToNull(fallbackUrl) : url.toString();
        } catch (Exception e) {
            log.warn("生成 COS 收款码访问地址失败, objectKey={}", objectKey, e);
            return StringUtils.trimToNull(fallbackUrl);
        }
    }

    public String resolveObjectAccessUrl(String objectKey, String fallbackUrl) {
        if (StringUtils.isBlank(objectKey)) {
            return StringUtils.trimToNull(fallbackUrl);
        }
        if (!properties.isEnabled()) {
            return StringUtils.trimToNull(fallbackUrl);
        }
        try {
            String customBase = StringUtils.trimToNull(properties.getPublicAccessUrlBase());
            if (customBase != null) {
                return customBase.endsWith("/") ? customBase + objectKey : customBase + "/" + objectKey;
            }
            URL url = requireCosClient().getObjectUrl(properties.getBucket(), objectKey);
            return url == null ? StringUtils.trimToNull(fallbackUrl) : url.toString();
        } catch (Exception e) {
            log.warn("生成 COS 对象访问地址失败, objectKey={}", objectKey, e);
            return StringUtils.trimToNull(fallbackUrl);
        }
    }

    public StoredObjectContent loadReceiptCodeObject(String objectKey) {
        ensureEnabled();
        if (StringUtils.isBlank(objectKey)) {
            throw new BusinessException("收款码对象不存在");
        }
        try (COSObject cosObject = requireCosClient().getObject(new GetObjectRequest(properties.getBucket(), objectKey));
             InputStream inputStream = cosObject.getObjectContent()) {
            ObjectMetadata metadata = cosObject.getObjectMetadata();
            return StoredObjectContent.builder()
                    .content(inputStream.readAllBytes())
                    .contentType(resolveContentType(metadata, objectKey))
                    .fileName(resolveFileName(objectKey))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取 COS 收款码失败, objectKey={}", objectKey, e);
            throw new BusinessException(404, "收款码文件不存在或无法读取");
        }
    }

    public StoredObjectContent loadObject(String objectKey) {
        ensureEnabled();
        if (StringUtils.isBlank(objectKey)) {
            throw new BusinessException("对象不存在");
        }
        try (COSObject cosObject = requireCosClient().getObject(new GetObjectRequest(properties.getBucket(), objectKey));
             InputStream inputStream = cosObject.getObjectContent()) {
            ObjectMetadata metadata = cosObject.getObjectMetadata();
            return StoredObjectContent.builder()
                    .content(inputStream.readAllBytes())
                    .contentType(resolveContentType(metadata, objectKey))
                    .fileName(resolveFileName(objectKey))
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取 COS 对象失败, objectKey={}", objectKey, e);
            throw new BusinessException(404, "文件不存在或无法读取");
        }
    }

    private String buildPresignedGetUrl(String objectKey) {
        long expireSeconds = Math.max(properties.getReceiptCode().getAccessUrlExpireSeconds(), 60L);
        Date expiration = new Date(System.currentTimeMillis() + expireSeconds * 1000L);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                properties.getBucket(),
                objectKey,
                HttpMethodName.GET
        );
        request.setExpiration(expiration);
        return requireCosClient().generatePresignedUrl(request).toString();
    }

    private void validateReceiptCodeFile(MultipartFile file) {
        long maxBytes = properties.getReceiptCode().getMaxFileSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new BusinessException("收款码图片不能超过 " + properties.getReceiptCode().getMaxFileSize().toMegabytes() + "MB");
        }
        String contentType = StringUtils.lowerCase(StringUtils.trimToEmpty(file.getContentType()), Locale.ROOT);
        boolean allowed = properties.getReceiptCode().getAllowedContentTypes().stream()
                .filter(StringUtils::isNotBlank)
                .map(value -> StringUtils.lowerCase(StringUtils.trim(value), Locale.ROOT))
                .anyMatch(contentType::equals);
        if (!allowed) {
            throw new BusinessException("收款码图片仅支持 PNG、JPG、WEBP");
        }
    }

    private String buildReceiptCodeObjectKey(String userId, MultipartFile file) {
        String keyPrefix = StringUtils.defaultIfBlank(properties.getReceiptCode().getKeyPrefix(), "sales/receipt-code");
        keyPrefix = StringUtils.stripEnd(keyPrefix, "/");
        String extension = resolveExtension(file);
        return keyPrefix
                + "/"
                + userId
                + "/"
                + LocalDate.now().format(DATE_PATH_FORMATTER)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + extension;
    }

    private String buildObjectKey(String keyPrefix, String ownerKey, MultipartFile file) {
        String normalizedPrefix = StringUtils.defaultIfBlank(keyPrefix, "uploads");
        normalizedPrefix = StringUtils.stripEnd(normalizedPrefix, "/");
        String extension = resolveOriginalExtension(file);
        return normalizedPrefix
                + "/"
                + ownerKey
                + "/"
                + LocalDate.now().format(DATE_PATH_FORMATTER)
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + extension;
    }

    private String resolveOriginalExtension(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        String safeName = StringUtils.defaultString(originalFilename);
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < safeName.length() - 1) {
            return "." + StringUtils.lowerCase(safeName.substring(dotIndex + 1), Locale.ROOT);
        }
        return "";
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = StringUtils.lowerCase(StringUtils.trimToEmpty(file.getContentType()), Locale.ROOT);
        if ("image/png".equals(contentType)) {
            return ".png";
        }
        if ("image/webp".equals(contentType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private String resolveContentType(ObjectMetadata metadata, String objectKey) {
        String metadataContentType = metadata == null ? null : StringUtils.trimToNull(metadata.getContentType());
        if (metadataContentType != null) {
            return metadataContentType;
        }
        String lowerKey = StringUtils.lowerCase(StringUtils.defaultString(objectKey), Locale.ROOT);
        if (lowerKey.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lowerKey.endsWith(".webp")) {
            return "image/webp";
        }
        return MediaType.IMAGE_JPEG_VALUE;
    }

    private String resolveFileName(String objectKey) {
        int index = objectKey.lastIndexOf('/');
        return index >= 0 ? objectKey.substring(index + 1) : objectKey;
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException("对象存储未启用，请先配置 COS");
        }
    }

    private COSClient requireCosClient() {
        COSClient cosClient = cosClientProvider.getIfAvailable();
        if (cosClient == null) {
            throw new BusinessException("对象存储客户端未初始化，请检查 COS 配置");
        }
        return cosClient;
    }

    @Getter
    @Builder
    public static class StoredObject {

        private final String objectKey;

        private final String accessUrl;
    }

    @Getter
    @Builder
    public static class StoredObjectContent {

        private final byte[] content;

        private final String contentType;

        private final String fileName;
    }
}
