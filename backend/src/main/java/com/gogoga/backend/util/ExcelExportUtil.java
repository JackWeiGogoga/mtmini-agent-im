package com.gogoga.backend.util;

import com.gogoga.backend.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

public final class ExcelExportUtil {

    public static final int MAX_EXPORT_ROWS = 10_000;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExcelExportUtil() {
    }

    public static <T> byte[] toXlsx(String sheetName, List<Column<T>> columns, List<T> rows) {
        if (rows != null && rows.size() > MAX_EXPORT_ROWS) {
            throw new BusinessException("导出数据超过10000行，请增加筛选条件后再导出");
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(normalizeSheetName(sheetName));
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
            }

            if (rows != null) {
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    Row dataRow = sheet.createRow(rowIndex + 1);
                    T item = rows.get(rowIndex);
                    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                        Object value = columns.get(columnIndex).valueProvider().apply(item);
                        dataRow.createCell(columnIndex).setCellValue(formatCellValue(value));
                    }
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("导出文件生成失败");
        }
    }

    public static ResponseEntity<byte[]> xlsxResponse(String filename, byte[] content) {
        String safeFilename = StringUtils.defaultIfBlank(filename, "export.xlsx");
        if (!StringUtils.endsWithIgnoreCase(safeFilename, ".xlsx")) {
            safeFilename = safeFilename + ".xlsx";
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(safeFilename, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content == null ? 0 : content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }

    public static String moneyFen(Integer value) {
        if (value == null) {
            return "";
        }
        return "¥" + BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    public static String percent(Integer value) {
        return value == null ? "" : value + "%";
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    public static <T> Column<T> column(String header, Function<T, Object> valueProvider) {
        return new Column<>(header, valueProvider);
    }

    private static String formatCellValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime localDateTime) {
            return dateTime(localDateTime);
        }
        return String.valueOf(value);
    }

    private static String normalizeSheetName(String sheetName) {
        String normalized = StringUtils.defaultIfBlank(sheetName, "导出数据")
                .replaceAll("[\\\\/?*\\[\\]:]", " ");
        return normalized.length() > 31 ? normalized.substring(0, 31) : normalized;
    }

    public record Column<T>(String header, Function<T, Object> valueProvider) {
    }
}
