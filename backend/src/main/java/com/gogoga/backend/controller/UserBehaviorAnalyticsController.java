package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.UserBehaviorAnalyticsQueryParam;
import com.gogoga.backend.param.UserBehaviorEventReportParam;
import com.gogoga.backend.service.UserBehaviorAnalyticsService;
import com.gogoga.backend.vo.ResultVO;
import com.gogoga.backend.vo.UserBehaviorAnalyticsRowVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequiresLogin
public class UserBehaviorAnalyticsController {

    private final UserBehaviorAnalyticsService userBehaviorAnalyticsService;

    @PostMapping("/api/h5/behavior-events")
    public ResultVO<Boolean> reportH5Event(@Valid @RequestBody UserBehaviorEventReportParam param) {
        userBehaviorAnalyticsService.reportH5Event(param);
        return ResultVO.success(Boolean.TRUE);
    }

    @PostMapping("/api/system/behavior-events")
    public ResultVO<Boolean> reportAdminEvent(@Valid @RequestBody UserBehaviorEventReportParam param) {
        userBehaviorAnalyticsService.reportAdminEvent(param);
        return ResultVO.success(Boolean.TRUE);
    }

    @GetMapping("/api/system/behavior-analytics")
    @RequiresPermission("analytics:user-behavior:list")
    public ResultVO<PageInfo<UserBehaviorAnalyticsRowVO>> listAnalyticsRows(@ModelAttribute UserBehaviorAnalyticsQueryParam param) {
        return ResultVO.success(userBehaviorAnalyticsService.listAnalyticsRows(param));
    }
}
