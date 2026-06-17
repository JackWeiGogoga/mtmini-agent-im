package com.gogoga.backend.service;

import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.vo.VisibleUserOptionVO;

import java.util.List;
import java.util.Set;

public interface AdminDataScopeService {

    AdminDataScopeTypeEnum getCurrentScopeType();

    Set<String> getCurrentVisibleUserIds();

    void validateCurrentUserCanViewTargetUser(String targetUserId);

    boolean canCurrentUserManageBindingsFor(String targetUserId);

    List<VisibleUserOptionVO> listCurrentVisibleUsers(String keyword);

    List<VisibleUserOptionVO> listCurrentVisibleUsersByRoleCode(String keyword, String roleCode);
}
