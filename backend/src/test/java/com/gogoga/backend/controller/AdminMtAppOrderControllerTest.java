package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminMtAppOrderManualPullParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminMtAppOrderControllerTest {

    @Test
    void manualPullRequiresLoginAndDedicatedPermission() throws Exception {
        assertNotNull(AdminMtAppOrderController.class.getAnnotation(RequiresLogin.class));

        Method manualPull = AdminMtAppOrderController.class.getMethod(
                "manualPull",
                AdminMtAppOrderManualPullParam.class
        );

        assertEquals(
                "shop:mt-app-order:manual-pull",
                manualPull.getAnnotation(RequiresPermission.class).value()
        );
    }
}
