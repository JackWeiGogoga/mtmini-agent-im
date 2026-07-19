package com.gogoga.backend.service;

import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendPortalServiceTest {

    private final BackendPortalService backendPortalService = new BackendPortalService();

    @Test
    void shouldHideSalesSharedMenusInAdminPortalWhileKeepingSalesSpecificMenus() {
        Menu ordinaryMasterConsole = menu("MENU_MASTER_CONSOLE", "/master-console", "shop:master-console:list");
        Menu salesMasterConsole = menu("MENU_SALES_MASTER_CONSOLE", "/master-console", "sales:master-console:list");
        Menu youzanMasterConsole = menu("MENU_YOUZAN_MASTER_CONSOLE", "/master-console", "youzan:master-console:list");
        Menu ordinaryShops = menu("MENU_SHOP_MANAGE", "/shops", "shop:binding:list");
        Menu salesShops = menu("MENU_SALES_SHOP_MANAGE", "/shops", "sales:binding:list");
        Menu youzanShops = menu("MENU_YOUZAN_SHOP_MANAGE", "/shops", "youzan:binding:list");
        Menu salesCommission = menu("MENU_SALES_COMMISSION", "/sales/commissions", "sales:commission:list");
        Menu salesConfig = menu("MENU_SALES_CONFIG", "/sales/config", "sales:self-config:view");

        List<Menu> filteredMenus = backendPortalService.filterMenus(
                List.of(
                        ordinaryMasterConsole,
                        salesMasterConsole,
                        youzanMasterConsole,
                        ordinaryShops,
                        salesShops,
                        youzanShops,
                        salesCommission,
                        salesConfig
                ),
                BackendPortalType.ADMIN
        );

        Set<String> menuIds = filteredMenus.stream().map(Menu::getMenuId).collect(java.util.stream.Collectors.toSet());
        assertEquals(3, filteredMenus.size());
        assertTrue(menuIds.contains("MENU_MASTER_CONSOLE"));
        assertTrue(menuIds.contains("MENU_SHOP_MANAGE"));
        assertTrue(menuIds.contains("MENU_SALES_COMMISSION"));
        assertFalse(menuIds.contains("MENU_SALES_MASTER_CONSOLE"));
        assertFalse(menuIds.contains("MENU_YOUZAN_MASTER_CONSOLE"));
        assertFalse(menuIds.contains("MENU_SALES_SHOP_MANAGE"));
        assertFalse(menuIds.contains("MENU_YOUZAN_SHOP_MANAGE"));
        assertFalse(menuIds.contains("MENU_SALES_CONFIG"));
    }

    @Test
    void shouldResolveYouzanPhonePortalAndFilterPermissions() {
        Set<String> roles = Set.of(BackendPortalService.ROLE_CODE_YOUZAN_USER);

        assertEquals(
                BackendPortalType.YOUZAN,
                backendPortalService.resolvePhonePortal(roles, null)
        );
        assertEquals(
                BackendPortalType.YOUZAN,
                backendPortalService.resolvePhonePortal(roles, BackendPortalType.YOUZAN)
        );
        assertEquals(
                Set.of("youzan:master-console:list", "youzan:binding:add"),
                backendPortalService.filterPermissions(
                        Set.of(
                                "shop:binding:list",
                                "youzan:master-console:list",
                                "youzan:binding:add",
                                "youzan:app-order:list",
                                "sales:binding:list"
                        ),
                        BackendPortalType.YOUZAN
                )
        );
    }

    @Test
    void shouldHideYouzanOperatorMenusInYouzanUserPortal() {
        Menu youzanMasterConsole = menu("MENU_YOUZAN_MASTER_CONSOLE", "/master-console", "youzan:master-console:list");
        Menu youzanOperatorOrder = menu("MENU_YOUZAN_APP_ORDER", "/youzan-app-orders", "youzan:app-order:list");

        List<Menu> filteredMenus = backendPortalService.filterMenus(
                List.of(youzanMasterConsole, youzanOperatorOrder),
                BackendPortalType.YOUZAN
        );

        assertEquals(1, filteredMenus.size());
        assertEquals("MENU_YOUZAN_MASTER_CONSOLE", filteredMenus.get(0).getMenuId());
    }

    @Test
    void shouldKeepOrdinaryMasterConsoleAndMerchantKnowledgeInOrdinaryPortal() {
        Menu ordinaryMasterConsole = menu("MENU_MASTER_CONSOLE", "/master-console", "shop:master-console:list");
        Menu ordinaryShops = menu("MENU_SHOP_MANAGE", "/shops", "shop:binding:list");
        Menu powerManagement = menu("MENU_POWER_MANAGE", "/power-management", "shop:power-management:list");
        Menu mtAppOrders = menu("MENU_MT_APP_ORDER", "/mt-app-orders", "shop:mt-app-order:list");
        Menu merchantKnowledge = menu("MENU_MERCHANT_KNOWLEDGE", "/merchant-knowledge", "knowledge:list");
        Menu behaviorAnalytics = menu("MENU_USER_BEHAVIOR_ANALYTICS", "/analytics/user-behavior", "analytics:user-behavior:list");

        List<Menu> filteredMenus = backendPortalService.filterMenus(
                List.of(ordinaryMasterConsole, ordinaryShops, powerManagement, mtAppOrders, merchantKnowledge, behaviorAnalytics),
                BackendPortalType.ORDINARY
        );

        Set<String> menuIds = filteredMenus.stream().map(Menu::getMenuId).collect(java.util.stream.Collectors.toSet());
        assertEquals(3, filteredMenus.size());
        assertTrue(menuIds.contains("MENU_MASTER_CONSOLE"));
        assertTrue(menuIds.contains("MENU_SHOP_MANAGE"));
        assertTrue(menuIds.contains("MENU_MERCHANT_KNOWLEDGE"));
        assertFalse(menuIds.contains("MENU_POWER_MANAGE"));
        assertFalse(menuIds.contains("MENU_MT_APP_ORDER"));
        assertFalse(menuIds.contains("MENU_USER_BEHAVIOR_ANALYTICS"));
        assertEquals(
                Set.of("shop:master-console:list", "shop:binding:list", "knowledge:list"),
                backendPortalService.filterPermissions(
                        Set.of(
                                "shop:master-console:list",
                                "shop:binding:list",
                                "shop:power-management:list",
                                "shop:mt-app-order:list",
                                "knowledge:list",
                                "analytics:user-behavior:list"
                        ),
                        BackendPortalType.ORDINARY
                )
        );
    }

    @Test
    void shouldHideAdminSalesRedemptionMenusInSalesPortal() {
        Menu salesMasterConsole = menu("MENU_SALES_MASTER_CONSOLE", "/master-console", "sales:master-console:list");
        Menu salesConfig = menu("MENU_SALES_CONFIG", "/sales/config", "sales:self-config:view");
        Menu salesCommission = menu("MENU_SALES_COMMISSION", "/sales/commissions", "sales:commission:list");
        Menu salesRedemptionCodes = menu("MENU_SALES_REDEMPTION_CODES", "/sales/redemption-codes", "sales:redemption-code:list");

        List<Menu> filteredMenus = backendPortalService.filterMenus(
                List.of(salesMasterConsole, salesConfig, salesCommission, salesRedemptionCodes),
                BackendPortalType.SALES
        );

        Set<String> menuIds = filteredMenus.stream().map(Menu::getMenuId).collect(java.util.stream.Collectors.toSet());
        assertEquals(3, filteredMenus.size());
        assertTrue(menuIds.contains("MENU_SALES_MASTER_CONSOLE"));
        assertTrue(menuIds.contains("MENU_SALES_CONFIG"));
        assertTrue(menuIds.contains("MENU_SALES_COMMISSION"));
        assertFalse(menuIds.contains("MENU_SALES_REDEMPTION_CODES"));
        assertEquals(
                Set.of("sales:master-console:list", "sales:self-config:view", "sales:commission:list"),
                backendPortalService.filterPermissions(
                        Set.of(
                                "sales:master-console:list",
                                "sales:self-config:view",
                                "sales:commission:list",
                                "sales:commission:ratio-edit",
                                "sales:commission:settle",
                                "sales:redemption-code:list",
                                "sales:redemption-code:create"
                        ),
                        BackendPortalType.SALES
                )
        );
    }

    @Test
    void shouldKeepSharedAnalyticsMenuAndPermissionInSalesPortal() {
        Menu salesMasterConsole = menu("MENU_SALES_MASTER_CONSOLE", "/master-console", "sales:master-console:list");
        Menu behaviorAnalytics = menu("MENU_USER_BEHAVIOR_ANALYTICS", "/analytics/user-behavior", "analytics:user-behavior:list");
        Menu ordinaryPowerManagement = menu("MENU_POWER_MANAGE", "/power-management", "shop:power-management:list");

        List<Menu> filteredMenus = backendPortalService.filterMenus(
                List.of(salesMasterConsole, behaviorAnalytics, ordinaryPowerManagement),
                BackendPortalType.SALES
        );

        Set<String> menuIds = filteredMenus.stream().map(Menu::getMenuId).collect(java.util.stream.Collectors.toSet());
        assertEquals(2, filteredMenus.size());
        assertTrue(menuIds.contains("MENU_SALES_MASTER_CONSOLE"));
        assertTrue(menuIds.contains("MENU_USER_BEHAVIOR_ANALYTICS"));
        assertFalse(menuIds.contains("MENU_POWER_MANAGE"));
        assertEquals(
                Set.of("sales:master-console:list", "analytics:user-behavior:list"),
                backendPortalService.filterPermissions(
                        Set.of(
                                "sales:master-console:list",
                                "analytics:user-behavior:list",
                                "shop:power-management:list"
                        ),
                        BackendPortalType.SALES
                )
        );
    }

    @Test
    void shouldRejectYouzanPortalForNonYouzanUser() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> backendPortalService.resolvePhonePortal(
                        Set.of(BackendPortalService.ROLE_CODE_USER),
                        BackendPortalType.YOUZAN
                )
        );

        assertEquals("当前账号无权进入所选工作台", exception.getMessage());
    }

    @Test
    void shouldExposeAvailableYouzanPortalForAssignedRole() {
        User user = new User();
        user.setAuthProvider("SMS");

        List<BackendPortalType> portals = backendPortalService.resolveAvailablePortals(
                user,
                Set.of(BackendPortalService.ROLE_CODE_YOUZAN_USER),
                BackendPortalType.YOUZAN
        );

        assertEquals(List.of(BackendPortalType.YOUZAN), portals);
    }

    private Menu menu(String menuId, String path, String permission) {
        Menu menu = new Menu();
        menu.setMenuId(menuId);
        menu.setPath(path);
        menu.setPermission(permission);
        menu.setMenuType("C");
        return menu;
    }
}
