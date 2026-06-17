package com.gogoga.backend.service;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.mapper.MtPowerRechargeMapper;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPowerRechargeOrderRowVO;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPowerRechargeOrderExportServiceTest {

    @Mock
    private MtPowerRechargeMapper mtPowerRechargeMapper;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldExportRechargeOrderCoreColumns() throws Exception {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);
        AdminPowerRechargeOrderService service = new AdminPowerRechargeOrderService(mtPowerRechargeMapper);

        AdminPowerRechargeOrderRowVO row = new AdminPowerRechargeOrderRowVO();
        row.setRechargeNo("PR-001");
        row.setShopName("充值门店");
        row.setPoiId("poi-001");
        row.setPowerAmount(1000);
        row.setTotalAmountFen(19900);
        row.setPayStatus(2);
        row.setPaidAt(LocalDateTime.of(2026, 5, 1, 11, 0));
        when(mtPowerRechargeMapper.selectAdminRechargeOrderList("PR", 2, null, null)).thenReturn(List.of(row));

        byte[] content = service.exportXlsx("PR", 2, null, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertEquals("充值订单号", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("PR-001", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("充值门店", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("¥199.00", workbook.getSheetAt(0).getRow(1).getCell(6).getStringCellValue());
            assertEquals("支付成功", workbook.getSheetAt(0).getRow(1).getCell(14).getStringCellValue());
        }
    }
}
