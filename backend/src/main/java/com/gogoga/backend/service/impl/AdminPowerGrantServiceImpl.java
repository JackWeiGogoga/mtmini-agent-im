package com.gogoga.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtPowerGrant;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerGrantMapper;
import com.gogoga.backend.service.AdminPowerGrantService;
import com.gogoga.backend.service.MtPowerAccountService;
import com.gogoga.backend.service.MtShopService;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminPowerGrantVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPowerGrantServiceImpl implements AdminPowerGrantService {

    private static final String STATUS_SUCCESS = "SUCCESS";

    private final MtPowerGrantMapper mtPowerGrantMapper;
    private final MtShopService mtShopService;
    private final MtPowerAccountService mtPowerAccountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminPowerGrantVO createGrant(Long shopId, Integer powerAmount, String grantReason, String remark) {
        requireSuperAdmin();
        if (shopId == null) {
            throw new BusinessException("店铺ID不能为空");
        }
        if (powerAmount == null || powerAmount <= 0) {
            throw new BusinessException("发放算力必须大于0");
        }
        if (StringUtils.isBlank(grantReason)) {
            throw new BusinessException("发放原因不能为空");
        }
        MtShop shop = mtShopService.findById(shopId);
        if (shop == null) {
            throw new BusinessException("门店不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        MtPowerGrant grant = new MtPowerGrant();
        grant.setGrantNo(buildGrantNo());
        grant.setShopId(shopId);
        grant.setPowerAmount(powerAmount);
        grant.setGrantReason(StringUtils.trim(grantReason));
        grant.setRemark(StringUtils.trimToNull(remark));
        grant.setOperatorUserId(UserContext.getUserId());
        grant.setOperatorUsername(UserContext.getUserName());
        grant.setStatus(STATUS_SUCCESS);
        grant.setCreateTime(now);
        grant.setUpdateTime(now);
        mtPowerGrantMapper.insert(grant);
        mtPowerAccountService.addAdminGrant(shopId, powerAmount);
        return toVO(grant, shop);
    }

    @Override
    public List<AdminPowerGrantVO> listByShopId(Long shopId) {
        requireSuperAdmin();
        if (shopId == null) {
            throw new BusinessException("店铺ID不能为空");
        }
        MtShop shop = mtShopService.findById(shopId);
        if (shop == null) {
            throw new BusinessException("门店不存在");
        }
        return mtPowerGrantMapper.selectByShopId(shopId).stream()
                .map(item -> toVO(item, shop))
                .collect(Collectors.toList());
    }

    @Override
    public PageInfo<AdminPowerGrantVO> listByShopId(
            Long shopId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer pageNum,
            Integer pageSize
    ) {
        requireSuperAdmin();
        if (shopId == null) {
            throw new BusinessException("店铺ID不能为空");
        }
        MtShop shop = mtShopService.findById(shopId);
        if (shop == null) {
            throw new BusinessException("门店不存在");
        }
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<AdminPowerGrantVO> records = mtPowerGrantMapper
                .selectByShopIdAndCreateTimeBetween(shopId, startTime, endTime)
                .stream()
                .map(item -> toVO(item, shop))
                .collect(Collectors.toList());
        return new PageInfo<>(records);
    }

    private AdminPowerGrantVO toVO(MtPowerGrant grant, MtShop shop) {
        AdminPowerGrantVO vo = new AdminPowerGrantVO();
        vo.setId(grant.getId());
        vo.setGrantNo(grant.getGrantNo());
        vo.setShopId(grant.getShopId());
        vo.setPoiId(shop == null ? null : shop.getPoiId());
        vo.setShopName(shop == null ? null : StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName()));
        vo.setPowerAmount(grant.getPowerAmount());
        vo.setGrantReason(grant.getGrantReason());
        vo.setRemark(grant.getRemark());
        vo.setOperatorUserId(grant.getOperatorUserId());
        vo.setOperatorUsername(grant.getOperatorUsername());
        vo.setStatus(grant.getStatus());
        vo.setCreateTime(grant.getCreateTime());
        return vo;
    }

    private String buildGrantNo() {
        return "PG" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(403, "仅超级管理员可执行该操作");
        }
    }
}
