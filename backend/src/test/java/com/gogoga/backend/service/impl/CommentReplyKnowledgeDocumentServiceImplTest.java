package com.gogoga.backend.service.impl;

import com.gogoga.backend.config.MtProperties;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeChunk;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeDocument;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtReplyKnowledgeChunkMapper;
import com.gogoga.backend.mapper.MtReplyKnowledgeDocumentMapper;
import com.gogoga.backend.param.CommentReplyKnowledgeChunkUpdateParam;
import com.gogoga.backend.service.CommentReplyEmbeddingService;
import com.gogoga.backend.service.CosStorageService;
import com.gogoga.backend.service.KnowledgeVectorStore;
import com.gogoga.backend.service.MtAsyncTaskService;
import com.gogoga.backend.service.MtH5ShopService;
import com.gogoga.backend.service.async.MtKnowledgeDocumentParseTaskPayload;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentReplyKnowledgeDocumentServiceImplTest {

    @Mock
    private MtH5ShopService mtH5ShopService;
    @Mock
    private MtReplyKnowledgeDocumentMapper mtReplyKnowledgeDocumentMapper;
    @Mock
    private MtReplyKnowledgeChunkMapper mtReplyKnowledgeChunkMapper;
    @Mock
    private CosStorageService cosStorageService;
    @Mock
    private MtAsyncTaskService mtAsyncTaskService;
    @Mock
    private KnowledgeVectorStore knowledgeVectorStore;
    @Mock
    private CommentReplyEmbeddingService commentReplyEmbeddingService;

    private MtProperties mtProperties;
    private CommentReplyKnowledgeDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        MtShop shop = new MtShop();
        shop.setId(100L);
        lenient().when(mtH5ShopService.getCurrentShop()).thenReturn(shop);
        mtProperties = new MtProperties();
        service = new CommentReplyKnowledgeDocumentServiceImpl(
                mtH5ShopService,
                mtReplyKnowledgeDocumentMapper,
                mtReplyKnowledgeChunkMapper,
                cosStorageService,
                mtAsyncTaskService,
                mtProperties,
                knowledgeVectorStore,
                commentReplyEmbeddingService
        );
    }

    @Test
    void uploadRejectsUnsupportedFormatDocumentLimitAndSizeLimit() {
        assertThrows(BusinessException.class, () -> service.uploadCurrentShopDocument(file("bad.pdf", new byte[]{1})));

        when(mtReplyKnowledgeDocumentMapper.countAvailableByShopId(100L)).thenReturn(10);
        assertThrows(BusinessException.class, () -> service.uploadCurrentShopDocument(file("ok.txt", "x".getBytes(StandardCharsets.UTF_8))));

        mtProperties.getComment().getKnowledge().setMaxDocumentBytes(1L);
        assertThrows(BusinessException.class, () -> service.uploadCurrentShopDocument(file("big.txt", "xx".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void uploadSavesToCosAndEnqueuesParseTask() {
        when(mtReplyKnowledgeDocumentMapper.countAvailableByShopId(100L)).thenReturn(0);
        when(cosStorageService.uploadObject(any(), eq("100"), any()))
                .thenReturn(CosStorageService.StoredObject.builder().objectKey("knowledge/100/a.txt").build());
        doAnswer(invocation -> {
            MtReplyKnowledgeDocument document = invocation.getArgument(0);
            document.setId(7L);
            return 1;
        }).when(mtReplyKnowledgeDocumentMapper).insert(any(MtReplyKnowledgeDocument.class));

        service.uploadCurrentShopDocument(file("ok.txt", "好评 感谢".getBytes(StandardCharsets.UTF_8)));

        ArgumentCaptor<MtKnowledgeDocumentParseTaskPayload> payloadCaptor =
                ArgumentCaptor.forClass(MtKnowledgeDocumentParseTaskPayload.class);
        verify(mtAsyncTaskService).enqueueTask(eq(com.gogoga.backend.enums.MtAsyncTaskTypeEnum.KNOWLEDGE_DOCUMENT_PARSE),
                eq("knowledge-document:7"), eq("100"), payloadCaptor.capture(), any(), anyInt());
        assertEquals(7L, payloadCaptor.getValue().getDocumentId());
    }

    @Test
    void parsesTxtDocxAndXlsxIntoChunks() throws Exception {
        assertParses("TXT", "ok.txt", "好评 感谢喜欢".getBytes(StandardCharsets.UTF_8), "GOOD");
        assertParses("DOCX", "ok.docx", docxBytes("中评 一般还行"), "NEUTRAL");
        assertParses("XLSX", "ok.xlsx", xlsxBytes(), "BAD");
    }

    @Test
    void parsesSentimentTemplateXlsxIntoColumnChunks() throws Exception {
        MtReplyKnowledgeDocument document = document("XLSX", "template.xlsx");
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/template.xlsx"))
                .thenReturn(CosStorageService.StoredObjectContent.builder().content(sentimentTemplateXlsxBytes()).fileName("template.xlsx").build());
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        List<MtReplyKnowledgeChunk> chunks = captureUpsertedChunks();
        assertEquals(9, chunks.size());
        assertEquals("GOOD", chunks.get(0).getSentiment());
        assertEquals("NEUTRAL", chunks.get(1).getSentiment());
        assertEquals("BAD", chunks.get(2).getSentiment());
        assertEquals("GOOD", chunks.get(3).getSentiment());
        assertEquals("NEUTRAL", chunks.get(4).getSentiment());
        assertEquals("BAD", chunks.get(5).getSentiment());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(0).getChunkText().startsWith("好评回复（四星五星）："));
    }

    @Test
    void parsesStructuredTxtQaBlocksIntoSeparateChunks() {
        MtReplyKnowledgeDocument document = document("TXT", "qa.txt");
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/qa.txt"))
                .thenReturn(CosStorageService.StoredObjectContent.builder()
                        .content(structuredQaText().getBytes(StandardCharsets.UTF_8))
                        .fileName("qa.txt")
                        .build());
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        List<MtReplyKnowledgeChunk> chunks = captureUpsertedChunks();
        assertEquals(2, chunks.size());
        assertEquals("NEUTRAL", chunks.get(0).getSentiment());
        assertEquals("BAD", chunks.get(1).getSentiment());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(0).getChunkText().contains("标题：配送时间说明"));
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(1).getChunkText().contains("标题：包装破损处理"));
    }

    @Test
    void parsesRepeatedCustomStructureIntoSeparateChunks() {
        MtReplyKnowledgeDocument document = document("TXT", "custom.txt");
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/custom.txt"))
                .thenReturn(CosStorageService.StoredObjectContent.builder()
                        .content(customStructuredText().getBytes(StandardCharsets.UTF_8))
                        .fileName("custom.txt")
                        .build());
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        List<MtReplyKnowledgeChunk> chunks = captureUpsertedChunks();
        assertEquals(2, chunks.size());
        assertEquals("GOOD", chunks.get(0).getSentiment());
        assertEquals("BAD", chunks.get(1).getSentiment());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(0).getChunkText().contains("场景=味道被夸"));
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(1).getChunkText().contains("场景=漏送投诉"));
    }

    @Test
    void parsesFreeTextParagraphsIntoSeparateChunks() {
        MtReplyKnowledgeDocument document = document("TXT", "free.txt");
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/free.txt"))
                .thenReturn(CosStorageService.StoredObjectContent.builder()
                        .content(freeTextParagraphs().getBytes(StandardCharsets.UTF_8))
                        .fileName("free.txt")
                        .build());
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        List<MtReplyKnowledgeChunk> chunks = captureUpsertedChunks();
        assertEquals(3, chunks.size());
        assertEquals("GOOD", chunks.get(0).getSentiment());
        assertEquals("NEUTRAL", chunks.get(1).getSentiment());
        assertEquals("BAD", chunks.get(2).getSentiment());
    }

    @Test
    void skipsMetaParagraphsAndSplitsMixedEmotionRuleLines() {
        MtReplyKnowledgeDocument document = document("TXT", "mixed.txt");
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/mixed.txt"))
                .thenReturn(CosStorageService.StoredObjectContent.builder()
                        .content(mixedEmotionText().getBytes(StandardCharsets.UTF_8))
                        .fileName("mixed.txt")
                        .build());
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        List<MtReplyKnowledgeChunk> chunks = captureUpsertedChunks();
        assertEquals(3, chunks.size());
        assertEquals("GOOD", chunks.get(0).getSentiment());
        assertEquals("NEUTRAL", chunks.get(1).getSentiment());
        assertEquals("BAD", chunks.get(2).getSentiment());
        org.junit.jupiter.api.Assertions.assertTrue(chunks.get(0).getChunkText().startsWith("顾客说很好吃"));
    }

    @Test
    void meaninglessTextFailsWithoutGeneratingChunks() {
        MtReplyKnowledgeDocument document = document("TXT", "meaningless.txt");
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/meaningless.txt"))
                .thenReturn(CosStorageService.StoredObjectContent.builder()
                        .content(meaninglessText().getBytes(StandardCharsets.UTF_8))
                        .fileName("meaningless.txt")
                        .build());

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        verify(knowledgeVectorStore, org.mockito.Mockito.never()).upsertChunks(any());
        verify(mtReplyKnowledgeDocumentMapper).updateParseStatus(eq(7L), eq("FAILED"), eq("知识文档没有可用片段"), eq(0), any());
    }

    @Test
    void updatesChunkSentimentEnabledAndDeleteByCurrentShop() {
        CommentReplyKnowledgeChunkUpdateParam param = new CommentReplyKnowledgeChunkUpdateParam();
        param.setSentiment("BAD");
        MtReplyKnowledgeChunk chunk = chunk(5L, "BAD");
        when(mtReplyKnowledgeChunkMapper.updateSentimentByIdAndShopId(eq(5L), eq(100L), eq("BAD"), any())).thenReturn(1);
        when(mtReplyKnowledgeChunkMapper.selectByIdAndShopId(5L, 100L)).thenReturn(chunk);
        assertEquals("BAD", service.updateCurrentShopChunkSentiment(5L, param).getSentiment());

        when(mtReplyKnowledgeChunkMapper.updateEnabledByIdAndShopId(eq(5L), eq(100L), eq(false), any())).thenReturn(1);
        service.updateCurrentShopChunkEnabled(5L, false);

        when(mtReplyKnowledgeChunkMapper.softDeleteByIdAndShopId(eq(5L), eq(100L), any())).thenReturn(1);
        service.deleteCurrentShopChunk(5L);
    }

    private void assertParses(String fileType, String filename, byte[] bytes, String expectedSentiment) {
        MtReplyKnowledgeDocument document = document(fileType, filename);
        when(mtReplyKnowledgeDocumentMapper.selectByIdAndShopId(7L, 100L)).thenReturn(document);
        when(cosStorageService.loadObject("knowledge/100/" + filename))
                .thenReturn(CosStorageService.StoredObjectContent.builder().content(bytes).fileName(filename).build());
        when(commentReplyEmbeddingService.embed(any())).thenReturn(new float[]{1F, 0F});

        MtKnowledgeDocumentParseTaskPayload payload = new MtKnowledgeDocumentParseTaskPayload();
        payload.setShopId(100L);
        payload.setDocumentId(7L);
        service.executeDocumentParseTask(payload);

        assertEquals(expectedSentiment, captureUpsertedChunks().get(0).getSentiment());
        clearInvocations(knowledgeVectorStore, mtReplyKnowledgeDocumentMapper, cosStorageService, commentReplyEmbeddingService);
    }

    private List<MtReplyKnowledgeChunk> captureUpsertedChunks() {
        ArgumentCaptor<List<MtReplyKnowledgeChunk>> chunksCaptor = replyKnowledgeChunkListCaptor();
        verify(knowledgeVectorStore).upsertChunks(chunksCaptor.capture());
        return chunksCaptor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<MtReplyKnowledgeChunk>> replyKnowledgeChunkListCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }

    private MtReplyKnowledgeDocument document(String fileType, String filename) {
        MtReplyKnowledgeDocument document = new MtReplyKnowledgeDocument();
        document.setId(7L);
        document.setShopId(100L);
        document.setFileName(filename);
        document.setFileType(fileType);
        document.setObjectKey("knowledge/100/" + filename);
        document.setDeleted(false);
        return document;
    }

    private MtReplyKnowledgeChunk chunk(Long id, String sentiment) {
        MtReplyKnowledgeChunk chunk = new MtReplyKnowledgeChunk();
        chunk.setId(id);
        chunk.setShopId(100L);
        chunk.setDocumentId(7L);
        chunk.setSourceType("DOCUMENT");
        chunk.setSentiment(sentiment);
        chunk.setChunkText("text");
        chunk.setEnabled(true);
        return chunk;
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private byte[] docxBytes(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] xlsxBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("知识");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("评论");
            header.createCell(1).setCellValue("回复");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("差评 投诉不好");
            row.createCell(1).setCellValue("抱歉改进");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] sentimentTemplateXlsxBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("评价回复-新颖");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("好评回复（四星五星）");
            header.createCell(1).setCellValue("中评回复三星");
            header.createCell(2).setCellValue("差评回复一星二星");
            for (int i = 1; i <= 3; i++) {
                var row = sheet.createRow(i);
                row.createCell(0).setCellValue("好评文案" + i);
                row.createCell(1).setCellValue("中评文案" + i);
                row.createCell(2).setCellValue("差评文案" + i);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String structuredQaText() {
        return """
                标题：配送时间说明
                评论类型：中评
                问题：顾客问为什么配送这么慢，餐什么时候送到？
                回答：亲，非常抱歉让您久等了。我们会继续优化出餐和配送衔接。

                标题：包装破损处理
                评论类型：差评
                问题：顾客反馈包装破了，汤洒了。
                回答：亲，非常抱歉给您带来不好的体验。我们会立即检查包装流程。
                """;
    }

    private String customStructuredText() {
        return """
                场景=味道被夸
                星级=5星
                顾客表达=很好吃，下次还来
                处理建议=感谢认可，欢迎下次光临

                场景=漏送投诉
                星级=1星
                顾客表达=少送了东西，非常不满意
                处理建议=先道歉，再说明会核查并改进
                """;
    }

    private String freeTextParagraphs() {
        return """
                如果顾客夸味道好、分量足、送得快，可以表达感谢，并邀请下次继续光临。

                如果顾客说包装一般，但是整体还可以，可以先感谢反馈，再说明我们会改进包装细节。

                如果顾客明确表示菜品少送、漏送、撒漏、口味严重不满意，要先道歉，再说明会反馈门店核查和优化。
                """;
    }

    private String mixedEmotionText() {
        return """
                这份文档故意把好评、中评、差评混在一段里，用来测试情绪识别是否容易偏向差评。

                顾客说很好吃、五星好评、非常喜欢。
                顾客又说配送慢、包装一般、三星中评。
                顾客还说漏送、差评、一星、非常抱歉、投诉。

                如果系统只能生成一个片段，这段大概率会被识别成差评；如果拆分更细，应该分别进入不同情绪。
                """;
    }

    private String meaninglessText() {
        return """
                asdfasdfasdf
                123123123123
                ！！！！！！！！！！！
                、、、、、、、、、、、
                火星菜单 蓝色香蕉 门店量子波动
                GOOD BAD NEUTRAL UNKNOWN UNKNOWN
                null null undefined NaN
                """;
    }
}
