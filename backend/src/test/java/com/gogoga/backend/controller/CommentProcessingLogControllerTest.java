package com.gogoga.backend.controller;

import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.CommentProcessingLogQueryParam;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommentProcessingLogControllerTest {

    @Test
    void requiresLoginAndSeparateListAndDetailPermissions() throws Exception {
        assertNotNull(CommentProcessingLogController.class.getAnnotation(RequiresLogin.class));

        Method list = CommentProcessingLogController.class.getMethod("list", CommentProcessingLogQueryParam.class);
        Method detail = CommentProcessingLogController.class.getMethod("detail", Long.class);

        assertEquals("comment:processing-log:list", list.getAnnotation(RequiresPermission.class).value());
        assertEquals("comment:processing-log:query", detail.getAnnotation(RequiresPermission.class).value());
    }
}
