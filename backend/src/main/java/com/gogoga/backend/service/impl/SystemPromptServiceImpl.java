package com.gogoga.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.dto.ResolvedSystemPromptDTO;
import com.gogoga.backend.entity.SystemPromptConfig;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.SystemPromptConfigMapper;
import com.gogoga.backend.param.SystemPromptUpdateParam;
import com.gogoga.backend.service.SystemPromptService;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.SystemPromptConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPromptServiceImpl implements SystemPromptService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private final SystemPromptConfigMapper systemPromptConfigMapper;
    private final ResourceLoader resourceLoader;

    @Override
    public PageInfo<SystemPromptConfigVO> list(String promptType, Boolean enabled, String keyword, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(normalizePageNum(pageNum), normalizePageSize(pageSize));
        List<SystemPromptConfig> rows = systemPromptConfigMapper.selectForAdmin(
                StringUtils.upperCase(StringUtils.trimToNull(promptType)),
                enabled,
                StringUtils.trimToNull(keyword)
        );
        PageInfo<SystemPromptConfig> sourcePage = new PageInfo<>(rows);
        PageInfo<SystemPromptConfigVO> result = new PageInfo<>(rows.stream().map(this::toVO).collect(Collectors.toList()));
        result.setTotal(sourcePage.getTotal());
        result.setPageNum(sourcePage.getPageNum());
        result.setPageSize(sourcePage.getPageSize());
        result.setPages(sourcePage.getPages());
        return result;
    }

    @Override
    public SystemPromptConfigVO getById(Long id) {
        return toVO(requireConfig(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemPromptConfigVO update(Long id, SystemPromptUpdateParam param) {
        SystemPromptConfig config = requireConfig(id);
        config.setContent(StringUtils.trimToEmpty(param.getContent()));
        config.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
        config.setRemark(StringUtils.trimToNull(param.getRemark()));
        config.setUpdateBy(StringUtils.defaultIfBlank(UserContext.getUserName(), UserContext.getUserId()));
        config.setUpdateTime(LocalDateTime.now());
        systemPromptConfigMapper.updateContent(config);
        return toVO(systemPromptConfigMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemPromptConfigVO updateEnabled(Long id, Boolean enabled) {
        requireConfig(id);
        systemPromptConfigMapper.updateEnabled(
                id,
                Boolean.TRUE.equals(enabled),
                StringUtils.defaultIfBlank(UserContext.getUserName(), UserContext.getUserId()),
                LocalDateTime.now()
        );
        return toVO(systemPromptConfigMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemPromptConfigVO restoreDefault(Long id) {
        SystemPromptConfig config = requireConfig(id);
        String defaultContent = readDefaultResource(config);
        if (StringUtils.isBlank(defaultContent)) {
            throw new BusinessException("默认提示词资源不存在或为空");
        }
        config.setContent(defaultContent);
        config.setEnabled(true);
        config.setUpdateBy(StringUtils.defaultIfBlank(UserContext.getUserName(), UserContext.getUserId()));
        config.setUpdateTime(LocalDateTime.now());
        systemPromptConfigMapper.updateContent(config);
        return toVO(systemPromptConfigMapper.selectById(id));
    }

    @Override
    public ResolvedSystemPromptDTO resolve(String promptKey, String fallbackContent) {
        SystemPromptConfig config = StringUtils.isBlank(promptKey) ? null : systemPromptConfigMapper.selectByPromptKey(promptKey);
        if (config != null && Boolean.TRUE.equals(config.getEnabled()) && StringUtils.isNotBlank(config.getContent())) {
            return resolved(promptKey, config.getContent(), config.getVersion(), "DB");
        }
        String defaultContent = readDefaultResource(config);
        if (StringUtils.isNotBlank(defaultContent)) {
            return resolved(promptKey, defaultContent, config == null ? null : config.getVersion(), "RESOURCE");
        }
        if (StringUtils.isNotBlank(fallbackContent)) {
            log.warn("系统提示词使用代码兜底，promptKey={}", promptKey);
            return resolved(promptKey, fallbackContent, config == null ? null : config.getVersion(), "FALLBACK");
        }
        throw new BusinessException("系统提示词不可用：" + promptKey);
    }

    @Override
    public ResolvedSystemPromptDTO render(String promptKey, Map<String, ?> variables, String fallbackContent) {
        ResolvedSystemPromptDTO resolved = resolve(promptKey, fallbackContent);
        String content = renderContent(promptKey, resolved.getContent(), variables);
        resolved.setContent(content);
        return resolved;
    }

    String renderContent(String promptKey, String template, Map<String, ?> variables) {
        if (StringUtils.isBlank(template)) {
            return template;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (variables == null || !variables.containsKey(variableName)) {
                log.warn("系统提示词存在未识别变量，promptKey={}, variable={}", promptKey, variableName);
            }
            Object value = variables == null ? null : variables.get(variableName);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toPromptValue(value)));
        }
        matcher.appendTail(buffer);
        Matcher remainMatcher = VARIABLE_PATTERN.matcher(buffer.toString());
        while (remainMatcher.find()) {
            log.warn("系统提示词存在未识别变量，promptKey={}, variable={}", promptKey, remainMatcher.group(1));
        }
        return buffer.toString();
    }

    private String toPromptValue(Object value) {
        if (value == null) {
            return "无";
        }
        String text = StringUtils.trimToNull(String.valueOf(value));
        return text == null ? "无" : text;
    }

    private ResolvedSystemPromptDTO resolved(String promptKey, String content, Integer version, String source) {
        ResolvedSystemPromptDTO result = new ResolvedSystemPromptDTO();
        result.setPromptKey(promptKey);
        result.setContent(content);
        result.setVersion(version);
        result.setSource(source);
        return result;
    }

    private SystemPromptConfig requireConfig(Long id) {
        if (id == null) {
            throw new BusinessException("提示词ID不能为空");
        }
        SystemPromptConfig config = systemPromptConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(404, "系统提示词不存在");
        }
        return config;
    }

    private String readDefaultResource(SystemPromptConfig config) {
        if (config == null || StringUtils.isBlank(config.getDefaultResourcePath())) {
            return null;
        }
        String location = "classpath:" + StringUtils.removeStart(config.getDefaultResourcePath(), "/");
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                return null;
            }
            return StringUtils.trimToNull(StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("读取默认提示词资源失败，promptKey={}, path={}", config.getPromptKey(), config.getDefaultResourcePath(), e);
            return null;
        }
    }

    private SystemPromptConfigVO toVO(SystemPromptConfig config) {
        if (config == null) {
            return null;
        }
        SystemPromptConfigVO vo = new SystemPromptConfigVO();
        vo.setId(config.getId());
        vo.setPromptKey(config.getPromptKey());
        vo.setPromptName(config.getPromptName());
        vo.setPromptType(config.getPromptType());
        vo.setContent(config.getContent());
        vo.setDefaultResourcePath(config.getDefaultResourcePath());
        vo.setVariablesJson(config.getVariablesJson());
        vo.setModelHint(config.getModelHint());
        vo.setEnabled(config.getEnabled());
        vo.setVersion(config.getVersion());
        vo.setRemark(config.getRemark());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
