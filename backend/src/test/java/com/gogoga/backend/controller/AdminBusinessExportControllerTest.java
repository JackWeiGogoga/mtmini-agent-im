package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminMtAppOrderQueryParam;
import com.gogoga.backend.param.AdminMtAppOrderSettlementQueryParam;
import com.gogoga.backend.param.AdminYouzanAppOrderQueryParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminBusinessExportControllerTest {

    @Test
    void shouldDeclareListPermissionOnExportEndpoints() throws Exception {
        assertPermission(
                AdminMtAppOrderController.class.getMethod("exportRows", AdminMtAppOrderQueryParam.class),
                "shop:mt-app-order:list"
        );
        assertPermission(
                AdminMtAppOrderController.class.getMethod(
                        "exportSettlementRows",
                        AdminMtAppOrderSettlementQueryParam.class
                ),
                "shop:mt-app-order:list"
        );
        assertPermission(
                AdminMtAppOrderController.class.getMethod(
                        "listSettlementRows",
                        AdminMtAppOrderSettlementQueryParam.class
                ),
                "shop:mt-app-order:list"
        );
        assertPermission(
                AdminYouzanAppOrderController.class.getMethod("exportRows", AdminYouzanAppOrderQueryParam.class),
                "youzan:app-order:list"
        );
        assertPermission(
                AdminPowerRechargeOrderController.class.getMethod(
                        "exportOrders",
                        String.class,
                        Integer.class,
                        LocalDateTime.class,
                        LocalDateTime.class
                ),
                "system:power-recharge-order:list"
        );
        assertPermission(
                SalesCommissionController.class.getMethod("export", String.class, String.class, String.class),
                "sales:commission:list"
        );
        assertPermission(
                AdminSalesRedemptionCodeController.class.getMethod("export", String.class),
                "sales:redemption-code:list"
        );
    }

    private void assertPermission(Method method, String expected) {
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
        assertNotNull(permission);
        assertEquals(expected, permission.value());
    }
}
