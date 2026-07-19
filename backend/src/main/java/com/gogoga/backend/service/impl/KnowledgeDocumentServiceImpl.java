package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtKnowledgeDocument;
import com.gogoga.backend.enums.KnowledgeDocumentParseStatusEnum;
import com.gogoga.backend.enums.KnowledgeSourceTypeEnum;
import com.gogoga.backend.enums.MtAsyncTaskTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtKnowledgeDocumentMapper;
import com.gogoga.backend.service.CosStorageService;
import com.gogoga.backend.service.KnowledgeDocumentService;
import com.gogoga.backend.service.KnowledgeEmbeddingService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.SensitiveWordService;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import com.gogoga.backend.util.EmbeddingVectorUtil;
import com.gogoga.backend.vo.KnowledgeChunkVO;
import com.gogoga.backend.vo.KnowledgeDocumentPreviewVO;
import com.gogoga.backend.vo.KnowledgeDocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
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

/**
 * 通用门店知识文档服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final String STORAGE_PROVIDER_COS = "TENCENT_COS";
    private static final int TARGET_CHUNK_CHARS = 800;
    private static final int CHUNK_OVERLAP_CHARS = 120;
    private static final int PREVIEW_CHUNK_LIMIT = 20;

    private final MtH5ShopService mtH5ShopService;
    private final MtKnowledgeDocumentMapper mtKnowledgeDocumentMapper;
    private final MtKnowledgeChunkMapper mtKnowledgeChunkMapper;
    private final CosStorageService cosStorageService;
    private final MtAsyncTaskService mtAsyncTaskService;
    private final MtProperties mtProperties;
    private final KnowledgeVectorStore knowledgeVectorStore;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final SensitiveWordService sensitiveWordService;

    @Override
    public List<KnowledgeDocumentVO> listCurrentShopDocuments() {
        return listShopDocuments(mtH5ShopService.getCurrentShop().getId());
    }

    @Override
    public List<KnowledgeDocumentVO> listShopDocuments(Long shopId) {
        requireShopId(shopId);
        return mtKnowledgeDocumentMapper.selectByShopId(shopId).stream().map(this::toDocumentVO).collect(Collectors.toList());
    }

    @Override
    public KnowledgeDocumentPreviewVO previewCurrentShopDocument(MultipartFile file) {
        return previewShopDocument(mtH5ShopService.getCurrentShop().getId(), file);
    }

    @Override
    public KnowledgeDocumentPreviewVO previewShopDocument(Long shopId, MultipartFile file) {
        requireShopId(shopId);
        validateUpload(file, shopId, false);
        String fileType = resolveFileType(file);
        try {
            return buildPreview(file, fileType, extractChunks(fileType, file.getBytes()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("知识文档解析预览失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO uploadCurrentShopDocument(MultipartFile file) {
        return uploadShopDocument(mtH5ShopService.getCurrentShop().getId(), file);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO uploadShopDocument(Long shopId, MultipartFile file) {
        requireShopId(shopId);
        validateUpload(file, shopId, true);
        String fileType = resolveFileType(file);
        CosStorageService.StoredObject storedObject = cosStorageService.uploadObject(
                mtProperties.getKnowledge().getDocumentKeyPrefix(), String.valueOf(shopId), file);
        LocalDateTime now = LocalDateTime.now();
        MtKnowledgeDocument document = new MtKnowledgeDocument();
        document.setShopId(shopId);
        document.setFileName(StringUtils.defaultIfBlank(file.getOriginalFilename(), "knowledge." + fileType.toLowerCase(Locale.ROOT)));
        document.setFileType(fileType);
        document.setFileSize(file.getSize());
        document.setStorageProvider(STORAGE_PROVIDER_COS);
        document.setObjectKey(storedObject.getObjectKey());
        document.setParseStatus(KnowledgeDocumentParseStatusEnum.PENDING.name());
        document.setChunkCount(0);
        document.setEnabled(true);
        document.setDeleted(false);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        mtKnowledgeDocumentMapper.insert(document);
        enqueueParseTask(document);
        return toDocumentVO(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopDocument(Long id) {
        deleteShopDocument(mtH5ShopService.getCurrentShop().getId(), id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShopDocument(Long shopId, Long id) {
        MtKnowledgeDocument document = requireCurrentShopDocument(id, shopId);
        mtKnowledgeDocumentMapper.softDeleteByIdAndShopId(id, shopId, LocalDateTime.now());
        knowledgeVectorStore.deleteBySource(KnowledgeSourceTypeEnum.DOCUMENT.name(), id);
        cosStorageService.deleteObject(document.getObjectKey());
    }

    @Override
    public List<KnowledgeChunkVO> listCurrentShopDocumentChunks(Long documentId) {
        return listShopDocumentChunks(mtH5ShopService.getCurrentShop().getId(), documentId);
    }

    @Override
    public List<KnowledgeChunkVO> listShopDocumentChunks(Long shopId, Long documentId) {
        requireCurrentShopDocument(documentId, shopId);
        return mtKnowledgeChunkMapper.selectByDocumentIdAndShopId(documentId, shopId)
                .stream().map(this::toChunkVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkVO updateCurrentShopChunkEnabled(Long chunkId, Boolean enabled) {
        return updateShopChunkEnabled(mtH5ShopService.getCurrentShop().getId(), chunkId, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeChunkVO updateShopChunkEnabled(Long shopId, Long chunkId, Boolean enabled) {
        requireShopId(shopId);
        if (mtKnowledgeChunkMapper.updateEnabledByIdAndShopId(
                chunkId, shopId, Boolean.TRUE.equals(enabled), LocalDateTime.now()) <= 0) {
            throw new BusinessException(404, "知识切片不存在");
        }
        return toChunkVO(mtKnowledgeChunkMapper.selectByIdAndShopId(chunkId, shopId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentShopChunk(Long chunkId) {
        deleteShopChunk(mtH5ShopService.getCurrentShop().getId(), chunkId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShopChunk(Long shopId, Long chunkId) {
        requireShopId(shopId);
        if (mtKnowledgeChunkMapper.softDeleteByIdAndShopId(chunkId, shopId, LocalDateTime.now()) <= 0) {
            throw new BusinessException(404, "知识切片不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeDocumentParseTask(MtKnowledgeDocumentParseTaskPayload payload) {
        if (payload == null || payload.getShopId() == null || payload.getDocumentId() == null) {
            throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_PARSE_PAYLOAD_INVALID");
        }
        MtKnowledgeDocument document = mtKnowledgeDocumentMapper.selectByIdAndShopId(
                payload.getDocumentId(), payload.getShopId());
        if (document == null) {
            return;
        }
        mtKnowledgeDocumentMapper.updateParseStatus(document.getId(),
                KnowledgeDocumentParseStatusEnum.PARSING.name(), null, 0, LocalDateTime.now());
        try {
            byte[] content = cosStorageService.loadObject(document.getObjectKey()).getContent();
            List<MtKnowledgeChunk> chunks = buildChunks(document, extractChunks(document.getFileType(), content));
            knowledgeVectorStore.deleteBySource(KnowledgeSourceTypeEnum.DOCUMENT.name(), document.getId());
            knowledgeVectorStore.upsertChunks(chunks);
            mtKnowledgeDocumentMapper.updateParseStatus(document.getId(),
                    KnowledgeDocumentParseStatusEnum.SUCCESS.name(), null, chunks.size(), LocalDateTime.now());
        } catch (Exception e) {
            String safeError = "DOCUMENT_PARSE_FAILED:" + e.getClass().getSimpleName();
            mtKnowledgeDocumentMapper.updateParseStatus(document.getId(),
                    KnowledgeDocumentParseStatusEnum.FAILED.name(), safeError, 0, LocalDateTime.now());
            throw new IllegalStateException(safeError, e);
        }
    }

    private void enqueueParseTask(MtKnowledgeDocument document) {
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

    private void validateUpload(MultipartFile file, Long shopId, boolean enforceDocumentLimit) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择知识文档");
        }
        resolveFileType(file);
        MtProperties.Knowledge knowledge = mtProperties.getKnowledge();
        if (file.getSize() > knowledge.getMaxDocumentBytes()) {
            throw new BusinessException("知识文档超过大小限制");
        }
        if (enforceDocumentLimit
                && mtKnowledgeDocumentMapper.countAvailableByShopId(shopId) >= knowledge.getMaxDocumentsPerShop()) {
            throw new BusinessException("每个商户最多上传 " + knowledge.getMaxDocumentsPerShop() + " 个知识文档");
        }
    }

    private String resolveFileType(MultipartFile file) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(file.getOriginalFilename()), Locale.ROOT);
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

    private List<String> extractChunks(String fileType, byte[] content) throws Exception {
        String text = switch (StringUtils.defaultString(fileType)) {
            case "TXT" -> new String(content, StandardCharsets.UTF_8);
            case "DOCX" -> extractDocxText(content);
            case "XLSX" -> extractXlsxText(content);
            default -> throw new BusinessException("不支持的知识文档格式");
        };
        String cleaned = cleanText(text);
        if (StringUtils.isBlank(cleaned)) {
            throw new BusinessException("知识文档内容为空");
        }
        int maxChunks = Math.max(1, mtProperties.getKnowledge().getMaxChunksPerDocument());
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < cleaned.length() && chunks.size() < maxChunks) {
            int end = Math.min(cleaned.length(), start + TARGET_CHUNK_CHARS);
            chunks.add(cleaned.substring(start, end).trim());
            if (end >= cleaned.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP_CHARS);
        }
        return chunks.stream().filter(StringUtils::isNotBlank).toList();
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
        DataFormatter formatter = new DataFormatter();
        List<String> lines = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> addIfNotBlank(cells, formatter.formatCellValue(cell)));
                    addIfNotBlank(lines, String.join(" | ", cells));
                }
            }
        }
        return String.join("\n", lines);
    }

    private List<MtKnowledgeChunk> buildChunks(MtKnowledgeDocument document, List<String> rawChunks) {
        MtProperties.Knowledge knowledge = mtProperties.getKnowledge();
        LocalDateTime now = LocalDateTime.now();
        List<MtKnowledgeChunk> chunks = new ArrayList<>();
        for (String text : rawChunks) {
            sensitiveWordService.validateKnowledgeText(
                    document.getShopId(), KnowledgeSourceTypeEnum.DOCUMENT.name(), text);
            float[] embedding = knowledgeEmbeddingService.embed(text);
            MtKnowledgeChunk chunk = new MtKnowledgeChunk();
            chunk.setShopId(document.getShopId());
            chunk.setSourceType(KnowledgeSourceTypeEnum.DOCUMENT.name());
            chunk.setSourceId(document.getId());
            chunk.setDocumentId(document.getId());
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

    private KnowledgeDocumentPreviewVO buildPreview(MultipartFile file, String fileType, List<String> rawChunks) {
        KnowledgeDocumentPreviewVO preview = new KnowledgeDocumentPreviewVO();
        preview.setFileName(file.getOriginalFilename());
        preview.setFileType(fileType);
        preview.setFileSize(file.getSize());
        preview.setChunkCount(rawChunks.size());
        List<KnowledgeDocumentPreviewVO.ChunkPreview> chunks = new ArrayList<>();
        for (int i = 0; i < Math.min(rawChunks.size(), PREVIEW_CHUNK_LIMIT); i++) {
            KnowledgeDocumentPreviewVO.ChunkPreview chunk = new KnowledgeDocumentPreviewVO.ChunkPreview();
            chunk.setIndex(i + 1);
            chunk.setChunkText(rawChunks.get(i));
            chunks.add(chunk);
        }
        preview.setChunks(chunks);
        return preview;
    }

    private MtKnowledgeDocument requireCurrentShopDocument(Long id, Long shopId) {
        requireShopId(shopId);
        MtKnowledgeDocument document = id == null ? null : mtKnowledgeDocumentMapper.selectByIdAndShopId(id, shopId);
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

    private String cleanText(String text) {
        return StringUtils.defaultString(text).replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private void addIfNotBlank(List<String> target, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(StringUtils.trim(value));
        }
    }

    private KnowledgeDocumentVO toDocumentVO(MtKnowledgeDocument document) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
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

    private KnowledgeChunkVO toChunkVO(MtKnowledgeChunk chunk) {
        if (chunk == null) {
            return null;
        }
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setShopId(chunk.getShopId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setSourceType(chunk.getSourceType());
        vo.setChunkText(chunk.getChunkText());
        vo.setEnabled(chunk.getEnabled());
        vo.setCreateTime(chunk.getCreateTime());
        vo.setUpdateTime(chunk.getUpdateTime());
        return vo;
    }
}
