package com.gogoga.backend.service;

import com.gogoga.backend.entity.Menu;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台工作台上下文服务
 */
@Service
public class BackendPortalService {

    public static final String ROLE_CODE_SUPER_ADMIN = "super_admin";
    public static final String ROLE_CODE_ADMIN = "admin";
    public static final String ROLE_CODE_USER = "user";
    public static final String ROLE_CODE_SALES = "sales_user";
    public static final String ROLE_CODE_YOUZAN_USER = "youzan_user";
    public static final String ROLE_CODE_YOUZAN_OPERATOR = "youzan_operator";

    private static final String ORDINARY_PERMISSION_PREFIX = "shop:";
    private static final String SALES_PERMISSION_PREFIX = "sales:";
    private static final String YOUZAN_PERMISSION_PREFIX = "youzan:";
    private static final String YOUZAN_OPERATOR_PERMISSION_PREFIX = "youzan:app-order:";
    private static final Set<String> SHARED_PORTAL_PERMISSIONS = Set.of(
            "analytics:user-behavior:list"
    );
    private static final Set<String> ORDINARY_PORTAL_HIDDEN_PERMISSION_PREFIXES = Set.of(
            "shop:power-management:",
            "shop:mt-app-order:"
    );
    private static final Set<String> SALES_PORTAL_HIDDEN_PERMISSION_PREFIXES = Set.of(
            "sales:commission:ratio-edit",
            "sales:commission:settle",
            "sales:redemption-code:"
    );
    private static final Set<String> ADMIN_HIDDEN_SALES_SHARED_PATHS = Set.of(
            "/master-console",
            "/shops"
    );
    private static final Set<String> ADMIN_VISIBLE_SALES_MENU_PATHS = Set.of(
            "/sales/commissions",
            "/sales/redemption-codes"
    );

    public BackendPortalType resolvePhonePortal(Set<String> roleCodes, BackendPortalType requestedPortal) {
        List<BackendPortalType> availablePortals = resolvePhoneAvailablePortals(roleCodes);
        if (requestedPortal != null) {
            if (!availablePortals.contains(requestedPortal)) {
                throw new BusinessException(403, "当前账号无权进入所选工作台");
            }
            return requestedPortal;
        }
        if (availablePortals.contains(BackendPortalType.SALES)) {
            return BackendPortalType.SALES;
        }
        if (availablePortals.contains(BackendPortalType.YOUZAN)) {
            return BackendPortalType.YOUZAN;
        }
        if (availablePortals.contains(BackendPortalType.ORDINARY)) {
            return BackendPortalType.ORDINARY;
        }
        throw new BusinessException(403, "当前账号未开通普通商户、网销或有赞工作台");
    }

    public BackendPortalType resolveSessionPortal(User user,
                                                  Set<String> roleCodes,
                                                  BackendPortalType storedPortal) {
        if (storedPortal != null && canAccessPortal(user, roleCodes, storedPortal)) {
            return storedPortal;
        }
        if (isAdminAuthProvider(user == null ? null : user.getAuthProvider())) {
            return BackendPortalType.ADMIN;
        }
        return resolvePhonePortal(roleCodes, null);
    }

    public List<BackendPortalType> resolveAvailablePortals(User user,
                                                           Set<String> roleCodes,
                                                           BackendPortalType activePortal) {
        LinkedHashSet<BackendPortalType> result = new LinkedHashSet<>();
        if (activePortal != null) {
            result.add(activePortal);
        }
        if (canAccessPortal(user, roleCodes, BackendPortalType.ADMIN)) {
            result.add(BackendPortalType.ADMIN);
        }
        if (canAccessPortal(user, roleCodes, BackendPortalType.SALES)) {
            result.add(BackendPortalType.SALES);
        }
        if (canAccessPortal(user, roleCodes, BackendPortalType.YOUZAN)) {
            result.add(BackendPortalType.YOUZAN);
        }
        if (canAccessPortal(user, roleCodes, BackendPortalType.ORDINARY)) {
            result.add(BackendPortalType.ORDINARY);
        }
        return new ArrayList<>(result);
    }

    public void validatePortalOwnership(User user, Set<String> roleCodes, BackendPortalType portal) {
        if (portal == null || !canAccessPortal(user, roleCodes, portal)) {
            throw new BusinessException(403, "当前账号无权切换到所选工作台");
        }
    }

