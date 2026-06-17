package com.gogoga.backend.service.auth.impl;

import com.gogoga.backend.entity.UserRole;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserRoleMapper;
import com.gogoga.backend.service.SmsLoginService;
import com.gogoga.backend.service.auth.AuthRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsAuthProviderTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @Mock
    private SmsLoginService smsLoginService;

    @InjectMocks
    private SmsAuthProvider smsAuthProvider;

    @Test
    void shouldAssignYouzanRoleToNewPhoneUserFromYouzanPortal() {
        when(smsLoginService.verifyLoginCode("13800000001", "123456"))
                .thenReturn("13800000001");
        when(userMapper.selectByPhone("13800000001")).thenReturn(null);

        smsAuthProvider.authenticate(AuthRequest.builder()
                .phone("13800000001")
                .smsCode("123456")
                .portal("YOUZAN")
                .build());

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(captor.capture());
        assertEquals("ROLE_YOUZAN_USER", captor.getValue().getRoleId());
    }

    @Test
    void shouldKeepOrdinaryDefaultRoleForNewPhoneUserWithoutYouzanPortal() {
        when(smsLoginService.verifyLoginCode("13800000002", "123456"))
                .thenReturn("13800000002");
        when(userMapper.selectByPhone("13800000002")).thenReturn(null);

        smsAuthProvider.authenticate(AuthRequest.builder()
                .phone("13800000002")
                .smsCode("123456")
                .portal("ORDINARY")
                .build());

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(captor.capture());
        assertEquals("ROLE_USER", captor.getValue().getRoleId());
    }
}
