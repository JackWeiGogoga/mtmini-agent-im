package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.mt.MtImBroadcastSettingHistory;
import com.gogoga.backend.mapper.MtImBroadcastRecordMapper;
import com.gogoga.backend.mapper.MtImBroadcastSettingHistoryMapper;
import com.gogoga.backend.mapper.MtImBroadcastTaskMapper;
import com.gogoga.backend.param.AdminImBroadcastQueryParam;
import com.gogoga.backend.vo.ImBroadcastRecordVO;
import com.gogoga.backend.vo.ImBroadcastSettingHistoryVO;
import com.gogoga.backend.vo.ImBroadcastTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理后台群发记录查询服务。
 */
@Service
@RequiredArgsConstructor
public class ImBroadcastQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MtImBroadcastTaskMapper taskMapper;
    private final MtImBroadcastRecordMapper recordMapper;
    private final MtImBroadcastSettingHistoryMapper historyMapper;
    private final MtImBroadcastSettingService settingService;

    public PageInfo<ImBroadcastTaskVO> listTasks(AdminImBroadcastQueryParam param) {
        AdminImBroadcastQueryParam query = param == null ? new AdminImBroadcastQueryParam() : param;
        PageHelper.startPage(normalizePageNum(query.getPageNum()), normalizePageSize(query.getPageSize()));
        return new PageInfo<>(taskMapper.selectAdminPage(query));
    }

    public PageInfo<ImBroadcastRecordVO> listRecords(Long taskId, String status, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(normalizePageNum(pageNum), normalizePageSize(pageSize));
        return new PageInfo<>(recordMapper.selectAdminPageByTask(taskId, status));
    }

    /**
     * 门店群发配置变更历史，含被删除的版本。
     */
    public PageInfo<ImBroadcastSettingHistoryVO> listSettingHistory(Long shopId, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(normalizePageNum(pageNum), normalizePageSize(pageSize));
        PageInfo<MtImBroadcastSettingHistory> page =
                new PageInfo<>(historyMapper.selectByShopId(shopId));
        List<ImBroadcastSettingHistoryVO> rows = page.getList() == null
                ? List.of()
                : page.getList().stream().map(this::toHistoryVO).collect(Collectors.toList());
        PageInfo<ImBroadcastSettingHistoryVO> result = new PageInfo<>();
        result.setList(rows);
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }

    private ImBroadcastSettingHistoryVO toHistoryVO(MtImBroadcastSettingHistory history) {
        ImBroadcastSettingHistoryVO vo = new ImBroadcastSettingHistoryVO();
        vo.setId(history.getId());
        vo.setShopId(history.getShopId());
        vo.setVersionNo(history.getVersionNo());
        vo.setChangeType(history.getChangeType());
        vo.setChangeTypeName(MtImBroadcastSettingService.CHANGE_TYPE_DELETE.equals(history.getChangeType())
                ? "清空配置" : "保存配置");
        vo.setChangeSource(history.getChangeSource());
        vo.setEnabled(history.getEnabled());
        vo.setWeekDays(settingService.parseWeekDays(history.getWeekDays()));
        vo.setRepeatMode(history.getRepeatMode());
        vo.setSendTime(history.getSendTime() == null ? null : history.getSendTime().format(TIME_FORMATTER));
        vo.setContent(history.getContent());
        vo.setOperatorUserId(history.getOperatorUserId());
        vo.setOperatorName(history.getOperatorName());
        vo.setCreateTime(history.getCreateTime());
        return vo;
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
