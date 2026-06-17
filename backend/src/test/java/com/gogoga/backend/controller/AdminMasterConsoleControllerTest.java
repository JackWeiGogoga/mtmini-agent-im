package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminMasterConsoleControllerTest {

    @Test
    void shouldDeclareDedicatedPermissionsOnEndpoints() throws Exception {
        Method listMethod = AdminMasterConsoleController.class.getMethod(
                "listRows",
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                Integer.class
        );
        Method getSettingsMethod = AdminMasterConsoleController.class.getMethod("getPromotionSettings", Long.class, String.class);
        Method updateSettingsMethod = AdminMasterConsoleController.class.getMethod(
                "updatePromotionSettings",
                Long.class,
                String.class,
                com.gogoga.backend.param.MtH5PromotionSettingsUpdateParam.class
        );
        Method listLogsMethod = AdminMasterConsoleController.class.getMethod(
                "listPromotionLogs",
                Long.class,
                String.class,
                Integer.class,
                Integer.class
        );
        Method getLogSummaryMethod = AdminMasterConsoleController.class.getMethod(
                "getPromotionLogSummary",
                Long.class,
                String.class
        );
        Method asyncTaskMetricsMethod = AdminMasterConsoleController.class.getMethod("getAsyncTaskMetrics");

        RequiresPermission listPermission = listMethod.getAnnotation(RequiresPermission.class);
        RequiresPermission getSettingsPermission = getSettingsMethod.getAnnotation(RequiresPermission.class);
        RequiresPermission updateSettingsPermission = updateSettingsMethod.getAnnotation(RequiresPermission.class);
        RequiresPermission listLogsPermission = listLogsMethod.getAnnotation(RequiresPermission.class);
        RequiresPermission getLogSummaryPermission = getLogSummaryMethod.getAnnotation(RequiresPermission.class);
        RequiresPermission asyncTaskMetricsPermission = asyncTaskMetricsMethod.getAnnotation(RequiresPermission.class);

        assertNotNull(listPermission);
        assertNotNull(getSettingsPermission);
        assertNotNull(updateSettingsPermission);
        assertNotNull(listLogsPermission);
        assertNotNull(getLogSummaryPermission);
        assertNotNull(asyncTaskMetricsPermission);
        assertEquals("shop:master-console:list,sales:master-console:list,youzan:master-console:list", listPermission.value());
        assertEquals("shop:master-console:config,sales:master-console:view,youzan:master-console:config", getSettingsPermission.value());
        assertEquals("shop:master-console:config,youzan:master-console:config", updateSettingsPermission.value());
        assertEquals("shop:master-console:log,sales:master-console:log,youzan:master-console:log", listLogsPermission.value());
        assertEquals("shop:master-console:log,sales:master-console:log,youzan:master-console:log", getLogSummaryPermission.value());
        assertEquals("shop:master-console:list,sales:master-console:list,youzan:master-console:list", asyncTaskMetricsPermission.value());
    }
}
