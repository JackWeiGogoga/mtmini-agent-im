package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminPowerGrantControllerTest {

    @Test
    void shouldDeclareDedicatedPermissionsOnEndpoints() throws Exception {
        Method listMethod = AdminPowerGrantController.class.getMethod("listByShopId", Long.class);
        Method createMethod = AdminPowerGrantController.class.getMethod(
                "createGrant",
                com.gogoga.backend.param.AdminPowerGrantCreateParam.class
        );

        RequiresPermission listPermission = listMethod.getAnnotation(RequiresPermission.class);
        RequiresPermission createPermission = createMethod.getAnnotation(RequiresPermission.class);

        assertNotNull(listPermission);
        assertNotNull(createPermission);
        assertEquals("shop:power-grant:list", listPermission.value());
        assertEquals("shop:power-grant:add", createPermission.value());
    }
}
