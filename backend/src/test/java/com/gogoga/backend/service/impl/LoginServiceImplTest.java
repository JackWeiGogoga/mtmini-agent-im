package com.gogoga.backend.service.impl;

import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.AuthProviderEnum;
import com.gogoga.backend.mapper.MenuMapper;
import com.gogoga.backend.mapper.RoleMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.service.BackendPortalService;
import com.gogoga.backend.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private MenuMapper menuMapper;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private BackendPortalService backendPortalService;

    @InjectMocks
    private LoginServiceImpl loginService;

    @Test
    void shouldRecoverWhenMtShopUserIsCreatedConcurrently() {
        MtShop shop = new MtShop();
        shop.setId(2994L);
        shop.setEpoiId("epoi-2994");
        shop.setPoiId("poi-2994");
        shop.setPoiName("并发门店");
        shop.setLogo("https://example.com/logo.png");

        User existingUser = new User();
        existingUser.setUserId("mtshop_2994");
        existingUser.setUsername("poi-2994");
        existingUser.setNickname("并发门店");
        existingUser.setAuthProvider(AuthProviderEnum.MT_H5.name());
        existingUser.setStatus(1);

        Role role = new Role();
        role.setRoleCode("USER");

        when(userMapper.selectByUserId("mtshop_2994"))
                .thenReturn(null)
                .thenReturn(existingUser);
        doThrow(new DuplicateKeyException("Duplicate entry 'mtshop_2994' for key 'ai_user.uniq_userid'"))
                .when(userMapper).insert(any(User.class));
        when(roleMapper.selectByUserId("mtshop_2994")).thenReturn(List.of(role));
        when(menuMapper.selectPermsByUserId("mtshop_2994")).thenReturn(List.of("h5:home:view"));
        when(tokenService.createToken(any(LoginUser.class))).thenReturn("token-2994");

        LoginUser loginUser = loginService.loginMtShop(shop);

        assertEquals("mtshop_2994", loginUser.getUserId());
        assertEquals("并发门店", loginUser.getUserName());
        assertEquals("epoi-2994", loginUser.getEpoiId());
        assertEquals("token-2994", loginUser.getToken());
        assertTrue(loginUser.getRoles().contains("USER"));
        assertTrue(loginUser.getPermissions().contains("h5:home:view"));

        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insertIgnoreDuplicate(roleCaptor.capture());
        assertEquals("mtshop_2994", roleCaptor.getValue().getUserId());
        assertEquals("ROLE_USER", roleCaptor.getValue().getRoleId());
    }
}
