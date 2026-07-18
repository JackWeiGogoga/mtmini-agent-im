package com.gogoga.backend.service;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.UserBehaviorEvent;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.enums.AdminDataScopeTypeEnum;
import com.gogoga.backend.enums.BackendPortalType;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.UserBehaviorEventMapper;
import com.gogoga.backend.param.UserBehaviorAnalyticsQueryParam;
import com.gogoga.backend.param.UserBehaviorEventReportParam;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.UserBehaviorAnalyticsRowVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBehaviorAnalyticsServiceTest {

    @Mock
    private UserBehaviorEventMapper userBehaviorEventMapper;

    @Mock
    private MtH5ShopService mtH5ShopService;

    @Mock
    private UserShopBindingService userShopBindingService;

    @Mock
    private AdminDataScopeService adminDataScopeService;

    @InjectMocks
    private UserBehaviorAnalyticsService userBehaviorAnalyticsService;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void reportH5EventShouldUseCurrentShopAndShopPhoneSnapshot() {
        LoginUser loginUser = new LoginUser();
        loginUser.setEpoiId("epoi-1");
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(1L);
        shop.setPoiName("门店名称");
        shop.setContactPhone("13800000000");
        shop.setPhone("021-123456");
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        UserBehaviorEventReportParam param = new UserBehaviorEventReportParam();
        param.setEventType("H5_APP_OPEN");
        param.setClientEventId("h5-event-1");
        param.setSessionId("h5-session-1");
        param.setContextJson("{\"entrySource\":\"APP\"}");
        userBehaviorAnalyticsService.reportH5Event(param);

        ArgumentCaptor<UserBehaviorEvent> captor = ArgumentCaptor.forClass(UserBehaviorEvent.class);
        verify(userBehaviorEventMapper).insert(captor.capture());
        UserBehaviorEvent event = captor.getValue();
        assertEquals("H5", event.getEventSource());
        assertEquals("H5_APP_OPEN", event.getEventType());
        assertEquals("h5-event-1", event.getClientEventId());
        assertEquals("h5-session-1", event.getSessionId());
        assertEquals("{\"entrySource\":\"APP\"}", event.getContextJson());
        assertEquals(1L, event.getShopId());
        assertEquals("门店名称", event.getShopNameSnapshot());
        assertEquals("13800000000", event.getShopPhoneSnapshot());
        assertEquals("epoi-1", event.getH5EpoiId());
    }

    @Test
    void reportAdminEventShouldRejectMismatchedEventSource() {
        UserBehaviorEventReportParam param = new UserBehaviorEventReportParam();
        param.setEventType("H5_APP_OPEN");
        param.setClientEventId("admin-event-1");
        param.setSessionId("admin-session-1");
        param.setShopId(1L);

        assertThrows(BusinessException.class, () -> userBehaviorAnalyticsService.reportAdminEvent(param));
    }

    @Test
    void reportAdminEventShouldValidateVisibleShopAndRecordBackendUser() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin-1");
        loginUser.setActivePortal(BackendPortalType.ADMIN);
        UserContext.setLoginUser(loginUser);

        MtShop shop = new MtShop();
        shop.setId(8L);
        shop.setName("后台门店");
        shop.setPhone("400");
        when(userShopBindingService.requireCurrentUserVisibleShop(8L, null)).thenReturn(shop);

        UserBehaviorEventReportParam param = new UserBehaviorEventReportParam();
        param.setEventType("PC_COMMENT_LOG_VIEW");
        param.setClientEventId("admin-event-2");
        param.setSessionId("admin-session-1");
        param.setContextJson("{\"portalType\":\"ADMIN\"}");
        param.setShopId(8L);
        userBehaviorAnalyticsService.reportAdminEvent(param);

        ArgumentCaptor<UserBehaviorEvent> captor = ArgumentCaptor.forClass(UserBehaviorEvent.class);
        verify(userBehaviorEventMapper).insert(captor.capture());
        UserBehaviorEvent event = captor.getValue();
        assertEquals("ADMIN_BACKEND", event.getEventSource());
        assertEquals("PC_COMMENT_LOG_VIEW", event.getEventType());
        assertEquals("admin-1", event.getBackendUserId());
        assertEquals("400", event.getShopPhoneSnapshot());
    }

    @Test
    void reportH5EventShouldRejectLegacyPromotionEvent() {
        UserBehaviorEventReportParam param = new UserBehaviorEventReportParam();
        param.setEventType("H5_CONFIG_BUTTON_CLICK");
        param.setClientEventId("legacy-event");
        param.setSessionId("h5-session-1");

        assertThrows(BusinessException.class, () -> userBehaviorAnalyticsService.reportH5Event(param));
    }

    @Test
    void reportH5EventShouldRejectNonObjectContext() {
        MtShop shop = new MtShop();
        shop.setId(1L);
        when(mtH5ShopService.getCurrentShop()).thenReturn(shop);

        UserBehaviorEventReportParam param = new UserBehaviorEventReportParam();
        param.setEventType("H5_PAGE_VIEW");
        param.setClientEventId("h5-event-2");
        param.setSessionId("h5-session-1");
        param.setContextJson("[\"HOME\"]");

        assertThrows(BusinessException.class, () -> userBehaviorAnalyticsService.reportH5Event(param));
    }

    @Test
    void listAnalyticsRowsShouldUseSalesVisibleScopeAndWhitelistSort() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("sales-manager");
        loginUser.setActivePortal(BackendPortalType.SALES);
        UserContext.setLoginUser(loginUser);
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.TEAM);
        when(adminDataScopeService.getCurrentVisibleUserIds()).thenReturn(new LinkedHashSet<>(List.of("sales-manager", "sales-2")));
        when(userBehaviorEventMapper.selectAnalyticsRows(
                any(),
                any(),
                eq("测试"),
                eq("SALES_VISIBLE"),
                eq("sales-manager"),
                any(),
                eq("h5_app_open_count"),
                eq("DESC")
        )).thenReturn(List.of(new UserBehaviorAnalyticsRowVO()));

        UserBehaviorAnalyticsQueryParam query = new UserBehaviorAnalyticsQueryParam();
        query.setKeyword(" 测试 ");
        query.setStatsRange("LAST_7_DAYS");
        query.setSortField("h5AppOpenCount");
        query.setSortOrder("descend");

        PageInfo<UserBehaviorAnalyticsRowVO> page = userBehaviorAnalyticsService.listAnalyticsRows(query);

        assertEquals(1, page.getList().size());
        verify(userBehaviorEventMapper).selectAnalyticsRows(
                eq(LocalDate.now().minusDays(6).atStartOfDay()),
                eq(LocalDate.now().plusDays(1).atStartOfDay()),
                eq("测试"),
                eq("SALES_VISIBLE"),
                eq("sales-manager"),
                eq(List.of("sales-manager", "sales-2")),
                eq("h5_app_open_count"),
                eq("DESC")
        );
    }

    @Test
    void listAnalyticsRowsShouldIgnoreUnknownSortField() {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId("admin");
        loginUser.setActivePortal(BackendPortalType.ADMIN);
        loginUser.setRoles(Set.of("super_admin"));
        UserContext.setLoginUser(loginUser);
        when(adminDataScopeService.getCurrentScopeType()).thenReturn(AdminDataScopeTypeEnum.ALL);
        when(userBehaviorEventMapper.selectAnalyticsRows(
                any(),
                any(),
                any(),
                eq("ADMIN_ALL"),
                eq("admin"),
                any(),
                eq(null),
                eq(null)
        )).thenReturn(List.of());

        UserBehaviorAnalyticsQueryParam query = new UserBehaviorAnalyticsQueryParam();
        query.setSortField("rawSql");
        query.setSortOrder("descend");

        userBehaviorAnalyticsService.listAnalyticsRows(query);

        verify(userBehaviorEventMapper).selectAnalyticsRows(
                any(),
                any(),
                eq(null),
                eq("ADMIN_ALL"),
                eq("admin"),
                eq(List.of()),
                eq(null),
                eq(null)
        );
    }
}
