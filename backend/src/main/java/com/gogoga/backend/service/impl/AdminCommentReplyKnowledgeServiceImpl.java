package com.gogoga.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.enums.CommentReplyKnowledgeScopeEnum;
import com.gogoga.backend.enums.CommentReplyKnowledgeSourceTypeEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtReplyKnowledgeItemMapper;
import com.gogoga.backend.param.AdminCommentReplyOfficialKnowledgeParam;
import com.gogoga.backend.service.AdminCommentReplyKnowledgeService;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.CommentReplyKnowledgeItemVO;
import com.gogoga.backend.vo.CommentReplyOfficialTemplateImportVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCommentReplyKnowledgeServiceImpl implements AdminCommentReplyKnowledgeService {

    private final MtReplyKnowledgeItemMapper mtReplyKnowledgeItemMapper;

    private static final int MAX_IMPORT_FILE_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public PageInfo<CommentReplyKnowledgeItemVO> listOfficialTemplates(String sentiment, Integer pageNum, Integer pageSize) {
        requireSuperAdmin();
        int normalizedPageNum = normalizePageNum(pageNum);
        int normalizedPageSize = normalizePageSize(pageSize);
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<MtReplyKnowledgeItem> rows;
        if (StringUtils.isBlank(sentiment)) {
            rows = mtReplyKnowledgeItemMapper.selectOfficialTemplatesForAdmin(null);
        } else {
            CommentReplySentimentEnum normalized = requireSentiment(sentiment, false);
            rows = mtReplyKnowledgeItemMapper.selectOfficialTemplatesForAdmin(normalized.name());
        }
        PageInfo<MtReplyKnowledgeItem> sourcePage = new PageInfo<>(rows);
        PageInfo<CommentReplyKnowledgeItemVO> result = new PageInfo<>(
                rows.stream().map(this::toVO).collect(Collectors.toList())
        );
        result.setTotal(sourcePage.getTotal());
        result.setPageNum(sourcePage.getPageNum());
        result.setPageSize(sourcePage.getPageSize());
        result.setPages(sourcePage.getPages());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeItemVO createOfficialTemplate(AdminCommentReplyOfficialKnowledgeParam param) {
        requireSuperAdmin();
        CommentReplySentimentEnum sentiment = requireSentiment(param.getSentiment(), false);
        LocalDateTime now = LocalDateTime.now();
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setShopId(null);
        item.setScope(CommentReplyKnowledgeScopeEnum.OFFICIAL.name());
        item.setSourceType(CommentReplyKnowledgeSourceTypeEnum.TEMPLATE.name());
        item.setSentiment(sentiment.name());
        item.setTitle(StringUtils.trim(param.getTitle()));
        item.setAnswer(StringUtils.trim(param.getAnswer()));
        item.setQuestion(null);
        item.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
        item.setDeleted(false);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        mtReplyKnowledgeItemMapper.insert(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeItemVO updateOfficialTemplate(Long id, AdminCommentReplyOfficialKnowledgeParam param) {
        requireSuperAdmin();
        MtReplyKnowledgeItem item = requireOfficialTemplate(id);
        CommentReplySentimentEnum sentiment = requireSentiment(param.getSentiment(), false);
        item.setSentiment(sentiment.name());
        item.setTitle(StringUtils.trim(param.getTitle()));
        item.setAnswer(StringUtils.trim(param.getAnswer()));
        item.setQuestion(null);
        item.setEnabled(Boolean.TRUE.equals(param.getEnabled()));
        item.setUpdateTime(LocalDateTime.now());
        mtReplyKnowledgeItemMapper.updateById(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOfficialTemplate(Long id) {
        requireSuperAdmin();
        MtReplyKnowledgeItem item = requireOfficialTemplate(id);
        item.setDeleted(true);
        item.setEnabled(false);
        item.setUpdateTime(LocalDateTime.now());
        mtReplyKnowledgeItemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyOfficialTemplateImportVO importOfficialTemplates(MultipartFile file) {
        requireSuperAdmin();
        validateImportFile(file);
        ImportRows parsedRows = parseImportRows(file);
        Map<CommentReplySentimentEnum, LinkedHashMap<String, String>> rows = parsedRows.getRows();
        CommentReplyOfficialTemplateImportVO result = new CommentReplyOfficialTemplateImportVO();
        int created = 0;
        int updated = 0;
        int total = 0;
        for (Map.Entry<CommentReplySentimentEnum, LinkedHashMap<String, String>> entry : rows.entrySet()) {
            CommentReplySentimentEnum sentiment = entry.getKey();
            Map<String, MtReplyKnowledgeItem> existingMap = mtReplyKnowledgeItemMapper
                    .selectOfficialTemplatesIncludingDeleted(sentiment.name())
                    .stream()
                    .collect(Collectors.toMap(
                            item -> normalizeTemplateText(item.getAnswer()),
                            item -> item,
                            (left, right) -> left,
                            HashMap::new
                    ));
            for (Map.Entry<String, String> row : entry.getValue().entrySet()) {
                String answer = row.getKey();
                String title = row.getValue();
                MtReplyKnowledgeItem existing = existingMap.get(answer);
                if (existing == null) {
                    mtReplyKnowledgeItemMapper.insert(buildOfficialItem(sentiment, title, answer));
                    created++;
                } else {
                    existing.setTitle(title);
                    existing.setEnabled(true);
                    existing.setDeleted(false);
                    existing.setUpdateTime(LocalDateTime.now());
                    mtReplyKnowledgeItemMapper.updateById(existing);
                    updated++;
                }
                total++;
            }
            if (sentiment == CommentReplySentimentEnum.GOOD) {
                result.setGoodCount(entry.getValue().size());
            } else if (sentiment == CommentReplySentimentEnum.NEUTRAL) {
                result.setNeutralCount(entry.getValue().size());
            } else if (sentiment == CommentReplySentimentEnum.BAD) {
                result.setBadCount(entry.getValue().size());
            }
        }
        result.setTotalCount(total);
        result.setCreatedCount(created);
        result.setUpdatedCount(updated);
        result.setDuplicateCount(parsedRows.getRawCount() - total);
        result.setSkippedCount(0);
        return result;
    }

    private ImportRows parseImportRows(MultipartFile file) {
        Map<CommentReplySentimentEnum, LinkedHashMap<String, String>> rows = new LinkedHashMap<>();
        ImportRows result = new ImportRows();
        result.setRows(rows);
        rows.put(CommentReplySentimentEnum.GOOD, new LinkedHashMap<>());
        rows.put(CommentReplySentimentEnum.NEUTRAL, new LinkedHashMap<>());
        rows.put(CommentReplySentimentEnum.BAD, new LinkedHashMap<>());
        int[] indexBySentiment = {1, 1, 1};
        DataFormatter formatter = new DataFormatter();
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() <= 0) {
                throw new BusinessException("Excel 文件没有可读取的工作表");
            }
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                readCell(result, CommentReplySentimentEnum.GOOD, formatter, row, 0, indexBySentiment, 0, "闪购官方好评回复");
                readCell(result, CommentReplySentimentEnum.NEUTRAL, formatter, row, 1, indexBySentiment, 1, "闪购官方中评回复");
                readCell(result, CommentReplySentimentEnum.BAD, formatter, row, 2, indexBySentiment, 2, "闪购官方差评回复");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取官方回复文案 Excel 失败", e);
        }
        if (rows.values().stream().mapToInt(Map::size).sum() <= 0) {
            throw new BusinessException("Excel 中未读取到有效回复文案");
        }
        return result;
    }

    private void readCell(ImportRows result,
                          CommentReplySentimentEnum sentiment,
                          DataFormatter formatter,
                          Row row,
                          int cellIndex,
                          int[] indexBySentiment,
                          int sentimentIndex,
                          String titlePrefix) {
        String answer = normalizeTemplateText(formatter.formatCellValue(row.getCell(cellIndex)));
        if (StringUtils.isBlank(answer)) {
            return;
        }
        result.setRawCount(result.getRawCount() + 1);
        result.getRows().get(sentiment).computeIfAbsent(answer, ignored ->
                titlePrefix + StringUtils.leftPad(String.valueOf(indexBySentiment[sentimentIndex]++), 3, '0'));
    }

    private MtReplyKnowledgeItem buildOfficialItem(CommentReplySentimentEnum sentiment, String title, String answer) {
        LocalDateTime now = LocalDateTime.now();
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setShopId(null);
        item.setScope(CommentReplyKnowledgeScopeEnum.OFFICIAL.name());
        item.setSourceType(CommentReplyKnowledgeSourceTypeEnum.TEMPLATE.name());
        item.setSentiment(sentiment.name());
        item.setTitle(title);
        item.setQuestion(null);
        item.setAnswer(answer);
        item.setEnabled(true);
        item.setDeleted(false);
        item.setCreateTime(now);
        item.setUpdateTime(now);
        return item;
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择 Excel 文件");
        }
        if (file.getSize() > MAX_IMPORT_FILE_BYTES) {
            throw new BusinessException("Excel 文件不能超过 20M");
        }
        String filename = StringUtils.lowerCase(StringUtils.defaultString(file.getOriginalFilename()), Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new BusinessException("仅支持 xlsx 或 xls 格式的 Excel 文件");
        }
    }

    private String normalizeTemplateText(String value) {
        return StringUtils.trimToEmpty(value)
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
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

    @lombok.Data
    private static class ImportRows {

        private Map<CommentReplySentimentEnum, LinkedHashMap<String, String>> rows;
        private int rawCount;
    }

    private MtReplyKnowledgeItem requireOfficialTemplate(Long id) {
        if (id == null) {
            throw new BusinessException("知识ID不能为空");
        }
        MtReplyKnowledgeItem item = mtReplyKnowledgeItemMapper.selectById(id);
        if (item == null || Boolean.TRUE.equals(item.getDeleted())
                || !StringUtils.equals(item.getScope(), CommentReplyKnowledgeScopeEnum.OFFICIAL.name())
                || !StringUtils.equals(item.getSourceType(), CommentReplyKnowledgeSourceTypeEnum.TEMPLATE.name())) {
            throw new BusinessException(404, "官方回复文案不存在");
        }
        return item;
    }

    private CommentReplySentimentEnum requireSentiment(String value, boolean allowUnknown) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        try {
            CommentReplySentimentEnum sentiment = CommentReplySentimentEnum.valueOf(normalized);
            if (!allowUnknown && sentiment == CommentReplySentimentEnum.UNKNOWN) {
                throw new BusinessException("官方回复文案不支持 UNKNOWN 类型");
            }
            return sentiment;
        } catch (IllegalArgumentException e) {
            throw new BusinessException("评论类型不合法");
        }
    }

    private CommentReplyKnowledgeItemVO toVO(MtReplyKnowledgeItem item) {
        CommentReplyKnowledgeItemVO vo = new CommentReplyKnowledgeItemVO();
        vo.setId(item.getId());
        vo.setShopId(item.getShopId());
        vo.setScope(item.getScope());
        vo.setSourceType(item.getSourceType());
        vo.setSentiment(item.getSentiment());
        vo.setTitle(item.getTitle());
        vo.setQuestion(item.getQuestion());
        vo.setAnswer(item.getAnswer());
        vo.setEnabled(item.getEnabled());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }

    private void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(403, "仅超级管理员可执行该操作");
        }
    }
}
