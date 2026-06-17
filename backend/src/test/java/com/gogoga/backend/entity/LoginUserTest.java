package com.gogoga.backend.entity;

import com.gogoga.backend.enums.BackendPortalType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginUserTest {

    @Test
    void shouldBypassPermissionsForSuperAdminInAdminPortal() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(Set.of("super_admin"));
        loginUser.setActivePortal(BackendPortalType.ADMIN);

        assertTrue(loginUser.hasPermission("sales:commission:settle"));
    }

    @Test
    void shouldUseFilteredPermissionsForSuperAdminInSalesPortal() {
        LoginUser loginUser = new LoginUser();
        loginUser.setRoles(Set.of("super_admin"));
        loginUser.setActivePortal(BackendPortalType.SALES);
        loginUser.setPermissions(Set.of("sales:commission:list"));

        assertTrue(loginUser.hasPermission("sales:commission:list"));
        assertFalse(loginUser.hasPermission("sales:commission:settle"));
    }
}
