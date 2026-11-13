package com.gogoga.backend.controller;

import com.github.pagehelper.PageInfo;
import com.gogoga.backend.annotation.RequiresLogin;
import com.gogoga.backend.annotation.RequiresPermission;
import com.gogoga.backend.param.AdminImBroadcastQueryParam;
import com.gogoga.backend.service.ImBroadcastQueryService;
import com.gogoga.backend.vo.ImBroadcastRecordVO;
import com.gogoga.backend.vo.ImBroadcastTaskVO;
import com.gogoga.backend.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/im-broadcasts")
@RequiredArgsConstructor
@RequiresLogin
@RequiresPermission("system:im-broadcast:list")
public class AdminImBroadcastController {

    private final ImBroadcastQueryService queryService;

    @GetMapping("/tasks")
    public ResultVO<PageInfo<ImBroadcastTaskVO>> tasks(AdminImBroadcastQueryParam param) {
        return ResultVO.success(queryService.listTasks(param));
    }

    @GetMapping("/tasks/{taskId}/records")
    public ResultVO<PageInfo<ImBroadcastRecordVO>> records(
            @PathVariable Long taskId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ResultVO.success(queryService.listRecords(taskId, status, pageNum, pageSize));
    }
}
