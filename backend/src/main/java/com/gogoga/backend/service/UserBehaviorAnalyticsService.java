package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.UserBehaviorEvent;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.enums.UserBehaviorAnalyticsSortFieldEnum;
import com.gogoga.backend.enums.UserBehaviorEventSourceEnum;
import com.gogoga.backend.enums.UserBehaviorEventTypeEnum;
import com.gogoga.backend.enums.UserBehaviorStatsRangeEnum;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.UserBehaviorEventMapper;
import com.gogoga.backend.param.UserBehaviorAnalyticsQueryParam;
import com.gogoga.backend.param.UserBehaviorEventReportParam;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.UserBehaviorAnalyticsRowVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBehaviorAnalyticsService {

    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserBehaviorEventMapper userBehaviorEventMapper;
    private final MtH5ShopService mtH5ShopService;
    private final UserShopBindingService userShopBindingService;
    private final AdminDataScopeService adminDataScopeService;

    @Transactional
    public void reportH5Event(UserBehaviorEventReportParam param) {
        if (param == null) {
            throw new BusinessException("埋点参数不能为空");
        }
        UserBehaviorEventTypeEnum eventType = UserBehaviorEventTypeEnum.requireForSource(
                param.getEventType(),
                UserBehaviorEventSourceEnum.H5
        );
        MtShop shop = mtH5ShopService.getCurrentShop();
        insertEvent(UserBehaviorEventSourceEnum.H5, eventType, shop, null, UserContext.getLoginUser() == null ? null : UserContext.getLoginUser().getEpoiId(), param.getContextJson());
    }

    @Transactional
    public void reportAdminEvent(UserBehaviorEventReportParam param) {
        if (param == null) {
            throw new BusinessException("埋点参数不能为空");
        }
        UserBehaviorEventTypeEnum eventType = UserBehaviorEventTypeEnum.requireForSource(
                param.getEventType(),
                UserBehaviorEventSourceEnum.ADMIN_BACKEND
        );
        Long shopId = param.getShopId();
        if (shopId == null) {
            throw new BusinessException("门店ID不能为空");
        }
        MtShop shop = userShopBindingService.requireCurrentUserVisibleShop(shopId, null);
        insertEvent(UserBehaviorEventSourceEnum.ADMIN_BACKEND, eventType, shop, UserContext.getUserId(), null, param.getContextJson());
    }

    public PageInfo<UserBehaviorAnalyticsRowVO> listAnalyticsRows(UserBehaviorAnalyticsQueryParam param) {
        UserBehaviorAnalyticsQueryParam query = param == null ? new UserBehaviorAnalyticsQueryParam() : param;
        int pageNum = normalizePageNum(query.getPageNum());
        int pageSize = normalizePageSize(query.getPageSize());
        UserBehaviorStatsRangeEnum statsRange = UserBehaviorStatsRangeEnum.fromValue(query.getStatsRange());
        LocalDate today = LocalDate.now();
        String sortColumn = UserBehaviorAnalyticsSortFieldEnum.resolveColumnAlias(query.getSortField());
        String sortDirection = UserBehaviorAnalyticsSortFieldEnum.resolveDirection(query.getSortOrder());
        if (sortColumn == null || sortDirection == null) {
            sortColumn = null;
            sortDirection = null;
        }

        VisibilityScope visibilityScope = resolveVisibilityScope();
        PageHelper.startPage(pageNum, pageSize);
        List<UserBehaviorAnalyticsRowVO> rows = userBehaviorEventMapper.selectAnalyticsRows(
                statsRange.startAt(today),
                statsRange.endAt(today),
                StringUtils.trimToNull(query.getKeyword()),
                visibilityScope.mode(),
                visibilityScope.currentUserId(),
                visibilityScope.visibleUserIds(),
                sortColumn,
                sortDirection
        );
        return new PageInfo<>(rows);
    }

    private void insertEvent(UserBehaviorEventSourceEnum source,
                             UserBehaviorEventTypeEnum eventType,
                             MtShop shop,
                             String backendUserId,
                             String h5EpoiId,
                             String contextJson) {
        if (shop == null || shop.getId() == null) {
            throw new BusinessException("门店不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        UserBehaviorEvent event = new UserBehaviorEvent();
        event.setEventSource(source.getValue());
        event.setEventType(eventType.getValue());
        event.setShopId(shop.getId());
        event.setShopNameSnapshot(StringUtils.firstNonBlank(shop.getPoiName(), shop.getName()));
        event.setShopPhoneSnapshot(StringUtils.firstNonBlank(shop.getContactPhone(), shop.getPhone()));
        event.setBackendUserId(StringUtils.trimToNull(backendUserId));
        event.setH5EpoiId(StringUtils.trimToNull(h5EpoiId));
        event.setEventTime(now);
        event.setContextJson(StringUtils.trimToNull(contextJson));
        event.setCreateTime(now);
        userBehaviorEventMapper.insert(event);
    }

    private VisibilityScope resolveVisibilityScope() {
        BackendPortalType activePortal = UserContext.getActivePortal();
        AdminDataScopeTypeEnum scopeType = adminDataScopeService.getCurrentScopeType();
        String currentUserId = UserContext.getUserId();
        if (activePortal == BackendPortalType.SALES) {
            return resolveBindingScope("SALES", scopeType, currentUserId);
        }
        if (activePortal == BackendPortalType.ADMIN && UserContext.isSuperAdmin()) {
            return new VisibilityScope("ADMIN_ALL", currentUserId, List.of());
        }
        return resolveBindingScope("ORDINARY", scopeType, currentUserId);
    }

    private VisibilityScope resolveBindingScope(String bindingPrefix, AdminDataScopeTypeEnum scopeType, String currentUserId) {
        if (scopeType == AdminDataScopeTypeEnum.ALL) {
            return new VisibilityScope(bindingPrefix + "_ALL", currentUserId, List.of());
        }
        if (scopeType == AdminDataScopeTypeEnum.TEAM) {
            List<String> visibleUserIds = new ArrayList<>(adminDataScopeService.getCurrentVisibleUserIds());
            if (visibleUserIds.isEmpty()) {
                return new VisibilityScope("NONE", currentUserId, List.of());
            }
            return new VisibilityScope(bindingPrefix + "_VISIBLE", currentUserId, visibleUserIds);
        }
        return new VisibilityScope(bindingPrefix + "_SELF", currentUserId, List.of());
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private record VisibilityScope(String mode, String currentUserId, List<String> visibleUserIds) {
    }
}
