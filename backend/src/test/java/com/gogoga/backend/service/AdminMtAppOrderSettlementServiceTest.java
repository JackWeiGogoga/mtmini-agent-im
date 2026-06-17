package com.gogoga.backend.service;

import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.gogoga.backend.param.AdminMtAppOrderSettlementQueryParam;
import com.gogoga.backend.vo.AdminMtAppOrderSettlementRowVO;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMtAppOrderSettlementServiceTest {

    @Mock
    private MtAppOrderMapper mtAppOrderMapper;

    @Mock
    private AdminDataScopeService adminDataScopeService;

    @Test
    void shouldCalculateSettlementDaysAndActualIncome() {
        AdminMtAppOrderService service = service();
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(mtAppOrderMapper.selectSettlementCandidates(
                eq("poi-001"),
                eq(2),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        )).thenReturn(List.of(
                row("MT-001", 3000, 30,
                        LocalDateTime.of(2026, 3, 20, 0, 0),
                        LocalDateTime.of(2026, 4, 18, 23, 59, 59)),
                row("MT-002", 3000, 30,
                        LocalDateTime.of(2026, 4, 20, 0, 0),
                        LocalDateTime.of(2026, 5, 19, 23, 59, 59))
        ));

        List<AdminMtAppOrderSettlementRowVO> rows = service.listSettlementRows(param("2026-04", "poi-001")).getList();

        assertEquals(2, rows.size());
        assertEquals(18, rows.get(0).getCurrentMonthUsedDays());
        assertEquals(12, rows.get(0).getPreviousMonthUsedDays());
        assertEquals(0, rows.get(0).getRemainingUnusedDays());
        assertEquals(new BigDecimal("3.60"), rows.get(0).getMeituanDeductionAmount());
        assertEquals(new BigDecimal("9.60"), rows.get(0).getPreviousMonthActualIncome());
        assertEquals(new BigDecimal("14.40"), rows.get(0).getCurrentMonthActualIncome());
        assertEquals(new BigDecimal("0.00"), rows.get(0).getRemainingUnsettledActualIncome());
        assertEquals(11, rows.get(1).getCurrentMonthUsedDays());
        assertEquals(0, rows.get(1).getPreviousMonthUsedDays());
        assertEquals(19, rows.get(1).getRemainingUnusedDays());
        assertEquals(new BigDecimal("2.20"), rows.get(1).getMeituanDeductionAmount());
        assertEquals(new BigDecimal("0.00"), rows.get(1).getPreviousMonthActualIncome());
        assertEquals(new BigDecimal("8.80"), rows.get(1).getCurrentMonthActualIncome());
        assertEquals(new BigDecimal("15.20"), rows.get(1).getRemainingUnsettledActualIncome());
    }

    @Test
    void shouldUseNaturalMonthBoundaryForLeapFebruary() {
        AdminMtAppOrderService service = service();
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(mtAppOrderMapper.selectSettlementCandidates(
                eq("poi-001"),
                eq(2),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        )).thenReturn(List.of(row("MT-FEB", 2900, 29,
                LocalDateTime.of(2028, 2, 1, 0, 0),
                LocalDateTime.of(2028, 2, 29, 23, 59, 59))));

        AdminMtAppOrderSettlementRowVO row = service.listSettlementRows(param("2028-02", "poi-001"))
                .getList()
                .get(0);

        assertEquals(29, row.getCurrentMonthUsedDays());
        assertEquals(new BigDecimal("5.80"), row.getMeituanDeductionAmount());
        assertEquals(new BigDecimal("23.20"), row.getCurrentMonthActualIncome());
    }

    @Test
    void shouldReturnZeroIncomeWhenSettlementBasisIsInvalid() {
        AdminMtAppOrderService service = service();
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(mtAppOrderMapper.selectSettlementCandidates(
                eq("poi-001"),
                eq(2),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        )).thenReturn(List.of(row("MT-MISSING", 3000, null,
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 30, 23, 59, 59))));

        AdminMtAppOrderSettlementRowVO row = service.listSettlementRows(param("2026-04", "poi-001"))
                .getList()
                .get(0);

        assertEquals(0, row.getCurrentMonthUsedDays());
        assertEquals(0, row.getPreviousMonthUsedDays());
        assertEquals(0, row.getRemainingUnusedDays());
        assertEquals(new BigDecimal("0.00"), row.getMeituanDeductionAmount());
        assertEquals(new BigDecimal("0.00"), row.getPreviousMonthActualIncome());
        assertEquals(new BigDecimal("0.00"), row.getCurrentMonthActualIncome());
        assertEquals(new BigDecimal("0.00"), row.getRemainingUnsettledActualIncome());
    }

    @Test
    void shouldRequireValidMonth() {
        AdminMtAppOrderService service = service();

        assertThrows(BusinessException.class, () -> service.listSettlementRows(param(null, "poi-001")));
        assertThrows(BusinessException.class, () -> service.listSettlementRows(param("202604", "poi-001")));
    }

    @Test
    void shouldAllowSettlementQueryWithoutPoiId() {
        AdminMtAppOrderService service = service();
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(mtAppOrderMapper.selectSettlementCandidates(
                eq(null),
                eq(2),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        )).thenReturn(List.of());

        service.listSettlementRows(param("2026-04", ""));

        verify(mtAppOrderMapper).selectSettlementCandidates(
                eq(null),
                eq(2),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        );
    }

    @Test
    void shouldExportSettlementRowsWithCoreColumns() throws Exception {
        AdminMtAppOrderService service = service();
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(mtAppOrderMapper.selectSettlementCandidates(
                eq("poi-001"),
                eq(2),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        )).thenReturn(List.of(row("MT-001", 3000, 30,
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 30, 23, 59, 59))));

        byte[] content = service.exportSettlementXlsx(param("2026-04", "poi-001"));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("订单号", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("MT-001", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("测试门店", workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
            assertEquals("13800138000", workbook.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals("¥30.00", workbook.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());
            assertEquals("¥6.00", workbook.getSheetAt(0).getRow(1).getCell(9).getStringCellValue());
            assertEquals("¥24.00", workbook.getSheetAt(0).getRow(1).getCell(11).getStringCellValue());
        }
    }

    @Test
    void shouldQueryOnlyPaidOrdersInsideVisibleScope() {
        AdminMtAppOrderService service = service();
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(mtAppOrderMapper.selectSettlementCandidates(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        service.listSettlementRows(param("2026-04", " poi-001 "));

        ArgumentCaptor<String> poiIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> payStatusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mtAppOrderMapper).selectSettlementCandidates(
                poiIdCaptor.capture(),
                payStatusCaptor.capture(),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        );
        assertEquals("poi-001", poiIdCaptor.getValue());
        assertEquals(2, payStatusCaptor.getValue());
    }

    private AdminMtAppOrderService service() {
        return new AdminMtAppOrderService(mtAppOrderMapper, adminDataScopeService);
    }

    private AdminMtAppOrderSettlementQueryParam param(String statsMonth, String poiId) {
        AdminMtAppOrderSettlementQueryParam param = new AdminMtAppOrderSettlementQueryParam();
        param.setStatsMonth(statsMonth);
        param.setPoiId(poiId);
        return param;
    }

    private AdminMtAppOrderSettlementRowVO row(String orderId,
                                              Integer paidAmountFen,
                                              Integer days,
                                              LocalDateTime startTime,
                                              LocalDateTime endTime) {
        AdminMtAppOrderSettlementRowVO row = new AdminMtAppOrderSettlementRowVO();
        row.setId((long) Math.abs(orderId.hashCode()));
        row.setOrderId(orderId);
        row.setOrderTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        row.setShopName("测试门店");
        row.setPhone("13800138000");
        row.setPoiId("poi-001");
        row.setVersionName("基础版");
        row.setDays(days);
        row.setPaidAmountFen(paidAmountFen);
        row.setStartTime(startTime);
        row.setEndTime(endTime);
        return row;
    }
}
