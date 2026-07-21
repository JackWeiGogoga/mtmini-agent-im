package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtShop;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把门店基础信息（地址、营业时间）拼成注入模型的事实块。
 *
 * <p>这些字段来自美团同步，命中率比知识库检索稳定，可以直接回答"你们在哪""几点关门"，
 * 避免系统里明明有数据、模型却按"禁止编造"规则一律推给人工客服。</p>
 *
 * <p>字段为空时整行省略而不是写"无"：写"无"会被模型读成"这家店没有营业时间"，
 * 省略配合块尾的兜底说明，才落到系统提示词里"按不知道处理"的分支。</p>
 */
@Service
public class ShopBasicInfoPromptService {

    private static final String HEADER =
            "以下是当前门店的基础信息，来自平台同步数据，可作为事实使用；如与门店知识片段冲突，以知识片段为准：";
    private static final String FOOTER =
            "未在上面列出的门店信息，一律按“系统中没有该信息”处理，不得推断或补全。"
                    + "地址与营业时间存在同步延迟，回答时请提示以平台页面展示为准。";

    /** 顾客问联系方式时的口径；仅在门店确实有可用号码时才加。 */
    private static final String CONTACT_NOTICE =
            "顾客询问联系方式时：门店联系方式已配置，系统会自动附在你回复的末尾，"
                    + "你只需说明可拨打末尾的门店电话，不要自己写出号码，更不得回答门店没有联系方式。"
                    + "本条关于联系方式的说明优先于其他规则。";

    /** 同步字段是任意文本，超长时截断，避免挤占上下文。 */
    private static final int MAX_FIELD_LENGTH = 120;

    /**
     * 构建门店基础信息块；没有任何可用字段时返回空串。
     *
     * @param hasContactPhone 门店是否有可用联系方式。这里只传布尔值不传号码：号码由
     *                        {@link ImReplyContactSuffixService} 在敏感词改写之后确定性拼接，
     *                        不经过模型就不会被改写或漏位；但模型必须知道它存在，否则会答
     *                        “没有联系方式”，再被后缀拼上号码，出现自相矛盾的回复。
     */
    public String build(MtShop shop, boolean hasContactPhone) {
        List<String> lines = new ArrayList<>(3);
        if (shop != null) {
            String address = sanitize(shop.getAddress());
            if (address != null) {
                lines.add("- 门店地址：" + address);
            }
            String shippingTime = sanitize(shop.getShippingTime());
            if (shippingTime != null) {
                lines.add("- 营业时间：" + shippingTime);
            }
        }
        if (hasContactPhone) {
            lines.add("- 门店联系方式：已配置（系统会自动附在回复末尾）");
        }
        if (lines.isEmpty()) {
            return "";
        }
        String block = HEADER + "\n" + String.join("\n", lines) + "\n" + FOOTER;
        return hasContactPhone ? block + "\n" + CONTACT_NOTICE : block;
    }

    /**
     * 同步数据会进入 system 消息，换行会被模型读成新的指令行，这里压平并截断。
     */
    private String sanitize(String raw) {
        String trimmed = StringUtils.trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        String flattened = StringUtils.trimToNull(trimmed.replaceAll("\\s+", " "));
        if (flattened == null) {
            return null;
        }
        return flattened.length() <= MAX_FIELD_LENGTH
                ? flattened
                : flattened.substring(0, MAX_FIELD_LENGTH) + "…";
    }

    /**
     * 与知识片段块合并成单条上下文；两侧都可能为空。
     */
    public String merge(String shopInfoBlock, String ragContext) {
        String shopInfo = StringUtils.trimToNull(shopInfoBlock);
        String rag = StringUtils.trimToNull(ragContext);
        if (shopInfo == null) {
            return StringUtils.defaultString(rag);
        }
        return rag == null ? shopInfo : shopInfo + "\n\n" + rag;
    }
}
