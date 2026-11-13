package com.gogoga.backend.service.sms;

import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.util.PhoneUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从门店联系电话字段中提取所有合法的中国大陆手机号。
 */
@Component
public class ShopContactPhoneParser {

    private static final Pattern CN_MOBILE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:(?:\\+?86)[\\s-]*)?(1[3-9](?:[\\s-]*\\d){9})(?!\\d)"
                    + "(?!\\s*(?:转|ext\\.?|分机|[-*#]\\s*\\d{1,6}(?!\\d)))",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 合并 contact_phone 与 phone，按首次出现顺序提取、标准化并去重。
     */
    public List<String> parse(MtShop shop) {
        if (shop == null) {
            return List.of();
        }
        return parse(shop.getContactPhone(), shop.getPhone());
    }

    public List<String> parse(String contactPhone, String phone) {
        Set<String> result = new LinkedHashSet<>();
        extract(contactPhone, result);
        extract(phone, result);
        return List.copyOf(result);
    }

    private void extract(String raw, Set<String> result) {
        if (StringUtils.isBlank(raw)) {
            return;
        }
        Matcher matcher = CN_MOBILE_PATTERN.matcher(raw);
        while (matcher.find()) {
            if (hasUnsupportedNumericPrefix(raw, matcher.start())) {
                continue;
            }
            String normalized = PhoneUtil.normalizeCnPhone(matcher.group(1));
            if (PhoneUtil.isValidCnPhone(normalized)) {
                result.add(normalized);
            }
        }
    }

    private boolean hasUnsupportedNumericPrefix(String raw, int matchStart) {
        int prefixStart = Math.max(0, matchStart - 12);
        String prefix = raw.substring(prefixStart, matchStart);
        boolean foreignCountryCode = prefix.matches("(?s).*\\+(?!86(?:[\\s-]*)$)\\d{1,3}[\\s-]*$");
        boolean landlineAreaCode = prefix.matches("(?s).*0\\d{2,3}[\\s-]+$");
        return foreignCountryCode || landlineAreaCode;
    }
}
