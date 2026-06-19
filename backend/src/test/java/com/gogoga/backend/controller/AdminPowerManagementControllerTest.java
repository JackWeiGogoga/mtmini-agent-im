package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminPowerManagementControllerTest {

    @Test
    void shouldDeclarePowerManagementPermissionOnAllEndpoints() throws Exception {
        Method listMethod = AdminPowerManagementController.class.getMethod(
                "listRows",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class
        );
        Method summaryMethod = AdminPowerManagementController.class.getMethod("getSummary", Long.class);
        Method rechargeMethod = AdminPowerManagementController.class.getMethod(
                "listRechargeRecords",
                Long.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                Integer.class,
                Integer.class
        );
        Method grantMethod = AdminPowerManagementController.class.getMethod(
                "listGrantRecords",
                Long.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                Integer.class,
                Integer.class
        );
        Method consumeMethod = AdminPowerManagementController.class.getMethod(
                "listConsumeRecords",
                Long.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                Integer.class,
                Integer.class
        );

        for (Method method : new Method[]{listMethod, summaryMethod, rechargeMethod, grantMethod, consumeMethod}) {
            RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
            assertNotNull(permission);
            assertEquals("shop:power-management:list", permission.value());
        }
    }
}
