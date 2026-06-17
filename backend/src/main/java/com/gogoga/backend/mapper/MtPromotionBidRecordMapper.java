package com.gogoga.backend.mapper;

import com.gogoga.backend.entity.mt.MtPromotionBidRecord;
import com.gogoga.backend.vo.AdminPricingAnalysisRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动调价执行记录 Mapper
 *
 * @author system
 * @since 2026-03-26
 */
@Mapper
public interface MtPromotionBidRecordMapper {

    /**
     * 根据主键查询
     *
     * @param id 主键 ID
     * @return 执行记录
     */
    MtPromotionBidRecord selectById(@Param("id") Long id);

    /**
     * 查询门店指定时段的执行记录
     *
     * @param shopId 店铺 ID
     * @param bizDate 业务日期
     * @param slotTime 时段时间
     * @return 执行记录
     */
    MtPromotionBidRecord selectByShopIdAndBizDateAndSlotTime(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate,
            @Param("slotTime") LocalDateTime slotTime
    );

    /**
     * 查询门店指定窗口最近成功执行记录
     *
     * @param shopId 店铺 ID
     * @param windowKey 窗口标识
     * @param limit 限制条数
     * @return 成功记录列表
     */
    List<MtPromotionBidRecord> selectLatestSuccessByShopIdAndWindowKey(
            @Param("shopId") Long shopId,
            @Param("windowKey") String windowKey,
            @Param("limit") int limit
    );

    /**
     * 查询门店最近执行记录
     *
     * @param shopId 店铺 ID
     * @param bizDateFrom 业务日期起点
     * @return 执行记录列表
     */
    List<MtPromotionBidRecord> selectRecentByShopId(
            @Param("shopId") Long shopId,
            @Param("bizDateFrom") LocalDate bizDateFrom
    );

    /**
     * 查询门店指定原因最近一条执行记录
     *
     * @param shopId 店铺 ID
     * @param decisionReason 决策原因
     * @return 最近记录
     */
    MtPromotionBidRecord selectLatestByShopIdAndDecisionReason(
            @Param("shopId") Long shopId,
            @Param("decisionReason") String decisionReason
    );

    /**
     * 查询门店指定业务日和原因最近一条执行记录
     *
     * @param shopId 店铺 ID
     * @param bizDate 业务日期
     * @param decisionReason 决策原因
     * @return 最近记录
     */
    MtPromotionBidRecord selectLatestByShopIdAndBizDateAndDecisionReason(
            @Param("shopId") Long shopId,
            @Param("bizDate") LocalDate bizDate,
            @Param("decisionReason") String decisionReason
    );

    /**
     * 查询门店最近一条成功执行记录
     *
     * @param shopId 店铺 ID
     * @return 最近成功记录
     */
    MtPromotionBidRecord selectLatestSuccessByShopId(@Param("shopId") Long shopId);

    /**
     * 批量查询门店最近一条成功执行记录
     *
     * @param shopIds 店铺 ID 列表
     * @return 最近成功记录列表
     */
    List<MtPromotionBidRecord> selectLatestSuccessByShopIds(@Param("shopIds") List<Long> shopIds);

    /**
     * 查询门店指定业务日期后的最近成功执行记录。
     *
     * @param shopId 店铺 ID
     * @param bizDateFrom 业务日期起点
     * @param limit 限制条数
     * @return 最近成功记录
     */
    List<MtPromotionBidRecord> selectLatestSuccessByShopIdSince(
            @Param("shopId") Long shopId,
            @Param("bizDateFrom") LocalDate bizDateFrom,
            @Param("limit") int limit
    );

    /**
     * 查询门店最近一条执行记录
     *
     * @param shopId 店铺 ID
     * @return 最近执行记录
     */
    MtPromotionBidRecord selectLatestByShopId(@Param("shopId") Long shopId);

    /**
     * 批量查询门店最近一条执行记录
     *
     * @param shopIds 店铺 ID 列表
     * @return 最近执行记录列表
     */
    List<MtPromotionBidRecord> selectLatestByShopIds(@Param("shopIds") List<Long> shopIds);

    Long selectLatestVisibleLogIdByShopId(@Param("shopId") Long shopId);

    int countVisibleLogsAfterId(
            @Param("shopId") Long shopId,
            @Param("recordId") Long recordId
    );

    /**
     * 查询门店在指定槽位前最近一条带有花费计数器的执行记录
     *
     * @param shopId 店铺 ID
     * @param slotTime 当前槽位
     * @return 最近记录
     */
    MtPromotionBidRecord selectLatestWithCostCounterByShopIdBeforeSlotTime(
            @Param("shopId") Long shopId,
            @Param("slotTime") LocalDateTime slotTime
    );

    /**
     * 汇总指定日期范围内成功执行记录的窗口花费
     *
     * @param shopId 店铺 ID
     * @param bizDateFrom 开始日期（含）
     * @param bizDateToExclusive 结束日期（不含）
     * @return 推广花费总额
     */
    java.math.BigDecimal sumLastSuccessTotalCostByShopIdAndBizDateBetween(
            @Param("shopId") Long shopId,
            @Param("bizDateFrom") LocalDate bizDateFrom,
            @Param("bizDateToExclusive") LocalDate bizDateToExclusive
    );

    List<AdminPricingAnalysisRowVO> selectAdminAnalysisRows(
            @Param("shopId") Long shopId,
            @Param("poiId") String poiId,
            @Param("shopName") String shopName,
            @Param("keyword") String keyword,
            @Param("executeStatus") String executeStatus,
            @Param("decisionAction") String decisionAction,
            @Param("decisionReason") String decisionReason,
            @Param("logMarkdownStatus") String logMarkdownStatus,
            @Param("slotStartTime") LocalDateTime slotStartTime,
            @Param("slotEndTime") LocalDateTime slotEndTime,
            @Param("scopeType") String scopeType,
            @Param("currentUserId") String currentUserId,
            @Param("visibleUserIds") List<String> visibleUserIds
    );

    /**
     * 更新 Markdown 日志和决策详情
     *
     * @param id 主键 ID
     * @param markdownMessage Markdown 日志
     * @param decisionDetailJson 决策详情 JSON
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int updateMarkdownAndDecisionDetail(
            @Param("id") Long id,
            @Param("markdownMessage") String markdownMessage,
            @Param("logMarkdownStatus") String logMarkdownStatus,
            @Param("decisionDetailJson") String decisionDetailJson,
            @Param("updateTime") LocalDateTime updateTime
    );

    /**
     * 更新日志异步任务元数据
     *
     * @param id 主键 ID
     * @param logTaskId 日志任务 ID
     * @param logMarkdownStatus 日志状态
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int updateLogTaskMetadata(
            @Param("id") Long id,
            @Param("logTaskId") Long logTaskId,
            @Param("logMarkdownStatus") String logMarkdownStatus,
            @Param("updateTime") LocalDateTime updateTime
    );

    /**
     * 新增执行记录
     *
     * @param record 执行记录
     * @return 影响行数
     */
    int insert(MtPromotionBidRecord record);
}
