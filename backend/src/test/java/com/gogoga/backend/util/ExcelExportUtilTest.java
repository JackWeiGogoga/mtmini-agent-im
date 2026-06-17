package com.gogoga.backend.util;

import com.gogoga.backend.exception.BusinessException;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExcelExportUtilTest {

    @Test
    void shouldGenerateReadableXlsxWorksheetXml() throws Exception {
        byte[] content = ExcelExportUtil.toXlsx(
                "测试",
                List.of(
                        ExcelExportUtil.column("订单号", Row::orderNo),
                        ExcelExportUtil.column("金额", row -> ExcelExportUtil.moneyFen(row.amountFen()))
                ),
                List.of(new Row("NO-001", 1234))
        );

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("订单号", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("NO-001", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("¥12.34", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
    }

    @Test
    void shouldRejectRowsOverLimit() {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < ExcelExportUtil.MAX_EXPORT_ROWS + 1; i++) {
            rows.add(new Row("NO-" + i, i));
        }

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ExcelExportUtil.toXlsx("测试", List.of(ExcelExportUtil.column("订单号", Row::orderNo)), rows)
        );

        assertEquals("导出数据超过10000行，请增加筛选条件后再导出", exception.getMessage());
    }

    private record Row(String orderNo, Integer amountFen) {
    }
}
