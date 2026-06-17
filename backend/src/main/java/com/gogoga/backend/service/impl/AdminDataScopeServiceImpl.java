package com.gogoga.backend.service.impl;

import com.gogoga.backend.entity.AdminUserDataScope;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.AdminUserDataScopeMapper;
import com.gogoga.backend.mapper.AdminUserRelationMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.service.AdminDataScopeService;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDataScopeServiceImpl implements AdminDataScopeService {

    private final AdminUserDataScopeMapper adminUserDataScopeMapper;
    private final AdminUserRelationMapper adminUserRelationMapper;
    private final UserMapper userMapper;

    @Override
    public AdminDataScopeTypeEnum getCurrentScopeType() {
        if (UserContext.isSuperAdmin()) {
            return AdminDataScopeTypeEnum.ALL;
        }
        String currentUserId = requireCurrentUserId();
        AdminUserDataScope dataScope = adminUserDataScopeMapper.selectByUserId(currentUserId);
        return dataScope == null
                ? AdminDataScopeTypeEnum.SELF
                : AdminDataScopeTypeEnum.fromValue(dataScope.getScopeType());
    }

    @Override
    public Set<String> getCurrentVisibleUserIds() {
        String currentUserId = requireCurrentUserId();
        AdminDataScopeTypeEnum scopeType = getCurrentScopeType();
        if (scopeType == AdminDataScopeTypeEnum.ALL) {
            return new LinkedHashSet<>(userMapper.selectAllBackendUserIds());
        }
        if (scopeType == AdminDataScopeTypeEnum.TEAM) {
            return resolveTeamVisibleUserIds(currentUserId);
        }
        LinkedHashSet<String> visibleUserIds = new LinkedHashSet<>();
        visibleUserIds.add(currentUserId);
        return visibleUserIds;
    }

    @Override
    public void validateCurrentUserCanViewTargetUser(String targetUserId) {
        String normalizedTargetUserId = StringUtils.trimToNull(targetUserId);
        if (normalizedTargetUserId == null) {
            return;
        }
        if (!getCurrentVisibleUserIds().contains(normalizedTargetUserId)) {
            throw new BusinessException(403, "无权查看目标用户门店");
        }
    }

    @Override
    public boolean canCurrentUserManageBindingsFor(String targetUserId) {
        String normalizedTargetUserId = StringUtils.trimToNull(targetUserId);
        String currentUserId = requireCurrentUserId();
        if (normalizedTargetUserId == null || Objects.equals(normalizedTargetUserId, currentUserId)) {
            return true;
        }
        return UserContext.isSuperAdmin() && getCurrentVisibleUserIds().contains(normalizedTargetUserId);
    }

    @Override
    public List<VisibleUserOptionVO> listCurrentVisibleUsers(String keyword) {
        return listVisibleUsers(keyword, null);
    }

    @Override
    public List<VisibleUserOptionVO> listCurrentVisibleUsersByRoleCode(String keyword, String roleCode) {
        return listVisibleUsers(keyword, StringUtils.trimToNull(roleCode));
    }

    private List<VisibleUserOptionVO> listVisibleUsers(String keyword, String roleCode) {
        String normalizedKeyword = StringUtils.trimToNull(keyword);
        List<User> users;
        AdminDataScopeTypeEnum scopeType = getCurrentScopeType();
        if (scopeType == AdminDataScopeTypeEnum.ALL) {
            users = roleCode == null
                    ? userMapper.selectBackendUsersByKeyword(normalizedKeyword)
                    : userMapper.selectBackendUsersByRoleCodeAndKeyword(roleCode, normalizedKeyword);
        } else if (scopeType == AdminDataScopeTypeEnum.SELF) {
            User currentUser = userMapper.selectByUserId(requireCurrentUserId());
            if (currentUser == null) {
                users = List.of();
            } else if (roleCode == null && matchesKeyword(currentUser, normalizedKeyword)) {
                users = List.of(currentUser);
            } else {
                users = List.of();
            }
        } else {
            List<String> visibleUserIds = new ArrayList<>(getCurrentVisibleUserIds());
            if (visibleUserIds.isEmpty()) {
                users = List.of();
            } else {
                users = roleCode == null
                        ? userMapper.selectByUserIdsAndKeyword(visibleUserIds, normalizedKeyword)
                        : userMapper.selectByUserIdsAndRoleCodeAndKeyword(visibleUserIds, roleCode, normalizedKeyword);
            }
        }
        return users.stream()
                .map(this::toVisibleUserOption)
                .collect(Collectors.toList());
    }

    private Set<String> resolveTeamVisibleUserIds(String currentUserId) {
        LinkedHashSet<String> visibleUserIds = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        visibleUserIds.add(currentUserId);
        queue.add(currentUserId);

        while (!queue.isEmpty()) {
            String managerUserId = queue.removeFirst();
            List<String> subordinates = adminUserRelationMapper.selectDirectSubordinateUserIds(managerUserId);
            if (subordinates == null || subordinates.isEmpty()) {
                continue;
            }
            for (String subordinateUserId : subordinates) {
                String normalized = StringUtils.trimToNull(subordinateUserId);
                if (normalized == null) {
                    continue;
                }
                if (visibleUserIds.add(normalized)) {
                    queue.addLast(normalized);
                }
            }
        }
        return visibleUserIds;
    }

    private VisibleUserOptionVO toVisibleUserOption(User user) {
        VisibleUserOptionVO option = new VisibleUserOptionVO();
        option.setUserId(user.getUserId());
        option.setUsername(user.getUsername());
        option.setNickname(user.getNickname());
        option.setDisplayName(StringUtils.firstNonBlank(user.getNickname(), user.getUsername(), user.getUserId()));
        return option;
    }

    private boolean matchesKeyword(User user, String keyword) {
        if (user == null) {
            return false;
        }
        if (keyword == null) {
            return true;
        }
        return contains(user.getUserId(), keyword)
                || contains(user.getUsername(), keyword)
                || contains(user.getNickname(), keyword)
                || contains(user.getPhone(), keyword);
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.contains(keyword);
    }

    private String requireCurrentUserId() {
        String currentUserId = UserContext.getUserId();
        if (StringUtils.isBlank(currentUserId)) {
            throw new BusinessException(401, "当前用户未登录");
        }
        return currentUserId;
    }
}
