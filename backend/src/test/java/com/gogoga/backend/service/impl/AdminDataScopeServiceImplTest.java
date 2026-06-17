package com.gogoga.backend.service.impl;

import com.gogoga.backend.entity.AdminUserDataScope;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.AdminUserDataScopeMapper;
import com.gogoga.backend.mapper.AdminUserRelationMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDataScopeServiceImplTest {

    @Mock
    private AdminUserDataScopeMapper adminUserDataScopeMapper;

    @Mock
    private AdminUserRelationMapper adminUserRelationMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminDataScopeServiceImpl adminDataScopeService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldResolveAllScopeForSuperAdmin() {
        mockLoginUser("super-1", Set.of("super_admin"));
        when(userMapper.selectAllBackendUserIds()).thenReturn(List.of("super-1", "manager-1", "user-1"));

        assertEquals(AdminDataScopeTypeEnum.ALL, adminDataScopeService.getCurrentScopeType());
        assertEquals(Set.of("super-1", "manager-1", "user-1"), adminDataScopeService.getCurrentVisibleUserIds());
    }

    @Test
    void shouldResolveTeamScopeWithDescendantTree() {
        mockLoginUser("manager-1", Set.of("admin"));

        AdminUserDataScope dataScope = new AdminUserDataScope();
        dataScope.setUserId("manager-1");
        dataScope.setScopeType("TEAM");
        when(adminUserDataScopeMapper.selectByUserId("manager-1")).thenReturn(dataScope);
        when(adminUserRelationMapper.selectDirectSubordinateUserIds("manager-1"))
                .thenReturn(List.of("user-1", "user-2"));
        when(adminUserRelationMapper.selectDirectSubordinateUserIds("user-1"))
                .thenReturn(List.of("user-1-1"));
        when(adminUserRelationMapper.selectDirectSubordinateUserIds("user-2"))
                .thenReturn(List.of());
        when(adminUserRelationMapper.selectDirectSubordinateUserIds("user-1-1"))
                .thenReturn(List.of());
        when(userMapper.selectByUserIdsAndKeyword(List.of("manager-1", "user-1", "user-2", "user-1-1"), null))
                .thenReturn(List.of(
                        buildUser("manager-1", "经理A"),
                        buildUser("user-1", "销售A"),
                        buildUser("user-2", "销售B"),
                        buildUser("user-1-1", "运营A")
                ));

        assertEquals(Set.of("manager-1", "user-1", "user-2", "user-1-1"),
                adminDataScopeService.getCurrentVisibleUserIds());

        List<VisibleUserOptionVO> result = adminDataScopeService.listCurrentVisibleUsers(null);
        assertEquals(4, result.size());
        assertEquals("经理A", result.get(0).getDisplayName());
    }

    @Test
    void shouldRestrictSelfScopeToCurrentUserOnly() {
        mockLoginUser("user-9", Set.of("admin"));
        when(adminUserDataScopeMapper.selectByUserId("user-9")).thenReturn(null);

        assertEquals(AdminDataScopeTypeEnum.SELF, adminDataScopeService.getCurrentScopeType());
        assertEquals(Set.of("user-9"), adminDataScopeService.getCurrentVisibleUserIds());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adminDataScopeService.validateCurrentUserCanViewTargetUser("user-1"));
        assertEquals("无权查看目标用户门店", exception.getMessage());

        User currentUser = buildUser("user-9", "普通用户");
        currentUser.setPhone("13800000000");
        when(userMapper.selectByUserId("user-9")).thenReturn(currentUser);

        List<VisibleUserOptionVO> result = adminDataScopeService.listCurrentVisibleUsers("1380");
        assertEquals(1, result.size());
        assertEquals("user-9", result.get(0).getUserId());
    }

    private void mockLoginUser(String userId, Set<String> roles) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        loginUser.setUserName(userId);
        loginUser.setRoles(roles);
        UserContext.setLoginUser(loginUser);
    }

    private User buildUser(String userId, String nickname) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(userId);
        user.setNickname(nickname);
        return user;
    }
}
