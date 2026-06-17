package com.gogoga.backend.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.UserShopBinding;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.enums.ShopBindingDomainEnum;
import com.gogoga.backend.enums.ShopBindingStatusEnum;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.service.AdminDataScopeService;
import com.gogoga.backend.service.BackendPortalService;
import com.gogoga.backend.mapper.SalesUserShopBindingMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.mapper.UserShopBindingMapper;
import com.gogoga.backend.service.MtShopService;
import com.gogoga.backend.service.UserShopBindingService;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.UserShopBatchBindResultVO;
import com.gogoga.backend.vo.UserShopBindingVO;
import com.gogoga.backend.vo.VisibleUserOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 当前登录用户门店绑定服务实现
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserShopBindingServiceImpl implements UserShopBindingService {

    private final UserShopBindingMapper userShopBindingMapper;
    private final SalesUserShopBindingMapper salesUserShopBindingMapper;
    private final MtShopService mtShopService;
    private final UserMapper userMapper;
    private final AdminDataScopeService adminDataScopeService;

    @Override
    @Transactional
    public UserShopBindingVO bindCurrentUserShop(String poiId, String targetUserId) {
        return bindCurrentUserShop(poiId, targetUserId, null);
    }

    @Override
    @Transactional
    public UserShopBindingVO bindCurrentUserShop(String poiId, String targetUserId, String bindingDomain) {
        ShopBindingDomainEnum resolvedDomain = resolveMutationDomain(bindingDomain);
        String ownerUserId = resolveBindingOwnerUserId(targetUserId);
        BindingExecution execution = bindShopInternal(ownerUserId, poiId, resolvedDomain);
        UserShopBinding binding = execution.getBinding();
        MtShop shop = execution.getShop();
        return buildFallbackVo(binding, shop, resolvedDomain);
    }

    @Override
    @Transactional
    public UserShopBatchBindResultVO batchBindCurrentUserShops(List<String> poiIds, String targetUserId) {
        return batchBindCurrentUserShops(poiIds, targetUserId, null);
    }

    @Override
    @Transactional
    public UserShopBatchBindResultVO batchBindCurrentUserShops(List<String> poiIds, String targetUserId, String bindingDomain) {
        ShopBindingDomainEnum resolvedDomain = resolveMutationDomain(bindingDomain);
        String ownerUserId = resolveBindingOwnerUserId(targetUserId);
        List<String> normalizedPoiIds = normalizePoiIds(poiIds);
        if (normalizedPoiIds.isEmpty()) {
            throw new BusinessException("门店ID列表不能为空");
        }

        List<String> successPoiIds = new ArrayList<>();
        List<UserShopBatchBindResultVO.FailureItem> failureItems = new ArrayList<>();
        for (String poiId : normalizedPoiIds) {
            try {
                bindShopInternal(ownerUserId, poiId, resolvedDomain);
                successPoiIds.add(poiId);
            } catch (BusinessException e) {
                UserShopBatchBindResultVO.FailureItem item = new UserShopBatchBindResultVO.FailureItem();
                item.setPoiId(poiId);
                item.setErrorMessage(e.getMessage());
                failureItems.add(item);
            }
        }

        UserShopBatchBindResultVO result = new UserShopBatchBindResultVO();
        result.setTotalCount(normalizedPoiIds.size());
        result.setSuccessCount(successPoiIds.size());
        result.setFailureCount(failureItems.size());
        result.setSuccessPoiIds(successPoiIds);
        result.setFailureItems(failureItems);
        return result;
    }

    @Override
    public PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, Integer pageNum, Integer pageSize) {
        return listCurrentUserShops(keyword, targetUserId, null, pageNum, pageSize);
    }

    @Override
    public PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, Integer pageNum, Integer pageSize) {
        return listCurrentUserShops(keyword, targetUserId, bindingDomain, null, pageNum, pageSize);
    }

    @Override
    public PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, String bindingStatus, Integer pageNum, Integer pageSize) {
        return listCurrentUserShops(keyword, targetUserId, bindingDomain, bindingStatus, null, null, pageNum, pageSize);
    }

    @Override
    public PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, String bindingStatus, String sortField, String sortOrder, Integer pageNum, Integer pageSize) {
        return listCurrentUserShops(keyword, targetUserId, bindingDomain, bindingStatus, null, sortField, sortOrder, pageNum, pageSize);
    }

    @Override
    public PageInfo<UserShopBindingVO> listCurrentUserShops(String keyword, String targetUserId, String bindingDomain, String bindingStatus, String promotionStatus, String sortField, String sortOrder, Integer pageNum, Integer pageSize) {
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String normalizedTargetUserId = StringUtils.trimToNull(targetUserId);
        String normalizedSortDirection = normalizePowerBalanceSortDirection(sortField, sortOrder);
        String normalizedPromotionStatus = normalizePromotionStatus(promotionStatus);
        ShopBindingDomainEnum resolvedDomain = resolveQueryDomain(bindingDomain);
        ShopBindingStatusEnum resolvedStatus = resolveQueryBindingStatus(bindingStatus);
        adminDataScopeService.validateCurrentUserCanViewTargetUser(normalizedTargetUserId);
        AdminDataScopeTypeEnum scopeType = adminDataScopeService.getCurrentScopeType();
        List<String> visibleUserIds = scopeType == AdminDataScopeTypeEnum.TEAM
                ? new ArrayList<>(adminDataScopeService.getCurrentVisibleUserIds())
                : List.of();
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        List<UserShopBindingVO> bindings = listBindingsByScope(
                StringUtils.trimToNull(keyword),
                normalizedTargetUserId,
                resolvedDomain,
                resolvedStatus,
                normalizedPromotionStatus,
                scopeType,
                visibleUserIds,
                normalizedSortDirection
        );
        warnDuplicateMerchantBindings(bindings, "shop-management");
        if (isYouzanPortal()) {
            bindings.forEach(this::redactPowerFields);
        }
        return new PageInfo<>(bindings);
    }

    @Override
    public List<VisibleUserOptionVO> listVisibleUsers(String keyword) {
        return listVisibleUsers(keyword, null);
    }

    @Override
    public List<VisibleUserOptionVO> listVisibleUsers(String keyword, String bindingDomain) {
        ShopBindingDomainEnum resolvedDomain = resolveVisibleUsersDomain(bindingDomain);
        if (!(isAdminPortal() && UserContext.isSuperAdmin())) {
            return adminDataScopeService.listCurrentVisibleUsers(keyword);
        }
        if (resolvedDomain == ShopBindingDomainEnum.SALES) {
            return adminDataScopeService.listCurrentVisibleUsersByRoleCode(
                    keyword,
                    BackendPortalService.ROLE_CODE_SALES
            );
        }
        if (resolvedDomain == ShopBindingDomainEnum.ORDINARY) {
            return adminDataScopeService.listCurrentVisibleUsersByRoleCode(
                    keyword,
                    BackendPortalService.ROLE_CODE_USER
            );
        }
        return adminDataScopeService.listCurrentVisibleUsers(keyword);
    }

    @Override
    public MtShop requireCurrentUserBoundShop(Long shopId) {
        return requireCurrentUserBoundShop(shopId, null);
    }

    @Override
    public MtShop requireCurrentUserBoundShop(Long shopId, String bindingDomain) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }

        ShopBindingDomainEnum resolvedDomain = resolveReadDomain(bindingDomain);
        UserShopBinding binding;
        AdminDataScopeTypeEnum scopeType = adminDataScopeService.getCurrentScopeType();
        if (scopeType == AdminDataScopeTypeEnum.ALL) {
            binding = resolvedDomain == ShopBindingDomainEnum.SALES
                    ? salesUserShopBindingMapper.selectByShopId(shopId)
                    : userShopBindingMapper.selectByShopId(shopId);
        } else if (scopeType == AdminDataScopeTypeEnum.SELF) {
            binding = resolvedDomain == ShopBindingDomainEnum.SALES
                    ? salesUserShopBindingMapper.selectByUserIdAndShopId(requireCurrentUserId(), shopId)
                    : userShopBindingMapper.selectByUserIdAndShopId(requireCurrentUserId(), shopId);
        } else {
            List<String> visibleUserIds = new ArrayList<>(adminDataScopeService.getCurrentVisibleUserIds());
            if (visibleUserIds.isEmpty()) {
                binding = null;
            } else {
                binding = resolvedDomain == ShopBindingDomainEnum.SALES
                    ? salesUserShopBindingMapper.selectByVisibleUserIdsAndShopId(
                            visibleUserIds,
                            shopId
                    )
                    : userShopBindingMapper.selectByVisibleUserIdsAndShopId(
                            visibleUserIds,
                            shopId
                    );
            }
        }
        if (binding == null) {
            throw new BusinessException("目标门店不在当前可见范围内");
        }

        MtShop shop = mtShopService.findById(shopId);
        if (shop == null) {
            throw new BusinessException("门店不存在");
        }
        return shop;
    }

    @Override
    public MtShop requireCurrentUserVisibleShop(Long shopId, String bindingDomain) {
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            MtShop shop = mtShopService.findById(shopId);
            if (shop == null) {
                throw new BusinessException("门店不存在");
            }
            return shop;
        }
        return requireCurrentUserBoundShop(shopId, bindingDomain);
    }

    @Override
    @Transactional
    public void unbindCurrentUserShop(Long bindingId) {
        unbindCurrentUserShop(bindingId, null);
    }

    @Override
    @Transactional
    public void unbindCurrentUserShop(Long bindingId, String bindingDomain) {
        if (bindingId == null) {
            throw new BusinessException("绑定记录ID不能为空");
        }
        ShopBindingDomainEnum resolvedDomain = resolveMutationDomain(bindingDomain);
        UserShopBinding binding = resolvedDomain == ShopBindingDomainEnum.SALES
                ? salesUserShopBindingMapper.selectById(bindingId)
                : userShopBindingMapper.selectById(bindingId);
        if (binding == null) {
            throw new BusinessException("绑定记录不存在或无权解绑");
        }
        String currentUserId = requireCurrentUserId();
        if (StringUtils.equals(currentUserId, binding.getUserId())) {
            int affectedRows = resolvedDomain == ShopBindingDomainEnum.SALES
                    ? salesUserShopBindingMapper.deleteByIdAndUserId(bindingId, currentUserId)
                    : userShopBindingMapper.deleteByIdAndUserId(bindingId, currentUserId);
            if (affectedRows <= 0) {
                throw new BusinessException("绑定记录不存在或无权解绑");
            }
            return;
        }
        if (UserContext.isSuperAdmin() && adminDataScopeService.canCurrentUserManageBindingsFor(binding.getUserId())) {
            int affectedRows = resolvedDomain == ShopBindingDomainEnum.SALES
                    ? salesUserShopBindingMapper.deleteById(bindingId)
                    : userShopBindingMapper.deleteById(bindingId);
            if (affectedRows <= 0) {
                throw new BusinessException("绑定记录不存在或无权解绑");
            }
            return;
        }
        throw new BusinessException("绑定记录不存在或无权解绑");
    }

    private List<UserShopBindingVO> listBindingsByScope(
            String keyword,
            String targetUserId,
            ShopBindingDomainEnum bindingDomain,
            ShopBindingStatusEnum bindingStatus,
            String promotionStatus,
            AdminDataScopeTypeEnum scopeType,
            List<String> visibleUserIds,
            String sortDirection
    ) {
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            return promotionStatus == null
                    ? userShopBindingMapper.selectAdminMerchantShops(
                            bindingDomain.name(),
                            bindingStatus.name(),
                            targetUserId,
                            keyword,
                            sortDirection
                    )
                    : userShopBindingMapper.selectAdminMerchantShops(
                            bindingDomain.name(),
                            bindingStatus.name(),
                            targetUserId,
                            keyword,
                            promotionStatus,
                            sortDirection
                    );
        }
        if (targetUserId != null) {
            return bindingDomain == ShopBindingDomainEnum.SALES
                    ? (promotionStatus == null
                            ? salesUserShopBindingMapper.selectBoundShopsByUserId(targetUserId, keyword, sortDirection)
                            : salesUserShopBindingMapper.selectBoundShopsByUserId(targetUserId, keyword, promotionStatus, sortDirection))
                    : (promotionStatus == null
                            ? userShopBindingMapper.selectBoundShopsByUserId(targetUserId, keyword, sortDirection)
                            : userShopBindingMapper.selectBoundShopsByUserId(targetUserId, keyword, promotionStatus, sortDirection));
        }
        if (scopeType == AdminDataScopeTypeEnum.ALL) {
            return bindingDomain == ShopBindingDomainEnum.SALES
                    ? (promotionStatus == null
                            ? salesUserShopBindingMapper.selectAllBoundShops(keyword, sortDirection)
                            : salesUserShopBindingMapper.selectAllBoundShops(keyword, promotionStatus, sortDirection))
                    : (promotionStatus == null
                            ? userShopBindingMapper.selectAllBoundShops(keyword, sortDirection)
                            : userShopBindingMapper.selectAllBoundShops(keyword, promotionStatus, sortDirection));
        }
        if (scopeType == AdminDataScopeTypeEnum.SELF) {
            return bindingDomain == ShopBindingDomainEnum.SALES
                    ? (promotionStatus == null
                            ? salesUserShopBindingMapper.selectBoundShopsByUserId(requireCurrentUserId(), keyword, sortDirection)
                            : salesUserShopBindingMapper.selectBoundShopsByUserId(requireCurrentUserId(), keyword, promotionStatus, sortDirection))
                    : (promotionStatus == null
                            ? userShopBindingMapper.selectBoundShopsByUserId(requireCurrentUserId(), keyword, sortDirection)
                            : userShopBindingMapper.selectBoundShopsByUserId(requireCurrentUserId(), keyword, promotionStatus, sortDirection));
        }
        if (visibleUserIds == null || visibleUserIds.isEmpty()) {
            return List.of();
        }
        return bindingDomain == ShopBindingDomainEnum.SALES
                ? (promotionStatus == null
                        ? salesUserShopBindingMapper.selectBoundShopsByVisibleUserIds(visibleUserIds, null, keyword, sortDirection)
                        : salesUserShopBindingMapper.selectBoundShopsByVisibleUserIds(visibleUserIds, null, keyword, promotionStatus, sortDirection))
                : (promotionStatus == null
                        ? userShopBindingMapper.selectBoundShopsByVisibleUserIds(visibleUserIds, null, keyword, sortDirection)
                        : userShopBindingMapper.selectBoundShopsByVisibleUserIds(visibleUserIds, null, keyword, promotionStatus, sortDirection));
    }

    private String normalizePromotionStatus(String promotionStatus) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(promotionStatus));
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        if (StringUtils.equals(normalized, "ALL")) {
            return "ALL";
        }
        if (StringUtils.equalsAny(normalized, "ENABLED", "DISABLED")) {
            return normalized;
        }
        throw new BusinessException(400, "推广开关筛选状态不正确");
    }

    private String normalizePowerBalanceSortDirection(String sortField, String sortOrder) {
        if (!StringUtils.equalsAnyIgnoreCase(StringUtils.trimToEmpty(sortField), "powerBalance", "balance")) {
            return null;
        }
        String normalizedOrder = StringUtils.trimToEmpty(sortOrder);
        if (StringUtils.equalsAnyIgnoreCase(normalizedOrder, "ascend", "asc")) {
            return "ASC";
        }
        if (StringUtils.equalsAnyIgnoreCase(normalizedOrder, "descend", "desc")) {
            return "DESC";
        }
        return null;
    }

    private String requireCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException("当前用户未登录");
        }
        return userId;
    }

    private BindingExecution bindShopInternal(String currentUserId, String poiId, ShopBindingDomainEnum bindingDomain) {
        String normalizedPoiId = StringUtils.trimToEmpty(poiId);
        if (StringUtils.isBlank(normalizedPoiId)) {
            throw new BusinessException("门店ID不能为空");
        }

        UserShopBinding currentBinding = bindingDomain == ShopBindingDomainEnum.SALES
                ? salesUserShopBindingMapper.selectByUserIdAndPoiId(currentUserId, normalizedPoiId)
                : userShopBindingMapper.selectByUserIdAndPoiId(currentUserId, normalizedPoiId);
        if (currentBinding != null) {
            throw new BusinessException("该门店已绑定到当前用户，请勿重复绑定");
        }

        UserShopBinding existingBinding = bindingDomain == ShopBindingDomainEnum.SALES
                ? salesUserShopBindingMapper.selectByPoiId(normalizedPoiId)
                : userShopBindingMapper.selectByPoiId(normalizedPoiId);
        if (existingBinding != null) {
            throw new BusinessException(buildAlreadyBoundMessage(existingBinding));
        }

        MtShop shop = mtShopService.getByPoiIdOrThrow(normalizedPoiId);
        LocalDateTime now = LocalDateTime.now();

        UserShopBinding binding = new UserShopBinding();
        binding.setUserId(currentUserId);
        binding.setShopId(shop.getId());
        binding.setPoiId(shop.getPoiId());
        binding.setCreateTime(now);
        binding.setUpdateTime(now);
        if (bindingDomain == ShopBindingDomainEnum.SALES) {
            salesUserShopBindingMapper.insert(binding);
        } else {
            userShopBindingMapper.insert(binding);
        }
        return new BindingExecution(binding, shop);
    }

    private String resolveBindingOwnerUserId(String targetUserId) {
        String currentUserId = requireCurrentUserId();
        String normalizedTargetUserId = StringUtils.trimToNull(targetUserId);
        if (normalizedTargetUserId == null || StringUtils.equals(normalizedTargetUserId, currentUserId)) {
            return currentUserId;
        }
        if (!adminDataScopeService.canCurrentUserManageBindingsFor(normalizedTargetUserId)) {
            throw new BusinessException(403, "无权为目标用户管理门店绑定");
        }
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || !loginUser.isSuperAdmin()) {
            throw new BusinessException(403, "仅超级管理员可为其他用户管理门店绑定");
        }
        return normalizedTargetUserId;
    }

    private List<String> normalizePoiIds(List<String> poiIds) {
        if (poiIds == null) {
            return List.of();
        }
        Set<String> uniquePoiIds = new LinkedHashSet<>();
        for (String poiId : poiIds) {
            String normalizedPoiId = StringUtils.trimToNull(poiId);
            if (normalizedPoiId != null) {
                uniquePoiIds.add(normalizedPoiId);
            }
        }
        return new ArrayList<>(uniquePoiIds);
    }

    private String buildAlreadyBoundMessage(UserShopBinding binding) {
        String boundUserId = binding == null ? null : binding.getUserId();
        if (StringUtils.isBlank(boundUserId)) {
            return "该门店已被其他用户绑定";
        }
        User user = userMapper.selectByUserId(boundUserId);
        if (user == null) {
            return "该门店已被其他用户绑定";
        }
        String displayName = StringUtils.firstNonBlank(user.getNickname(), user.getUsername(), user.getUserId());
        return "该门店已被用户「" + displayName + "」绑定";
    }

    private ShopBindingDomainEnum resolveQueryDomain(String bindingDomain) {
        ShopBindingDomainEnum requestedDomain = ShopBindingDomainEnum.fromValue(bindingDomain);
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            return requestedDomain == null ? ShopBindingDomainEnum.ALL : requestedDomain;
        }
        return resolvePortalDomain(requestedDomain);
    }

    private ShopBindingStatusEnum resolveQueryBindingStatus(String bindingStatus) {
        ShopBindingStatusEnum requestedStatus = ShopBindingStatusEnum.fromValue(bindingStatus);
        if (requestedStatus == null) {
            requestedStatus = ShopBindingStatusEnum.ALL;
        }
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            return requestedStatus;
        }
        if (requestedStatus != ShopBindingStatusEnum.ALL) {
            throw new BusinessException(403, "当前工作台无权访问所选绑定状态");
        }
        return ShopBindingStatusEnum.ALL;
    }

    private ShopBindingDomainEnum resolveVisibleUsersDomain(String bindingDomain) {
        ShopBindingDomainEnum requestedDomain = ShopBindingDomainEnum.fromValue(bindingDomain);
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            return requestedDomain == null ? ShopBindingDomainEnum.ALL : requestedDomain;
        }
        return resolvePortalDomain(requestedDomain);
    }

    private ShopBindingDomainEnum resolveReadDomain(String bindingDomain) {
        ShopBindingDomainEnum requestedDomain = ShopBindingDomainEnum.fromValue(bindingDomain);
        if (requestedDomain == ShopBindingDomainEnum.ALL) {
            throw new BusinessException("行级操作不支持全部绑定域");
        }
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            return requestedDomain == null ? ShopBindingDomainEnum.ORDINARY : requestedDomain;
        }
        return resolvePortalDomain(requestedDomain);
    }

    private ShopBindingDomainEnum resolveMutationDomain(String bindingDomain) {
        ShopBindingDomainEnum requestedDomain = ShopBindingDomainEnum.fromValue(bindingDomain);
        if (isAdminPortal() && UserContext.isSuperAdmin()) {
            if (requestedDomain == null || requestedDomain == ShopBindingDomainEnum.ALL) {
                throw new BusinessException("请选择普通商户或网销用户绑定域");
            }
            return requestedDomain;
        }
        if (requestedDomain == ShopBindingDomainEnum.ALL) {
            throw new BusinessException("行级操作不支持全部绑定域");
        }
        return resolvePortalDomain(requestedDomain);
    }

    private ShopBindingDomainEnum resolvePortalDomain(ShopBindingDomainEnum requestedDomain) {
        ShopBindingDomainEnum portalDomain = isSalesPortal()
                ? ShopBindingDomainEnum.SALES
                : ShopBindingDomainEnum.ORDINARY;
        if (requestedDomain != null && requestedDomain != portalDomain) {
            throw new BusinessException(403, "当前工作台无权访问所选绑定域");
        }
        return portalDomain;
    }

    private boolean isSalesPortal() {
        return UserContext.getActivePortal() == BackendPortalType.SALES;
    }

    private boolean isAdminPortal() {
        return UserContext.getActivePortal() == BackendPortalType.ADMIN;
    }

    private boolean isYouzanPortal() {
        return UserContext.getActivePortal() == BackendPortalType.YOUZAN;
    }

    private UserShopBindingVO buildFallbackVo(UserShopBinding binding, MtShop shop, ShopBindingDomainEnum bindingDomain) {
        UserShopBindingVO vo = new UserShopBindingVO();
        vo.setBindingId(binding.getId());
        vo.setShopId(shop.getId());
        vo.setPoiId(shop.getPoiId());
        vo.setEpoiId(shop.getEpoiId());
        vo.setShopName(StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName()));
        vo.setAddress(shop.getAddress());
        vo.setContactPhone(StringUtils.firstNonBlank(shop.getContactPhone(), shop.getPhone()));
        vo.setBoundUserId(binding.getUserId());
        User boundUser = userMapper.selectByUserId(binding.getUserId());
        vo.setBoundUserName(boundUser == null
                ? binding.getUserId()
                : StringUtils.firstNonBlank(boundUser.getNickname(), boundUser.getUsername(), boundUser.getUserId()));
        vo.setBindingDomain(bindingDomain.name());
        vo.setBindingDomainName(bindingDomain.getLabel());
        vo.setBindingStatus("BOUND");
        vo.setBindingStatusName("已绑定");
        if (bindingDomain == ShopBindingDomainEnum.SALES) {
            vo.setSalesBindingId(binding.getId());
            vo.setSalesBoundUserId(binding.getUserId());
            vo.setSalesBoundUserName(vo.getBoundUserName());
            vo.setSalesBindTime(binding.getCreateTime());
        } else {
            vo.setOrdinaryBindingId(binding.getId());
            vo.setOrdinaryBoundUserId(binding.getUserId());
            vo.setOrdinaryBoundUserName(vo.getBoundUserName());
            vo.setOrdinaryBindTime(binding.getCreateTime());
        }
        vo.setPowerBalance(0);
        vo.setTotalRecharge(0);
        vo.setTotalGrant(0);
        vo.setTotalConsume(0);
        if (isYouzanPortal()) {
            redactPowerFields(vo);
        }
        vo.setRightExpireAt(null);
        vo.setBindTime(binding.getCreateTime());
        return vo;
    }

    private void redactPowerFields(UserShopBindingVO vo) {
        if (vo == null) {
            return;
        }
        vo.setPowerBalance(null);
        vo.setTotalRecharge(null);
        vo.setTotalGrant(null);
        vo.setTotalConsume(null);
    }

    private void warnDuplicateMerchantBindings(List<UserShopBindingVO> rows, String scene) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (UserShopBindingVO row : rows) {
            if (row == null || row.getShopId() == null) {
                continue;
            }
            if (row.getOrdinaryBindingCount() != null && row.getOrdinaryBindingCount() > 1) {
                log.warn("检测到普通商户绑定重复，scene={}, shopId={}, count={}, chosenBindingId={}",
                        scene, row.getShopId(), row.getOrdinaryBindingCount(), row.getOrdinaryBindingId());
            }
            if (row.getSalesBindingCount() != null && row.getSalesBindingCount() > 1) {
                log.warn("检测到网销用户绑定重复，scene={}, shopId={}, count={}, chosenBindingId={}",
                        scene, row.getShopId(), row.getSalesBindingCount(), row.getSalesBindingId());
            }
        }
    }

    @lombok.Getter
    @RequiredArgsConstructor
    private static class BindingExecution {

        private final UserShopBinding binding;

        private final MtShop shop;
    }
}