    public Set<String> filterPermissions(Set<String> permissions, BackendPortalType activePortal) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }
        if (activePortal == null || activePortal == BackendPortalType.ADMIN) {
            return permissions;
        }
        String prefix = permissionPrefix(activePortal);
        return permissions.stream()
                .filter(permission -> StringUtils.startsWith(permission, prefix)
                        || isSharedPortalPermission(permission, activePortal))
                .filter(permission -> !isHiddenInPortal(permission, activePortal))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<Menu> filterMenus(List<Menu> menus, BackendPortalType activePortal) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        if (activePortal == null) {
            return menus;
        }
        if (activePortal == BackendPortalType.ADMIN) {
            Set<String> ordinarySharedPaths = menus.stream()
                    .filter(this::isOrdinaryPortalMenu)
                    .map(Menu::getPath)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(HashSet::new));
            return menus.stream()
                    .filter(menu -> !isAdminDuplicateSharedPortalMenu(menu, ordinarySharedPaths))
                    .collect(Collectors.toList());
        }
        return menus.stream()
                .filter(menu -> supportsMenu(menu, activePortal))
                .collect(Collectors.toList());
    }

    public boolean supportsMenu(Menu menu, BackendPortalType activePortal) {
        if (menu == null) {
            return false;
        }
        if (activePortal == null || activePortal == BackendPortalType.ADMIN) {
            return true;
        }
        String permission = StringUtils.trimToNull(menu.getPermission());
        return permission != null
                && (StringUtils.startsWith(permission, permissionPrefix(activePortal))
                || isSharedPortalPermission(permission, activePortal))
                && !isHiddenInPortal(permission, activePortal);
    }

    private boolean isSharedPortalPermission(String permission, BackendPortalType activePortal) {
        return activePortal == BackendPortalType.SALES
                && SHARED_PORTAL_PERMISSIONS.contains(StringUtils.trimToEmpty(permission));
    }

    private boolean canAccessPortal(User user, Set<String> roleCodes, BackendPortalType portal) {
        if (portal == null) {
            return false;
        }
        return switch (portal) {
            case ADMIN -> isAdminAuthProvider(user == null ? null : user.getAuthProvider()) || hasAdminRole(roleCodes);
            case ORDINARY -> hasOrdinaryRole(roleCodes);
            case SALES -> hasSalesRole(roleCodes);
            case YOUZAN -> hasYouzanUserRole(roleCodes);
        };
    }

    private List<BackendPortalType> resolvePhoneAvailablePortals(Set<String> roleCodes) {
        List<BackendPortalType> result = new ArrayList<>();
        if (hasSalesRole(roleCodes)) {
            result.add(BackendPortalType.SALES);
        }
        if (hasYouzanUserRole(roleCodes)) {
            result.add(BackendPortalType.YOUZAN);
        }
        if (hasOrdinaryRole(roleCodes)) {
            result.add(BackendPortalType.ORDINARY);
        }
        return result;
    }

    private boolean hasAdminRole(Set<String> roleCodes) {
        return roleCodes != null
                && (roleCodes.contains(ROLE_CODE_SUPER_ADMIN)
                || roleCodes.contains(ROLE_CODE_ADMIN)
                || roleCodes.contains(ROLE_CODE_YOUZAN_OPERATOR));
    }

    private boolean hasOrdinaryRole(Set<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(ROLE_CODE_USER);
    }

    private boolean hasSalesRole(Set<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(ROLE_CODE_SALES);
    }

    private boolean hasYouzanUserRole(Set<String> roleCodes) {
        return roleCodes != null && roleCodes.contains(ROLE_CODE_YOUZAN_USER);
    }

    private boolean isAdminAuthProvider(String authProvider) {
        return StringUtils.equalsAnyIgnoreCase(authProvider, "PASSWORD", "GOOGLE", "GITHUB");
    }

    private String permissionPrefix(BackendPortalType activePortal) {
        if (activePortal == BackendPortalType.SALES) {
            return SALES_PERMISSION_PREFIX;
        }
        if (activePortal == BackendPortalType.YOUZAN) {
            return YOUZAN_PERMISSION_PREFIX;
        }
        return ORDINARY_PERMISSION_PREFIX;
    }

    private boolean isHiddenInPortal(String permission, BackendPortalType activePortal) {
        if (activePortal == BackendPortalType.ORDINARY) {
            return startsWithAny(permission, ORDINARY_PORTAL_HIDDEN_PERMISSION_PREFIXES);
        }
        if (activePortal == BackendPortalType.SALES) {
            return startsWithAny(permission, SALES_PORTAL_HIDDEN_PERMISSION_PREFIXES);
        }
        return activePortal == BackendPortalType.YOUZAN
                && StringUtils.startsWith(permission, YOUZAN_OPERATOR_PERMISSION_PREFIX);
    }

    private boolean startsWithAny(String value, Set<String> prefixes) {
        if (StringUtils.isBlank(value) || prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        return prefixes.stream().anyMatch(prefix -> StringUtils.startsWith(value, prefix));
    }

    private boolean isOrdinaryPortalMenu(Menu menu) {
        if (menu == null) {
            return false;
        }
        String permission = StringUtils.trimToNull(menu.getPermission());
        return permission != null && StringUtils.startsWith(permission, ORDINARY_PERMISSION_PREFIX);
    }

    private boolean isAdminDuplicateSharedPortalMenu(Menu menu, Set<String> ordinarySharedPaths) {
        if (menu == null) {
            return false;
        }
        String permission = StringUtils.trimToNull(menu.getPermission());
        String path = StringUtils.trimToNull(menu.getPath());
        if (permission == null || path == null) {
            return false;
        }
        boolean salesMenu = StringUtils.startsWith(permission, SALES_PERMISSION_PREFIX);
        boolean youzanMenu = StringUtils.startsWith(permission, YOUZAN_PERMISSION_PREFIX);
        if (!salesMenu && !youzanMenu) {
            return false;
        }
        if (salesMenu
                && !ADMIN_VISIBLE_SALES_MENU_PATHS.contains(path)
                && !ADMIN_HIDDEN_SALES_SHARED_PATHS.contains(path)) {
            return true;
        }
        return (salesMenu || youzanMenu)
                && ADMIN_HIDDEN_SALES_SHARED_PATHS.contains(path)
                && ordinarySharedPaths.contains(path);
    }
}
