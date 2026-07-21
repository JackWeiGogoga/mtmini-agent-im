package com.gogoga.backend.enums;

/**
 * 知识与消息分类枚举。
 *
 * <p>知识条目可归为四类；消息分类仅使用前三类（{@link #GENERAL}/{@link #DELIVERY}/{@link #AFTER_SALE}）。</p>
 */
public enum KnowledgeCategoryEnum {

    GENERAL("普通类"),
    DELIVERY("配送类"),
    AFTER_SALE("售后类"),
    OTHER("其他"),
    ;

    private final String label;

    KnowledgeCategoryEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 归一化知识条目分类，非法或空值兜底为 {@link #GENERAL}。
     */
    public static KnowledgeCategoryEnum fromValue(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        for (KnowledgeCategoryEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return GENERAL;
    }

    /**
     * 归一化消息分类，仅允许普通/配送/售后，其余兜底为 {@link #GENERAL}。
     */
    public static KnowledgeCategoryEnum fromMessageValue(String value) {
        KnowledgeCategoryEnum category = fromValue(value);
        return category == OTHER ? GENERAL : category;
    }
}
