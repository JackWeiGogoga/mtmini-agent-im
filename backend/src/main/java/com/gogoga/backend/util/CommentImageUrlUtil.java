package com.gogoga.backend.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 评价图片 URL 解析工具。
 */
public final class CommentImageUrlUtil {

    private CommentImageUrlUtil() {
    }

    public static List<String> parseImageUrls(String raw, int maxCount) {
        if (StringUtils.isBlank(raw)) {
            return List.of();
        }
        String[] parts = raw.split("[,，\\n\\r\\t ]+");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                values.add(part);
            }
        }
        return parseImageUrls(values, maxCount);
    }

    public static List<String> parseImageUrls(List<String> rawUrls, int maxCount) {
        if (rawUrls == null || rawUrls.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(maxCount, 0);
        if (limit <= 0) {
            return List.of();
        }
        Set<String> urls = new LinkedHashSet<>();
        for (String rawUrl : rawUrls) {
            String url = StringUtils.trimToNull(rawUrl);
            if (url == null || !isHttpUrl(url)) {
                continue;
            }
            urls.add(url);
            if (urls.size() >= limit) {
                break;
            }
        }
        return List.copyOf(urls);
    }

    private static boolean isHttpUrl(String url) {
        String lower = StringUtils.lowerCase(url);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
