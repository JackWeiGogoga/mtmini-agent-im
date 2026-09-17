package com.gogoga.backend.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 在自动回复结尾固定拼接门店联系方式。
 *
 * <p>拼接发生在敏感词检测与改写之后：联系方式是商家自己配置或美团同步的门店数据，
 * 不是模型产出，没有改写空间；若放在检测前，含号码的正常回复会被反复改写甚至打回兜底话术。</p>
 */
@Service
@RequiredArgsConstructor
public class ImReplyContactSuffixService {

    private static final String SUFFIX_PREFIX = "-门店";

    private final ShopOperationSettingsService settingsService;

    /**
     * 为回复正文追加联系方式后缀；门店没有可用联系方式时原样返回。
     */
    public String append(Long shopId, String content) {
        return appendPhone(content, settingsService.resolveEffectiveContactPhone(shopId));
    }

    /**
     * 取门店最终生效的联系方式。
     *
     * <p>生成前要据此告诉模型“联系方式已配置”，生成后又要拿它拼后缀，两处必须是同一个值：
     * 若分别解析，中间商家改了配置就会出现模型说有、后缀没拼（或反之）的矛盾回复。</p>
     */
    public String resolveContactPhone(Long shopId) {
        return settingsService.resolveEffectiveContactPhone(shopId);
    }

    /**
     * 已知联系方式时的拼接逻辑，便于单测与复用。
     */
    public String appendPhone(String content, String contactPhone) {
        String body = StringUtils.stripToNull(content);
        String phone = StringUtils.trimToNull(contactPhone);
        if (body == null || phone == null) {
            return content;
        }
        // 模型可能已经从知识库里读到并写出了同一个号码，再拼一次会变成重复播报。
        if (containsSameNumber(body, phone)) {
            return body;
        }
        return body + "\n" + SUFFIX_PREFIX + phone;
    }

    /**
     * 按纯数字比较：正文里的号码可能带有和配置值不同的分隔符号。
     */
    private boolean containsSameNumber(String body, String phone) {
        String phoneDigits = digitsOf(phone);
        return !phoneDigits.isEmpty() && digitsOf(body).contains(phoneDigits);
    }

    private String digitsOf(String value) {
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
