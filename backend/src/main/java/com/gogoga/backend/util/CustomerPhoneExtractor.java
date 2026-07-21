package com.gogoga.backend.util;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从顾客消息正文里识别手机号，供日志页展示与一键复制。
 *
 * <p>只认中国大陆 11 位手机号：顾客留联系方式时给的就是手机号，放宽到座机/400
 * 会把订单号、金额、门牌号一起捞进来，日志页反而看不出哪个是真联系方式。</p>
 */
public final class CustomerPhoneExtractor {

    /**
     * 1[3-9] 开头共 11 位，容忍中间的空格与横线（顾客常写成 138-0013-8000）。
     *
     * <p>前后的 {@code (?<!\d)} / {@code (?!\d)} 是关键：订单号这类长数字串里也可能出现
     * 形如手机号的片段，没有边界保护就会把订单号截一段当成手机号。</p>
     */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d(?:[\\s-]?\\d){8}(?!\\d)");

    private CustomerPhoneExtractor() {
    }

    /**
     * 提取正文中的手机号，统一去掉分隔符后按出现顺序去重；无命中时返回空列表。
     */
    public static List<String> extract(String content) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }
        // 去重用 LinkedHashSet：同一个号码可能以不同分隔符重复出现，但展示只需要一次。
        LinkedHashSet<String> phones = new LinkedHashSet<>();
        Matcher matcher = MOBILE.matcher(content);
        while (matcher.find()) {
            phones.add(digitsOf(matcher.group()));
        }
        return phones.isEmpty() ? List.of() : new ArrayList<>(phones);
    }

    private static String digitsOf(String value) {
        StringBuilder digits = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
