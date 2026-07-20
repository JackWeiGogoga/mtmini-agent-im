package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.mapper.MtImBroadcastRecordMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
import com.gogoga.backend.param.AdminImBroadcastQueryParam;
import com.gogoga.backend.vo.ImBroadcastRecordVO;
import com.gogoga.backend.vo.ImBroadcastTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 管理后台群发记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class ImBroadcastQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MtImBroadcastTaskMapper taskMapper;
    private final MtImBroadcastRecordMapper recordMapper;

    public PageInfo<ImBroadcastTaskVO> listTasks(AdminImBroadcastQueryParam param) {
        AdminImBroadcastQueryParam query = param == null ? new AdminImBroadcastQueryParam() : param;
        PageHelper.startPage(normalizePageNum(query.getPageNum()), normalizePageSize(query.getPageSize()));
        return new PageInfo<>(taskMapper.selectAdminPage(query));
    }

    public PageInfo<ImBroadcastRecordVO> listRecords(Long taskId, String status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(normalizePageNum(pageNum), normalizePageSize(pageSize));
        return new PageInfo<>(recordMapper.selectAdminPageByTask(taskId, status));
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
