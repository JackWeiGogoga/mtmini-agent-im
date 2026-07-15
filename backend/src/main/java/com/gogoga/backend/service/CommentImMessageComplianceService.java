package com.gogoga.backend.service;

import com.gogoga.backend.dto.CommentImComplianceResultDTO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评价匿名群聊专用内容合规校验。
 */
@Service
public class CommentImMessageComplianceService {

    private static final Map<String, List<String>> BLOCKED_TERMS = blockedTerms();
    private static final Pattern CONDITIONAL_COMPENSATION_PATTERN = Pattern.compile(
            "(?:核实|确认)(?:情况|信息|订单)?后[^，。！？；\\n]{0,12}补偿"
    );
    private static final List<String> UNCONDITIONAL_COMMITMENT_TERMS =
            List.of("一定", "肯定", "保证", "无条件", "直接", "立即");

    public CommentImComplianceResultDTO validate(String content) {
        if (StringUtils.isBlank(content)) {
            return new CommentImComplianceResultDTO(false, "EMPTY_CONTENT");
        }
        String normalized = removeAllowedConditionalCompensation(StringUtils.lowerCase(content));
        for (Map.Entry<String, List<String>> entry : BLOCKED_TERMS.entrySet()) {
            if (entry.getValue().stream().anyMatch(normalized::contains)) {
                return new CommentImComplianceResultDTO(false, entry.getKey());
            }
        }
        return new CommentImComplianceResultDTO(true, null);
    }

    private String removeAllowedConditionalCompensation(String content) {
        Matcher matcher = CONDITIONAL_COMPENSATION_PATTERN.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String conditionalPhrase = matcher.group();
            if (UNCONDITIONAL_COMMITMENT_TERMS.stream().anyMatch(conditionalPhrase::contains)) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(conditionalPhrase));
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(conditionalPhrase.replace("补偿", "")));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static Map<String, List<String>> blockedTerms() {
        Map<String, List<String>> terms = new LinkedHashMap<>();
        terms.put("COMPENSATION", List.of("补偿", "红包", "优惠券", "退款", "退钱", "赔付", "赔偿", "补发", "返现", "返款"));
        terms.put("REVIEW_INDUCEMENT", List.of("删评", "删除评价", "改评", "改差评", "修改评价", "好评", "五星"));
        terms.put("OFF_PLATFORM", List.of("微信", "vx", "v信", "qq", "支付宝", "二维码", "扫码", "加我", "私聊", "站外", "其他平台", "点击链接", "http://", "https://"));
        terms.put("HIGH_RISK_PERSONAL_DATA", List.of("验证码", "身份证", "银行卡", "支付密码", "密码"));
        terms.put("ABUSE_OR_ILLEGAL", List.of("傻逼", "白痴", "威胁", "弄死", "违法犯罪", "政治", "宗教", "平台垃圾"));
        return terms;
    }
}
