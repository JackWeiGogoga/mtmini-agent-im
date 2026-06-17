package com.gogoga.backend.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具类，提供对象与 JSON 字符串之间的转换功能
 * <p>
 * 基于 Jackson 实现，已配置：
 * <ul>
 *   <li>支持 Java 8 日期时间类型（LocalDateTime、LocalDate 等）</li>
 *   <li>日期格式使用 ISO-8601 而非时间戳</li>
 *   <li>忽略未知属性，避免反序列化失败</li>
 * </ul>
 *
 * @author JackWei
 * @since 2026-01-01
 */
@Slf4j
public class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER;
    private static final ObjectMapper NOT_NULL_MAPPER;
    private static final ObjectWriter PRETTY_WRITER;

    static {
        // 创建并配置主 ObjectMapper
        OBJECT_MAPPER = createObjectMapper();

        // 创建排除 null 值的 ObjectMapper
        NOT_NULL_MAPPER = createObjectMapper();
        NOT_NULL_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // 使用 ObjectWriter 进行格式化输出，比创建新的 ObjectMapper 更轻量
        PRETTY_WRITER = OBJECT_MAPPER.writerWithDefaultPrettyPrinter();
    }

    /**
     * 创建配置好的 ObjectMapper 实例
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 Java 8 日期时间模块
        mapper.registerModule(new JavaTimeModule());

        // 日期格式使用 ISO-8601，而非时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 忽略未知属性，提高兼容性
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }

    // ==================== 对象转 JSON ====================

    /**
     * 将对象转换为 JSON 字符串
     *
     * @param object 待转换的对象
     * @return JSON 字符串，转换失败或对象为 null 时返回空字符串
     */
    public static String toJson(Object object) {
        if (object == null) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("对象转 JSON 失败: {}", object.getClass().getSimpleName(), e);
            return "";
        }
    }

    /**
     * 将对象转换为 JSON 字符串，忽略 null 值字段
     *
     * @param object 待转换的对象
     * @return JSON 字符串，转换失败或对象为 null 时返回空字符串
     */
    public static String toJsonNonNull(Object object) {
        if (object == null) {
            return "";
        }
        try {
            return NOT_NULL_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("对象转 JSON（非空）失败: {}", object.getClass().getSimpleName(), e);
            return "";
        }
    }

    /**
     * 将对象转换为格式化的 JSON 字符串（带缩进）
     *
     * @param object 待转换的对象
     * @return 格式化的 JSON 字符串，转换失败或对象为 null 时返回空字符串
     */
    public static String toJsonPretty(Object object) {
        if (object == null) {
            return "";
        }
        try {
            return PRETTY_WRITER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("对象转 JSON（格式化）失败: {}", object.getClass().getSimpleName(), e);
            return "";
        }
    }

    // ==================== JSON 转对象 ====================

    /**
     * 将 JSON 字符串转换为指定类型的对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 转换后的对象，失败或 json 为空时返回 null
     */
    public static <T> T toBean(String json, Class<T> clazz) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("JSON 转对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为泛型类型的对象
     * <p>
     * 适用于复杂泛型，如 {@code List<Map<String, String>>}
     *
     * @param json          JSON 字符串
     * @param typeReference 类型引用
     * @param <T>           目标类型泛型
     * @return 转换后的对象，失败或 json 为空时返回 null
     */
    public static <T> T toBean(String json, TypeReference<T> typeReference) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.warn("JSON 转泛型对象失败", e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为 List
     *
     * @param json         JSON 数组字符串
     * @param elementClazz 列表元素类型
     * @param <T>          元素类型泛型
     * @return 转换后的 List，失败时返回空 List
     */
    public static <T> List<T> toList(String json, Class<T> elementClazz) {
        if (StringUtils.isEmpty(json) || elementClazz == null) {
            return Collections.emptyList();
        }
        try {
            JavaType javaType = OBJECT_MAPPER.getTypeFactory()
                    .constructCollectionType(ArrayList.class, elementClazz);
            return OBJECT_MAPPER.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            log.warn("JSON 转 List 失败: {}", elementClazz.getSimpleName(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将 JSON 字符串解析为 JsonNode 树结构
     *
     * @param json JSON 字符串
     * @return JsonNode 对象，失败或 json 为空时返回 null
     */
    public static JsonNode readTree(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("解析 JSON 树失败", e);
            return null;
        }
    }

    /**
     * 将 JSON 字符串转换为 Map
     *
     * @param json JSON 字符串
     * @return Map 对象，失败时返回 null
     */
    public static Map<String, Object> toMap(String json) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("JSON 转 Map 失败", e);
            return null;
        }
    }

    // ==================== 对象与 Map 互转 ====================

    /**
     * 将对象转换为 Map
     *
     * @param obj 待转换的对象
     * @return Map 对象，obj 为 null 时返回 null
     */
    public static Map<String, Object> objectToMap(Object obj) {
        if (obj == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 将对象转换为 Map，排除 null 值字段
     *
     * @param obj 待转换的对象
     * @return Map 对象（不含 null 值），obj 为 null 时返回 null
     */
    public static Map<String, Object> objectToMapNonNull(Object obj) {
        if (obj == null) {
            return null;
        }
        return NOT_NULL_MAPPER.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 将 Map 转换为指定类型的对象
     *
     * @param map   Map 对象
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 转换后的对象，失败时返回 null
     */
    public static <T> T mapToObject(Map<String, Object> map, Class<T> clazz) {
        if (map == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.convertValue(map, clazz);
        } catch (IllegalArgumentException e) {
            log.warn("Map 转对象失败: {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    /**
     * 将对象转换为 String 值的 Map
     *
     * @param obj 待转换的对象
     * @return Map<String, String>，obj 为 null 时返回 null
     */
    public static Map<String, String> objectToStringMap(Object obj) {
        Map<String, Object> objMap = objectToMap(obj);
        if (objMap == null) {
            return null;
        }
        Map<String, String> strMap = new HashMap<>(objMap.size());
        objMap.forEach((key, value) -> {
            if (value != null) {
                strMap.put(key, value.toString());
            }
        });
        return strMap;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从文本中提取 JSON 对象字符串
     * <p>
     * 查找第一个 '{' 和最后一个 '}' 之间的内容
     *
     * @param text 包含 JSON 的文本
     * @return 提取的 JSON 字符串，未找到时返回 null
     */
    public static String extractJsonObject(String text) {
        if (StringUtils.isEmpty(text)) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || start >= end) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    /**
     * 清洗大模型返回文本中的 JSON 对象字符串
     * <p>
     * 支持：
     * <ul>
     *   <li>纯 JSON 对象</li>
     *   <li>```json fenced code block</li>
     *   <li>前后带少量说明文本的 JSON 对象</li>
     * </ul>
     *
     * @param text 大模型返回文本
     * @return 可用于解析的 JSON 对象字符串，未找到时返回 null
     */
    public static String normalizeLlmJsonObjectText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }

        String trimmed = text.trim();
        if (isJsonObject(trimmed)) {
            return trimmed;
        }

        String fencedContent = extractFencedCodeContent(trimmed);
        if (StringUtils.isNotBlank(fencedContent)) {
            String fencedTrimmed = fencedContent.trim();
            if (isJsonObject(fencedTrimmed)) {
                return fencedTrimmed;
            }
            String extractedFromFence = extractJsonObject(fencedTrimmed);
            if (isJsonObject(extractedFromFence)) {
                return extractedFromFence;
            }
        }

        String extracted = extractJsonObject(trimmed);
        if (isJsonObject(extracted)) {
            return extracted;
        }
        return null;
    }

    /**
     * 从可能带 markdown 包裹或少量杂文本的大模型输出中解析 JSON 对象
     *
     * @param text 大模型返回文本
     * @param clazz 目标类型
     * @param <T> 目标类型
     * @return 解析结果，失败时返回 null
     */
    public static <T> T parseLlmJsonObject(String text, Class<T> clazz) {
        if (StringUtils.isBlank(text) || clazz == null) {
            return null;
        }

        String trimmed = text.trim();
        T direct = isJsonObject(trimmed) ? toBean(trimmed, clazz) : null;
        if (direct != null) {
            return direct;
        }

        String normalized = normalizeLlmJsonObjectText(text);
        if (StringUtils.isBlank(normalized) || StringUtils.equals(normalized, trimmed)) {
            return null;
        }
        return toBean(normalized, clazz);
    }

    private static boolean isJsonObject(String text) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private static String extractFencedCodeContent(String text) {
        if (StringUtils.isBlank(text) || !text.startsWith("```")) {
            return null;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0) {
            return null;
        }
        int closingFence = text.lastIndexOf("```");
        if (closingFence <= firstLineEnd) {
            return null;
        }
        return text.substring(firstLineEnd + 1, closingFence);
    }

    /**
     * 获取内部 ObjectMapper 实例
     * <p>
     * 注意：优先使用 JsonUtil 的静态方法，仅在特殊场景下使用此方法
     *
     * @return ObjectMapper 实例
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

}
