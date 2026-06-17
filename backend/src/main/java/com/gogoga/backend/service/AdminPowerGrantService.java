package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.vo.AdminPowerGrantVO;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminPowerGrantService {

    AdminPowerGrantVO createGrant(Long shopId, Integer powerAmount, String grantReason, String remark);

    List<AdminPowerGrantVO> listByShopId(Long shopId);

    PageInfo<AdminPowerGrantVO> listByShopId(
            Long shopId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize
    );
}
