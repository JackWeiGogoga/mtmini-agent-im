package com.gogoga.backend.service.impl;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtReplyKnowledgeItem;
import com.gogoga.backend.mapper.MtReplyKnowledgeItemMapper;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.CommentReplyOfficialTemplateImportVO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCommentReplyKnowledgeServiceImplTest {

    @Mock
    private MtReplyKnowledgeItemMapper mtReplyKnowledgeItemMapper;

    @InjectMocks
    private AdminCommentReplyKnowledgeServiceImpl adminCommentReplyKnowledgeService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void shouldImportOfficialTemplatesFromExcelColumns() throws Exception {
        mockSuperAdmin();
        MtReplyKnowledgeItem existingGood = officialItem(1L, "GOOD", "质量很好，欢迎下次再来");
        existingGood.setEnabled(false);
        existingGood.setDeleted(true);
        when(mtReplyKnowledgeItemMapper.selectOfficialTemplatesIncludingDeleted("GOOD"))
                .thenReturn(List.of(existingGood));
        when(mtReplyKnowledgeItemMapper.selectOfficialTemplatesIncludingDeleted("NEUTRAL"))
                .thenReturn(List.of());
        when(mtReplyKnowledgeItemMapper.selectOfficialTemplatesIncludingDeleted("BAD"))
                .thenReturn(List.of());

        MockMultipartFile file = buildExcelFile();

        CommentReplyOfficialTemplateImportVO result = adminCommentReplyKnowledgeService.importOfficialTemplates(file);

        assertEquals(5, result.getTotalCount());
        assertEquals(4, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(1, result.getDuplicateCount());
        assertEquals(2, result.getGoodCount());
        assertEquals(2, result.getNeutralCount());
        assertEquals(1, result.getBadCount());
        assertTrue(existingGood.getEnabled());
        assertFalse(existingGood.getDeleted());

        ArgumentCaptor<MtReplyKnowledgeItem> insertCaptor = ArgumentCaptor.forClass(MtReplyKnowledgeItem.class);
        verify(mtReplyKnowledgeItemMapper).updateById(existingGood);
        verify(mtReplyKnowledgeItemMapper, org.mockito.Mockito.times(4)).insert(insertCaptor.capture());
        List<MtReplyKnowledgeItem> inserted = insertCaptor.getAllValues();
        assertEquals("GOOD", inserted.get(0).getSentiment());
        assertEquals("第二条好评回复", inserted.get(0).getAnswer());
        assertEquals("NEUTRAL", inserted.get(1).getSentiment());
        assertEquals("中评解释一", inserted.get(1).getAnswer());
        assertEquals("BAD", inserted.get(3).getSentiment());
        assertEquals("差评安抚一", inserted.get(3).getAnswer());
    }

    private MockMultipartFile buildExcelFile() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("评价回复-新颖");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("好评回复");
            header.createCell(1).setCellValue("中评回复");
            header.createCell(2).setCellValue("差评回复");
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("质量很好，欢迎下次再来");
            row1.createCell(1).setCellValue("中评解释一");
            row1.createCell(2).setCellValue("差评安抚一");
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("质量很好，欢迎下次再来");
            row2.createCell(1).setCellValue("中评解释二");
            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("第二条好评回复");
            workbook.write(outputStream);
            return new MockMultipartFile(
                    "file",
                    "templates.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    outputStream.toByteArray()
            );
        }
    }

    private MtReplyKnowledgeItem officialItem(Long id, String sentiment, String answer) {
        MtReplyKnowledgeItem item = new MtReplyKnowledgeItem();
        item.setId(id);
        item.setScope("OFFICIAL");
        item.setSourceType("TEMPLATE");
        item.setSentiment(sentiment);
        item.setTitle("旧标题");
        item.setAnswer(answer);
        item.setEnabled(true);
        item.setDeleted(false);
        return item;
    }

    private void mockSuperAdmin() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setUserName("系统管理员");
        loginUser.setRoles(Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);
    }
}
