package com.gogoga.backend.service;

import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.mapper.MtAppOrderMapper;
import com.gogoga.backend.param.AdminMtAppOrderQueryParam;
import com.gogoga.backend.vo.AdminMtAppOrderRowVO;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMtAppOrderExportServiceTest {

    @Mock
    private MtAppOrderMapper mtAppOrderMapper;

    @Mock
    private AdminDataScopeService adminDataScopeService;

    @Test
    void shouldExportMtAppOrderRowsWithFiltersAndCoreColumns() throws Exception {
        AdminMtAppOrderService service = new AdminMtAppOrderService(mtAppOrderMapper, adminDataScopeService);
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        AdminMtAppOrderRowVO row = new AdminMtAppOrderRowVO();
        row.setOrderId("MT-001");
        row.setOrderViewId("VIEW-001");
        row.setShopName("测试门店");
        row.setPoiId("poi-001");
        row.setRealPayPrice(9900);
        row.setOrderTime(LocalDateTime.of(2026, 5, 1, 10, 0));
        when(mtAppOrderMapper.selectAdminPage(
                eq("MT-001"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(AdminDataScopeTypeEnum.ALL.name()),
                any(),
                any()
        )).thenReturn(List.of(row));

        AdminMtAppOrderQueryParam param = new AdminMtAppOrderQueryParam();
        param.setOrderId(" MT-001 ");
        byte[] content = service.exportXlsx(param);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("订单号", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("MT-001", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("测试门店", workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
            assertEquals("¥99.00", workbook.getSheetAt(0).getRow(1).getCell(10).getStringCellValue());
        }

        ArgumentCaptor<String> orderIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(mtAppOrderMapper).selectAdminPage(
                orderIdCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
        assertTrue(orderIdCaptor.getValue().equals("MT-001"));
    }

}
