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
        return mtReplyKnowledgeDocumentMapper.selectByShopId(shop.getId()).stream()
                .map(this::toDocumentVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeDocumentVO uploadCurrentShopDocument(MultipartFile file) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        validateUpload(file, shop.getId(), knowledge);
        String fileType = resolveFileType(file);
        CosStorageService.StoredObject storedObject = cosStorageService.uploadObject(
                knowledge.getDocumentKeyPrefix(),
                String.valueOf(shop.getId()),
                file
        );
        LocalDateTime now = LocalDateTime.now();
        MtReplyKnowledgeDocument document = new MtReplyKnowledgeDocument();
        document.setShopId(shop.getId());
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
        MtReplyKnowledgeDocument document = requireCurrentShopDocument(id, shop.getId());
        mtReplyKnowledgeDocumentMapper.softDeleteByIdAndShopId(document.getId(), shop.getId(), LocalDateTime.now());
        knowledgeVectorStore.deleteBySource(CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name(), document.getId());
        cosStorageService.deleteObject(document.getObjectKey());
    }

    @Override
    public List<CommentReplyKnowledgeChunkVO> listCurrentShopDocumentChunks(Long documentId) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        requireCurrentShopDocument(documentId, shop.getId());
        return mtReplyKnowledgeChunkMapper.selectByDocumentIdAndShopId(documentId, shop.getId()).stream()
                .map(this::toChunkVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeChunkVO updateCurrentShopChunkSentiment(Long chunkId, CommentReplyKnowledgeChunkUpdateParam param) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        CommentReplySentimentEnum sentiment = requireSentiment(param.getSentiment());
        int rows = mtReplyKnowledgeChunkMapper.updateSentimentByIdAndShopId(chunkId, shop.getId(), sentiment.name(), LocalDateTime.now());
        if (rows <= 0) {
            throw new BusinessException(404, "知识片段不存在");
        }
        return toChunkVO(mtReplyKnowledgeChunkMapper.selectByIdAndShopId(chunkId, shop.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentReplyKnowledgeChunkVO updateCurrentShopChunkEnabled(Long chunkId, Boolean enabled) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        int rows = mtReplyKnowledgeChunkMapper.updateEnabledByIdAndShopId(chunkId, shop.getId(), Boolean.TRUE.equals(enabled), LocalDateTime.now());
        if (rows <= 0) {
            throw new BusinessException(404, "知识片段不存在");
        }
        return toChunkVO(mtReplyKnowledgeChunkMapper.selectByIdAndShopId(chunkId, shop.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopChunk(Long chunkId) {
        MtShop shop = mtH5ShopService.getCurrentShop();
        int rows = mtReplyKnowledgeChunkMapper.softDeleteByIdAndShopId(chunkId, shop.getId(), LocalDateTime.now());
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
            List<String> rawChunks = extractChunks(document.getFileType(), content);
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

    private List<String> extractChunks(String fileType, byte[] content) throws Exception {
        String text = switch (StringUtils.defaultString(fileType)) {
            case "TXT" -> new String(content, StandardCharsets.UTF_8);
            case "DOCX" -> extractDocxText(content);
            case "XLSX" -> extractXlsxText(content);
            default -> throw new BusinessException("不支持的知识文档格式");
        };
        return splitText(cleanText(text));
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

    private String extractXlsxText(byte[] content) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            List<String> lines = new ArrayList<>();
            for (Sheet sheet : workbook) {
                addIfNotBlank(lines, "Sheet：" + sheet.getSheetName());
                List<String> headers = null;
                boolean structured = false;
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        addIfNotBlank(cells, cell.toString());
                    }
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

    private List<String> splitText(String text) {
        if (StringUtils.isBlank(text)) {
            throw new BusinessException("知识文档没有可解析文本");
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length() && result.size() < knowledge.getMaxChunksPerDocument()) {
            int end = Math.min(start + TARGET_CHUNK_CHARS, text.length());
            result.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP_CHARS, start + 1);
        }
        return result.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
    }

    private List<MtReplyKnowledgeChunk> buildChunks(MtReplyKnowledgeDocument document, List<String> rawChunks) {
        if (rawChunks == null || rawChunks.isEmpty()) {
            throw new BusinessException("知识文档没有可用片段");
        }
        MtProperties.Knowledge knowledge = mtProperties.getComment().getKnowledge();
        LocalDateTime now = LocalDateTime.now();
        List<MtReplyKnowledgeChunk> chunks = new ArrayList<>();
        for (String text : rawChunks) {
            float[] embedding = commentReplyEmbeddingService.embed(text);
            MtReplyKnowledgeChunk chunk = new MtReplyKnowledgeChunk();
            chunk.setShopId(document.getShopId());
            chunk.setSourceType(CommentReplyKnowledgeSourceTypeEnum.DOCUMENT.name());
            chunk.setSourceId(document.getId());
            chunk.setDocumentId(document.getId());
            chunk.setItemId(null);
            chunk.setSentiment(inferSentiment(text).name());
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

    private CommentReplySentimentEnum inferSentiment(String text) {
        String value = StringUtils.defaultString(text);
        if (StringUtils.containsAny(value, "差评", "一星", "1星", "二星", "2星", "不好", "抱歉", "投诉")) {
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
