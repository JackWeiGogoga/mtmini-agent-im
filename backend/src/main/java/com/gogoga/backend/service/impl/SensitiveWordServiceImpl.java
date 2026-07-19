package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.config.SensitiveWordsProperties;
import com.gogoga.backend.dto.SensitiveWordHitDTO;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 与具体评价或 IM 业务无关的敏感词检测服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private static final String DEFAULT_SEVERITY = "MEDIUM";

    private final MtProperties mtProperties;
    private final SensitiveWordsProperties sensitiveWordsProperties;

    @Override
    public SensitiveWordHitDTO detect(String text) {
        SensitiveWordHitDTO result = new SensitiveWordHitDTO();
        MtProperties.SensitiveWord config = mtProperties.getSensitiveWord();
        if (config == null || !config.isEnabled() || StringUtils.isBlank(text)) {
            return result;
        }
        String normalizedText = normalize(text);
        if (StringUtils.isBlank(normalizedText)) {
            return result;
        }
        Set<String> categories = new LinkedHashSet<>();
        List<String> severities = new ArrayList<>();
        int hitCount = 0;
        for (SensitiveWordEntry entry : enabledEntries()) {
            if (StringUtils.isBlank(entry.getNormalizedWord())) {
                continue;
            }
            if (normalizedText.contains(entry.getNormalizedWord())) {
                hitCount++;
                categories.add(entry.getCategoryName());
                severities.add(entry.getSeverity());
            }
        }
        if (hitCount <= 0) {
            return result;
        }
        result.setHit(true);
        result.setCategories(new ArrayList<>(categories));
        result.setHitCount(hitCount);
        result.setHighestSeverity(severities.stream()
                .min(Comparator.comparingInt(this::severityRank))
                .orElse(DEFAULT_SEVERITY));
        return result;
    }

    @Override
    public void validateKnowledgeText(Long shopId, String sourceType, String text) {
        SensitiveWordHitDTO hit = detect(text);
        if (!hit.isHit()) {
            return;
        }
        log.warn("商户知识敏感词拦截，shopId={}, sourceType={}, categories={}, hitCount={}",
                shopId, sourceType, hit.getCategories(), hit.getHitCount());
        throw new BusinessException(toSafeMessage(hit));
    }

    @Override
    public String toSafeMessage(SensitiveWordHitDTO hit) {
        if (hit == null || !hit.isHit()) {
            return "内容未命中敏感词";
        }
        return "内容命中敏感词类别：" + StringUtils.join(hit.getCategories(), "、");
    }

    private List<SensitiveWordEntry> enabledEntries() {
        List<SensitiveWordEntry> entries = new ArrayList<>();
        if (sensitiveWordsProperties == null || sensitiveWordsProperties.getCategories() == null) {
            return entries;
        }
        for (SensitiveWordsProperties.Category category : sensitiveWordsProperties.getCategories()) {
            if (category == null || !category.isEnabled() || category.getWords() == null) {
                continue;
            }
            String categoryName = StringUtils.defaultIfBlank(category.getName(), category.getCode());
            String categorySeverity = StringUtils.defaultIfBlank(category.getSeverity(), DEFAULT_SEVERITY);
            for (SensitiveWordsProperties.Word word : category.getWords()) {
                if (word == null || !word.isEnabled() || StringUtils.isBlank(word.getText())) {
                    continue;
                }
                entries.add(new SensitiveWordEntry(
                        categoryName,
                        StringUtils.upperCase(StringUtils.defaultIfBlank(word.getSeverity(), categorySeverity), Locale.ROOT),
                        normalize(word.getText())
                ));
            }
        }
        return entries;
    }

    private String normalize(String text) {
        String normalized = Normalizer.normalize(StringUtils.defaultString(text), Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                continue;
            }
            int type = Character.getType(ch);
            if (type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.DASH_PUNCTUATION
                    || type == Character.START_PUNCTUATION
                    || type == Character.END_PUNCTUATION
                    || type == Character.INITIAL_QUOTE_PUNCTUATION
                    || type == Character.FINAL_QUOTE_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION
                    || type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL
                    || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_SYMBOL) {
                continue;
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }

    private int severityRank(String severity) {
        return switch (StringUtils.upperCase(StringUtils.defaultString(severity), Locale.ROOT)) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            default -> 3;
        };
    }

    @Value
    private static class SensitiveWordEntry {
        String categoryName;
        String severity;
        String normalizedWord;
    }
}
