package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeDocument;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.CommentReplyDocumentParseStatusEnum;
import com.gogoga.backend.enums.CommentReplyKnowledgeSourceTypeEnum;
import com.gogoga.backend.enums.CommentReplySentimentEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtReplyKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtReplyKnowledgeDocumentMapper;
import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CommentReplyKnowledgeDocumentService;
import com.gogoga.backend.service.CosStorageService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.util.EmbeddingVectorUtil;
import com.gogoga.backend.vo.CommentReplyKnowledgeChunkVO;
import com.gogoga.backend.vo.CommentReplyKnowledgeDocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentReplyKnowledgeDocumentServiceImpl implements CommentReplyKnowledgeDocumentService {

    private static final String STORAGE_PROVIDER_COS = "TENCENT_COS";
    private static final int TARGET_CHUNK_CHARS = 800;
    private static final int CHUNK_OVERLAP_CHARS = 120;

    private final MtH5ShopService mtH5ShopService;
    private final MtReplyKnowledgeDocumentMapper mtReplyKnowledgeDocumentMapper;
    private final MtReplyKnowledgeChunkMapper mtReplyKnowledgeChunkMapper;
    private final CosStorageService cosStorageService;
    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtProperties mtProperties;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final CommentReplyEmbeddingService commentReplyEmbeddingService;

    @Override
    public List<CommentReplyKnowledgeDocumentVO> listCurrentShopDocuments() {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return listShopDocuments(shop.getId());
    }

    @Override
    public List<CommentReplyKnowledgeDocumentVO> listShopDocuments(Long shopId) {
        requireShopId(shopId);
        return mtReplyKnowledgeDocumentMapper.selectByShopId(shopId).stream()
                .map(this::toDocumentVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeDocumentVO uploadCurrentShopDocument(MultipartFile file) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return uploadShopDocument(shop.getId(), file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeDocumentVO uploadShopDocument(Long shopId, MultipartFile file) {
        requireShopId(shopId);
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        validateUpload(file, shopId, knowledge);
        String fileType = resolveFileType(file);
        CosStorageService.StoredObject storedObject = cosStorageService.uploadObject(
                knowledge.getDocumentKeyPrefix(),
                String.valueOf(shopId),
                file
        );
        LocalDateTime now = LocalDateTime.now();
        MtReplyKnowledgeDocument document = new MtReplyKnowledgeDocument();
        document.setShopId(shopId);
        document.setFileName(StringUtils.defaultIfBlank(file.getOriginalFilename(), "knowledge." + fileType.toLowerCase(Locale.ROOT)));
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setStorageProvider(STORAGE_PROVIDER_COS);
        document.setObjectKey(storedObject.getObjectKey());
        document.setParseStatus(CommentReplyDocumentParseStatusEnum.PENDING.name());
        document.setParseError(null);
        document.setChunkCount(0);
        document.setEnabled(true);
        document.setDeleted(false);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        mtReplyKnowledgeDocumentMapper.insert(document);
        enqueueParseTask(document);
        return toDocumentVO(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopDocument(Long id) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        deleteShopDocument(shop.getId(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShopDocument(Long shopId, Long id) {
        requireShopId(shopId);
        MtReplyKnowledgeDocument document = requireCurrentShopDocument(id, shopId);
        mtReplyKnowledgeDocumentMapper.softDeleteByIdAndShopId(document.getId(), shopId, LocalDateTime.now());
        knowledgeVectorStore.deleteBySource(CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name(), document.getId());
        cosStorageService.deleteObject(document.getObjectKey());
    }

    @Override
    public List<CommentReplyKnowledgeChunkVO> listCurrentShopDocumentChunks(Long documentId) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return listShopDocumentChunks(shop.getId(), documentId);
    }

    @Override
    public List<CommentReplyKnowledgeChunkVO> listShopDocumentChunks(Long shopId, Long documentId) {
        requireShopId(shopId);
        requireCurrentShopDocument(documentId, shopId);
        return mtReplyKnowledgeChunkMapper.selectByDocumentIdAndShopId(documentId, shopId).stream()
                .map(this::toChunkVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeChunkVO updateCurrentShopChunkSentiment(Long chunkId, CommentReplyKnowledgeChunkUpdateParam param) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return updateShopChunkSentiment(shop.getId(), chunkId, param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeChunkVO updateShopChunkSentiment(Long shopId, Long chunkId, CommentReplyKnowledgeChunkUpdateParam param) {
        requireShopId(shopId);
        CommentReplySentimentEnum sentiment = requireSentiment(param.getSentiment());
        int rows = mtReplyKnowledgeChunkMapper.updateSentimentByIdAndShopId(chunkId, shopId, sentiment.name(), LocalDateTime.now());
        if (rows <= 0) {
            throw new BusinessException(404, "知识片段不存在");
        }
        return toChunkVO(mtReplyKnowledgeChunkMapper.selectByIdAndShopId(chunkId, shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeChunkVO updateCurrentShopChunkEnabled(Long chunkId, Boolean enabled) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        return updateShopChunkEnabled(shop.getId(), chunkId, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeChunkVO updateShopChunkEnabled(Long shopId, Long chunkId, Boolean enabled) {
        requireShopId(shopId);
        int rows = mtReplyKnowledgeChunkMapper.updateEnabledByIdAndShopId(chunkId, shopId, Boolean.TRUE.equals(enabled), LocalDateTime.now());
        if (rows <= 0) {
            throw new BusinessException(404, "知识片段不存在");
        }
        return toChunkVO(mtReplyKnowledgeChunkMapper.selectByIdAndShopId(chunkId, shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopChunk(Long chunkId) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        deleteShopChunk(shop.getId(), chunkId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShopChunk(Long shopId, Long chunkId) {
        requireShopId(shopId);
        int rows = mtReplyKnowledgeChunkMapper.softDeleteByIdAndShopId(chunkId, shopId, LocalDateTime.now());
        if (rows <= 0) {
            throw new BusinessException(404, "知识片段不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeDocumentParseTask(MtKnowledgeDocumentParseTaskPayload payload) {
        if (payload == null || payload.getShopId() == null || payload.getDocumentId() == null) {
            throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_PARSE_PAYLOAD_INVALID");
        }
        MtReplyKnowledgeDocument document = mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(payload.getDocumentId(), payload.getShopId());
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            log.warn("知识文档解析跳过，原因=文档不存在，shopId={}, documentId={}", payload.getShopId(), payload.getDocumentId());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        mtReplyKnowledgeDocumentMapper.updateParseStatus(document.getId(), CommentReplyDocumentParseStatusEnum.PARSING.name(), null, 0, now);
        try {
            byte[] content = cosStorageService.loadObject(document.getObjectKey()).getContent();
            List<ParsedDocumentChunk> rawChunks = extractChunks(document.getFileType(), content);
            List<MtReplyKnowledgeChunk> chunks = buildChunks(document, rawChunks);
            knowledgeVectorStore.deleteBySource(CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name(), document.getId());
            knowledgeVectorStore.upsertChunks(chunks);
            mtReplyKnowledgeDocumentMapper.updateParseStatus(
                    document.getId(),
                    CommentReplyDocumentParseStatusEnum.SUCCESS.name(),
                    null,
                    chunks.size(),
                    LocalDateTime.now()
            );
        } catch (Exception e) {
            String error = StringUtils.left(StringUtils.defaultIfBlank(e.getMessage(), "UNKNOWN_ERROR"), 1000);
            mtReplyKnowledgeDocumentMapper.updateParseStatus(
                    document.getId(),
                    CommentReplyDocumentParseStatusEnum.FAILED.name(),
                    error,
                    0,
                    LocalDateTime.now()
            );
            log.error("知识文档解析失败，shopId={}, documentId={}", document.getShopId(), document.getId(), e);
        }
    }

    private void enqueueParseTask(MtReplyKnowledgeDocument document) {
        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(document.getShopId());
        payload.setDocumentId(document.getId());
        mtAsyncTaskService.enqueueTask(
                MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE,
                "knowledge-document:" + document.getId(),
                String.valueOf(document.getShopId()),
                payload,
                LocalDateTime.now(),
                mtProperties.getAsyncTask().getKnowledgeDocumentParse().getMaxRetryCount()
        );
    }

    private void validateUpload(MultipartFile file, Long shopId, MtProperties.Knowledge knowledge) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择知识文档");
        }
        resolveFileType(file);
        if (file.getSize() > knowledge.getMaxDocumentBytes()) {
            throw new BusinessException("单个知识文档不能超过 20M");
        }
        int count = mtReplyKnowledgeDocumentMapper.countAvailableByShopId(shopId);
        if (count >= knowledge.getMaxDocumentsPerShop()) {
            throw new BusinessException("每个商户最多上传 " + knowledge.getMaxDocumentsPerShop() + " 个知识文档");
        }
    }

    private String resolveFileType(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        String lower = StringUtils.lowerCase(StringUtils.defaultString(filename), Locale.ROOT);
        if (lower.endsWith(".txt")) {
            return "TXT";
        }
        if (lower.endsWith(".docx")) {
            return "DOCX";
        }
        if (lower.endsWith(".xlsx")) {
            return "XLSX";
        }
        throw new BusinessException("知识文档仅支持 txt、docx、xlsx 格式");
    }

    private MtReplyKnowledgeDocument requireCurrentShopDocument(Long id, Long shopId) {
        if (id == null) {
            throw new BusinessException("文档ID不能为空");
        }
        MtReplyKnowledgeDocument document = mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(id, shopId);
        if (document == null) {
            throw new BusinessException(404, "知识文档不存在");
        }
        return document;
    }

    private void requireShopId(Long shopId) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
    }

    private List<ParsedDocumentChunk> extractChunks(String fileType, byte[] content) throws Exception {
        String text = switch (StringUtils.defaultString(fileType)) {
            case "TXT" -> new String(content, StandardCharsets.UTF_8);
            case "DOCX" -> extractDocxText(content);
            case "XLSX" -> null;
            default -> throw new BusinessException("不支持的知识文档格式");
        };
        if (StringUtils.equals(fileType, "XLSX")) {
            return extractXlsxChunks(content);
        }
        String cleanedText = cleanText(text);
        List<ParsedDocumentChunk> structuredChunks = extractStructuredTextChunks(cleanedText);
        if (!structuredChunks.isEmpty()) {
            return structuredChunks;
        }
        List<ParsedDocumentChunk> paragraphChunks = extractParagraphChunks(cleanedText);
        return paragraphChunks.isEmpty() ? splitText(cleanedText) : paragraphChunks;
    }

    private String extractDocxText(byte[] content) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            List<String> lines = new ArrayList<>();
            document.getParagraphs().forEach(paragraph -> addIfNotBlank(lines, paragraph.getText()));
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        addIfNotBlank(cells, cell.getText());
                    }
                    addIfNotBlank(lines, String.join(" | ", cells));
                }
            }
            return String.join("\n", lines);
        }
    }

    private List<ParsedDocumentChunk> extractXlsxChunks(byte[] content) throws Exception {
        List<ParsedDocumentChunk> sentimentChunks = extractSentimentTemplateXlsxChunks(content);
        if (!sentimentChunks.isEmpty()) {
            return sentimentChunks;
        }
        return splitText(cleanText(extractGenericXlsxText(content)));
    }

    private List<ParsedDocumentChunk> extractSentimentTemplateXlsxChunks(byte[] content) throws Exception {
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        int maxChunks = Math.max(1, knowledge.getMaxChunksPerDocument());
        DataFormatter formatter = new DataFormatter();
        List<ParsedDocumentChunk> chunks = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (Sheet sheet : workbook) {
                List<String> headers = null;
                CommentReplySentimentEnum[] sentimentByColumn = null;
                for (Row row : sheet) {
                    List<String> cells = readRowCells(row, formatter);
                    if (cells.isEmpty()) {
                        continue;
                    }
                    if (headers == null) {
                        headers = cells;
                        sentimentByColumn = resolveSentimentColumns(headers);
                        if (!hasAnySentimentColumn(sentimentByColumn)) {
                            break;
                        }
                        continue;
                    }
                    for (int i = 0; i < cells.size() && i < sentimentByColumn.length; i++) {
                        CommentReplySentimentEnum sentiment = sentimentByColumn[i];
                        String value = StringUtils.trimToEmpty(cells.get(i));
                        if (sentiment == null || StringUtils.isBlank(value)) {
                            continue;
                        }
                        String header = i < headers.size() ? headers.get(i) : sentiment.name();
                        String text = StringUtils.defaultIfBlank(header, sentiment.name()) + "：" + value;
                        chunks.add(new ParsedDocumentChunk(cleanText(text), sentiment));
                        if (chunks.size() >= maxChunks) {
                            return chunks;
                        }
                    }
                }
            }
        }
        return chunks;
    }

    private String extractGenericXlsxText(byte[] content) throws Exception {
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            List<String> lines = new ArrayList<>();
            for (Sheet sheet : workbook) {
                addIfNotBlank(lines, "Sheet：" + sheet.getSheetName());
                List<String> headers = null;
                boolean structured = false;
                for (Row row : sheet) {
                    List<String> cells = readRowCells(row, formatter);
                    if (cells.isEmpty()) {
                        continue;
                    }
                    if (headers == null) {
                        headers = cells;
                        structured = hasKnowledgeHeaders(headers);
                        addIfNotBlank(lines, String.join(" | ", cells));
                        continue;
                    }
                    if (structured) {
                        addIfNotBlank(lines, buildStructuredExcelLine(headers, cells));
                    } else {
                        addIfNotBlank(lines, String.join(" | ", cells));
                    }
                }
            }
            return String.join("\n", lines);
        }
    }

    private List<String> readRowCells(Row row, DataFormatter formatter) {
        List<String> cells = new ArrayList<>();
        if (row == null) {
            return cells;
        }
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return cells;
        }
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i);
            cells.add(cell == null ? "" : StringUtils.trimToEmpty(formatter.formatCellValue(cell)));
        }
        while (!cells.isEmpty() && StringUtils.isBlank(cells.get(cells.size() - 1))) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }

    private CommentReplySentimentEnum[] resolveSentimentColumns(List<String> headers) {
        CommentReplySentimentEnum[] result = new CommentReplySentimentEnum[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            String header = StringUtils.defaultString(headers.get(i));
            if (StringUtils.containsAny(header, "好评", "四星", "五星", "4星", "5星")) {
                result[i] = CommentReplySentimentEnum.GOOD;
            } else if (StringUtils.containsAny(header, "中评", "三星", "3星")) {
                result[i] = CommentReplySentimentEnum.NEUTRAL;
            } else if (StringUtils.containsAny(header, "差评", "零星", "一星", "二星", "0星", "1星", "2星")) {
                result[i] = CommentReplySentimentEnum.BAD;
            }
        }
        return result;
    }

    private boolean hasAnySentimentColumn(CommentReplySentimentEnum[] sentimentByColumn) {
        if (sentimentByColumn == null) {
            return false;
        }
        for (CommentReplySentimentEnum sentiment : sentimentByColumn) {
            if (sentiment != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKnowledgeHeaders(List<String> headers) {
        String joined = headers.stream()
                .map(value -> StringUtils.lowerCase(StringUtils.defaultString(value), Locale.ROOT))
                .collect(Collectors.joining("|"));
        return StringUtils.containsAny(joined, "问题", "顾客问题", "评论", "用户评论", "回答", "回复", "商家回复", "话术");
    }

    private String buildStructuredExcelLine(List<String> headers, List<String> cells) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < headers.size() && i < cells.size(); i++) {
            String header = StringUtils.trimToEmpty(headers.get(i));
            String value = StringUtils.trimToEmpty(cells.get(i));
            if (StringUtils.isBlank(header) || StringUtils.isBlank(value)) {
                continue;
            }
            parts.add(header + "：" + value);
        }
        return String.join("\n", parts);
    }

    private String cleanText(String text) {
        return StringUtils.defaultString(text)
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private List<ParsedDocumentChunk> extractStructuredTextChunks(String text) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        int maxChunks = Math.max(1, knowledge.getMaxChunksPerDocument());
        List<String> blocks = splitStructuredBlocks(text);
        List<ParsedDocumentChunk> chunks = new ArrayList<>();
        for (String block : blocks) {
            String cleanedBlock = cleanText(block);
            if (!isUsefulStructuredBlock(cleanedBlock)) {
                continue;
            }
            chunks.add(new ParsedDocumentChunk(cleanedBlock, extractExplicitSentiment(cleanedBlock)));
            if (chunks.size() >= maxChunks) {
                break;
            }
        }
        return chunks;
    }

    private List<String> splitStructuredBlocks(String text) {
        List<String> blocks = new ArrayList<>();
        String[] lines = StringUtils.splitByWholeSeparatorPreserveAllTokens(text, "\n");
        StringBuilder current = new StringBuilder();
        List<String> currentKeys = new ArrayList<>();
        for (String line : lines) {
            String trimmed = StringUtils.trimToEmpty(line);
            StructuredLine structuredLine = parseStructuredLine(trimmed);
            if (StringUtils.isBlank(trimmed)) {
                if (current.length() > 0 && currentKeys.size() >= 2) {
                    addIfNotBlank(blocks, current.toString());
                    current.setLength(0);
                    currentKeys.clear();
                }
                continue;
            }
            if (structuredLine != null) {
                String normalizedKey = normalizeStructureKey(structuredLine.getKey());
                if (current.length() > 0 && currentKeys.size() >= 2 && currentKeys.contains(normalizedKey)) {
                    addIfNotBlank(blocks, current.toString());
                    current.setLength(0);
                    currentKeys.clear();
                }
                currentKeys.add(normalizedKey);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(trimmed);
        }
        addIfNotBlank(blocks, current.toString());
        return blocks;
    }

    private boolean isUsefulStructuredBlock(String block) {
        if (StringUtils.isBlank(block)) {
            return false;
        }
        int structuredLineCount = 0;
        for (String line : StringUtils.splitByWholeSeparatorPreserveAllTokens(block, "\n")) {
            if (parseStructuredLine(StringUtils.trimToEmpty(line)) != null) {
                structuredLineCount++;
            }
        }
        if (structuredLineCount < 2) {
            return false;
        }
        return StringUtils.length(block) >= 20;
    }

    private StructuredLine parseStructuredLine(String line) {
        if (StringUtils.isBlank(line)) {
            return null;
        }
        int delimiterIndex = findStructureDelimiter(line);
        if (delimiterIndex <= 0 || delimiterIndex >= line.length() - 1) {
            return null;
        }
        String key = StringUtils.trimToEmpty(line.substring(0, delimiterIndex));
        String value = StringUtils.trimToEmpty(line.substring(delimiterIndex + 1));
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) || key.length() > 24) {
            return null;
        }
        return new StructuredLine(key, value);
    }

    private int findStructureDelimiter(String line) {
        int fullColon = StringUtils.indexOf(line, "：");
        int halfColon = StringUtils.indexOf(line, ":");
        int equals = StringUtils.indexOf(line, "=");
        int result = -1;
        for (int index : new int[]{fullColon, halfColon, equals}) {
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private String normalizeStructureKey(String key) {
        return StringUtils.lowerCase(StringUtils.trimToEmpty(key), Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private CommentReplySentimentEnum extractExplicitSentiment(String text) {
        String value = StringUtils.defaultString(text);
        for (String line : StringUtils.splitByWholeSeparatorPreserveAllTokens(value, "\n")) {
            StructuredLine structuredLine = parseStructuredLine(StringUtils.trimToEmpty(line));
            if (structuredLine == null) {
                continue;
            }
            String key = structuredLine.getKey();
            String fieldValue = structuredLine.getValue();
            if (StringUtils.containsAny(key, "评论类型", "类型", "评分", "星级", "评价", "情绪", "sentiment", "score", "rating")
                    || StringUtils.containsAny(fieldValue, "好评", "中评", "差评", "四星", "五星", "三星", "零星", "一星", "二星", "4星", "5星", "3星", "0星", "1星", "2星", "GOOD", "NEUTRAL", "BAD")) {
                CommentReplySentimentEnum sentiment = inferSentiment(fieldValue);
                if (sentiment != CommentReplySentimentEnum.UNKNOWN) {
                    return sentiment;
                }
            }
        }
        CommentReplySentimentEnum inferred = inferSentiment(value);
        return inferred == CommentReplySentimentEnum.UNKNOWN ? null : inferred;
    }

    private List<ParsedDocumentChunk> extractParagraphChunks(String text) {
        if (StringUtils.isBlank(text) || !StringUtils.contains(text, "\n\n")) {
            return List.of();
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        int maxChunks = Math.max(1, knowledge.getMaxChunksPerDocument());
        List<ParsedDocumentChunk> chunks = new ArrayList<>();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String cleanedParagraph = cleanText(paragraph);
            if (!isUsefulParagraph(cleanedParagraph) || isMetaTestParagraph(cleanedParagraph)) {
                continue;
            }
            List<ParsedDocumentChunk> lineChunks = extractRuleLineChunks(cleanedParagraph);
            if (lineChunks.size() >= 2) {
                chunks.addAll(lineChunks);
            } else {
                chunks.add(new ParsedDocumentChunk(cleanedParagraph, null));
            }
            if (chunks.size() >= maxChunks) {
                break;
            }
        }
        List<ParsedDocumentChunk> meaningfulChunks = filterMeaningfulChunks(chunks);
        return meaningfulChunks.size() >= 2 ? meaningfulChunks : List.of();
    }

    private boolean isUsefulParagraph(String paragraph) {
        if (StringUtils.length(paragraph) < 12) {
            return false;
        }
        int chineseOrLetterCount = 0;
        for (int i = 0; i < paragraph.length(); i++) {
            char ch = paragraph.charAt(i);
            if (Character.isLetter(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                chineseOrLetterCount++;
            }
        }
        return chineseOrLetterCount >= 8;
    }

    private boolean isMetaTestParagraph(String paragraph) {
        String value = StringUtils.defaultString(paragraph);
        return StringUtils.containsAny(value,
                "用来测试", "测试情绪", "测试解析", "这份文档故意", "如果系统", "应该分别", "是否容易");
    }

    private List<ParsedDocumentChunk> extractRuleLineChunks(String paragraph) {
        List<ParsedDocumentChunk> chunks = new ArrayList<>();
        String[] lines = StringUtils.splitByWholeSeparatorPreserveAllTokens(paragraph, "\n");
        int usefulLineCount = 0;
        for (String line : lines) {
            String cleanedLine = cleanText(line);
            if (StringUtils.isBlank(cleanedLine)) {
                continue;
            }
            usefulLineCount++;
            if (!isRuleLikeLine(cleanedLine)) {
                return List.of();
            }
            chunks.add(new ParsedDocumentChunk(cleanedLine, null));
        }
        return usefulLineCount >= 2 ? chunks : List.of();
    }

    private boolean isRuleLikeLine(String line) {
        if (!isUsefulParagraph(line) || isMetaTestParagraph(line)) {
            return false;
        }
        String value = StringUtils.trimToEmpty(line);
        return StringUtils.startsWithAny(value, "顾客", "客户", "用户", "如果顾客", "如果客户", "如果用户",
                "当顾客", "当客户", "遇到", "评论", "评价", "场景");
    }

    private List<ParsedDocumentChunk> splitText(String text) {
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("知识文档没有可解析文本");
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        List<ParsedDocumentChunk> result = new ArrayList<>();
        int start = 0;
        while (start < text.length() && result.size() < knowledge.getMaxChunksPerDocument()) {
            int end = Math.min(start + TARGET_CHUNK_CHARS, text.length());
            result.add(new ParsedDocumentChunk(text.substring(start, end).trim(), null));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP_CHARS, start + 1);
        }
        return filterMeaningfulChunks(result.stream()
                .filter(chunk -> StringUtils.isNotBlank(chunk.getText()))
                .collect(Collectors.toList()));
    }

    private List<ParsedDocumentChunk> filterMeaningfulChunks(List<ParsedDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .filter(chunk -> isMeaningfulKnowledgeText(chunk.getText()))
                .collect(Collectors.toList());
    }

    private boolean isMeaningfulKnowledgeText(String text) {
        String value = StringUtils.trimToEmpty(text);
        if (StringUtils.length(value) < 8) {
            return inferSentiment(value) != CommentReplySentimentEnum.UNKNOWN && StringUtils.length(value) >= 6;
        }
        String lower = StringUtils.lowerCase(value, Locale.ROOT);
        if (StringUtils.containsAny(lower, "asdf", "undefined", "nan", "null null", "unknown unknown")) {
            return false;
        }
        int hanCount = 0;
        int letterCount = 0;
        int digitCount = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                hanCount++;
            } else if (Character.isLetter(ch)) {
                letterCount++;
            } else if (Character.isDigit(ch)) {
                digitCount++;
            }
        }
        if (hanCount >= 8) {
            return true;
        }
        if (hanCount < 4 && letterCount + digitCount > hanCount * 3 + 12) {
            return false;
        }
        return StringUtils.containsAny(value,
                "顾客", "客户", "用户", "评论", "评价", "回复", "回答", "好评", "中评", "差评",
                "配送", "包装", "口味", "味道", "漏送", "少送", "满意", "感谢", "抱歉", "门店");
    }

    private List<MtReplyKnowledgeChunk> buildChunks(MtReplyKnowledgeDocument document, List<ParsedDocumentChunk> rawChunks) {
        if (rawChunks == null || rawChunks.isEmpty()) {
            throw new BusinessException("知识文档没有可用片段");
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        LocalDateTime now = LocalDateTime.now();
        List<MtReplyKnowledgeChunk> chunks = new ArrayList<>();
        for (ParsedDocumentChunk rawChunk : rawChunks) {
            String text = rawChunk.getText();
            float[] embedding = commentReplyEmbeddingService.embed(text);
            MtReplyKnowledgeChunk chunk = new MtReplyKnowledgeChunk();
            chunk.setShopId(document.getShopId());
            chunk.setSourceType(CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name());
            chunk.setSourceId(document.getId());
            chunk.setDocumentId(document.getId());
            chunk.setItemId(null);
            CommentReplySentimentEnum sentiment = rawChunk.getSentiment() == null
                    ? inferSentiment(text)
                    : rawChunk.getSentiment();
            chunk.setSentiment(sentiment.name());
            chunk.setChunkText(text);
            chunk.setEmbeddingProvider(knowledge.getEmbeddingProvider());
            chunk.setEmbeddingModel(knowledge.getEmbeddingModel());
            chunk.setEmbeddingDim(embedding.length);
            chunk.setEmbeddingBlob(EmbeddingVectorUtil.toFloat32Blob(embedding));
            chunk.setTokenCount(Math.max(1, text.length() / 2));
            chunk.setEnabled(true);
            chunk.setDeleted(false);
            chunk.setCreateTime(now);
            chunk.setUpdateTime(now);
            chunks.add(chunk);
        }
        return chunks;
    }

    @lombok.Value
    private static class ParsedDocumentChunk {

        String text;
        CommentReplySentimentEnum sentiment;
    }

    @lombok.Value
    private static class StructuredLine {

        String key;
        String value;
    }

    private CommentReplySentimentEnum inferSentiment(String text) {
        String value = StringUtils.defaultString(text);
        if (StringUtils.containsAny(value, "差评", "零星", "0星", "零分", "0分", "一星", "1星", "二星", "2星", "不好", "抱歉", "投诉",
                "少送", "漏送", "撒漏", "洒漏", "不满意", "道歉", "退款", "赔付")) {
            return CommentReplySentimentEnum.BAD;
        }
        if (StringUtils.containsAny(value, "中评", "三星", "3星", "一般", "还行")) {
            return CommentReplySentimentEnum.NEUTRAL;
        }
        if (StringUtils.containsAny(value, "好评", "四星", "4星", "五星", "5星", "感谢", "喜欢")) {
            return CommentReplySentimentEnum.GOOD;
        }
        return CommentReplySentimentEnum.UNKNOWN;
    }

    private CommentReplySentimentEnum requireSentiment(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        try {
            return CommentReplySentimentEnum.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("评论类型不合法");
        }
    }

    private void addIfNotBlank(List<String> list, String value) {
        if (StringUtils.isNotBlank(value)) {
            list.add(StringUtils.trim(value));
        }
    }

    private CommentReplyKnowledgeDocumentVO toDocumentVO(MtReplyKnowledgeDocument document) {
        CommentReplyKnowledgeDocumentVO vo = new CommentReplyKnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setShopId(document.getShopId());
        vo.setFileName(document.getFileName());
        vo.setFileType(document.getFileType());
        vo.setFileSize(document.getFileSize());
        vo.setParseStatus(document.getParseStatus());
        vo.setParseError(document.getParseError());
        vo.setChunkCount(document.getChunkCount());
        vo.setEnabled(document.getEnabled());
        vo.setCreateTime(document.getCreateTime());
        vo.setUpdateTime(document.getUpdateTime());
        return vo;
    }

    private CommentReplyKnowledgeChunkVO toChunkVO(MtReplyKnowledgeChunk chunk) {
        CommentReplyKnowledgeChunkVO vo = new CommentReplyKnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setShopId(chunk.getShopId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setSourceType(chunk.getSourceType());
        vo.setSentiment(chunk.getSentiment());
        vo.setChunkText(chunk.getChunkText());
        vo.setEnabled(chunk.getEnabled());
        vo.setCreateTime(chunk.getCreateTime());
        vo.setUpdateTime(chunk.getUpdateTime());
        return vo;
    }
}
