package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.config.YouzanProperties;
import com.gogoga.backend.constant.YouzanConstant;
import com.gogoga.backend.entity.LoginUser;
import com.gogoga.backend.entity.mt.MtShop;
import com.gogoga.backend.entity.mt.MtShopRight;
import com.gogoga.backend.entity.youzan.YzAppOrder;
import com.gogoga.backend.entity.youzan.YzOrderRightUseRecord;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtShopRightMapper;
import com.gogoga.backend.mapper.YzAppOrderMapper;
import com.gogoga.backend.mapper.YzOrderRightUseRecordMapper;
import com.gogoga.backend.param.AdminYouzanAppOrderQueryParam;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminYouzanAppOrderDetailVO;
import com.gogoga.backend.vo.AdminYouzanAppOrderRowVO;
import com.gogoga.backend.vo.AdminYouzanOrderRedeemVO;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台有赞订购订单服务
 */
@Service
@RequiredArgsConstructor
public class AdminYouzanAppOrderService {

    private static final int RIGHT_TYPE_DURATION = 2;

    private final YzAppOrderMapper yzAppOrderMapper;
    private final YzOrderRightUseRecordMapper yzOrderRightUseRecordMapper;
    private final MtShopService mtShopService;
    private final MtShopRightMapper mtShopRightMapper;
    private final YouzanProperties youzanProperties;

    public PageInfo<AdminYouzanAppOrderRowVO> listRows(AdminYouzanAppOrderQueryParam param) {
        AdminYouzanAppOrderQueryParam query = prepareQuery(param);
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
        PageHelper.startPage(pageNum, pageSize);
        return new PageInfo<>(selectRows(query));
    }

    public byte[] exportXlsx(AdminYouzanAppOrderQueryParam param) {
        AdminYouzanAppOrderQueryParam query = prepareQuery(param);
        PageHelper.startPage(1, ExcelExportUtil.MAX_EXPORT_ROWS + 1, false);
        List<AdminYouzanAppOrderRowVO> rows;
        try {
            rows = selectRows(query);
        } finally {
            PageHelper.clearPage();
        }
        return ExcelExportUtil.toXlsx("有赞应用订单", List.of(
                ExcelExportUtil.column("有赞订单号", AdminYouzanAppOrderRowVO::getOrderNo),
                ExcelExportUtil.column("有赞店铺", AdminYouzanAppOrderRowVO::getShopName),
                ExcelExportUtil.column("kdtId", AdminYouzanAppOrderRowVO::getKdtId),
                ExcelExportUtil.column("应用ID", AdminYouzanAppOrderRowVO::getAppId),
                ExcelExportUtil.column("购买人手机号", AdminYouzanAppOrderRowVO::getBuyerPhone),
                ExcelExportUtil.column("购买人标识", row -> StringUtils.firstNonBlank(row.getOpenId(), row.getBuyerId() == null ? null : String.valueOf(row.getBuyerId()))),
                ExcelExportUtil.column("套餐版本", AdminYouzanAppOrderRowVO::getSkuVersionText),
                ExcelExportUtil.column("套餐天数", AdminYouzanAppOrderRowVO::getSkuIntervalText),
                ExcelExportUtil.column("支付金额", row -> ExcelExportUtil.moneyFen(row.getPrice())),
                ExcelExportUtil.column("支付状态", row -> resolvePayStatusDesc(row.getStatus())),
                ExcelExportUtil.column("支付时间", row -> ExcelExportUtil.dateTime(row.getPayTime())),
                ExcelExportUtil.column("核销状态", row -> resolveUseStatusDesc(row.getUseStatus())),
                ExcelExportUtil.column("核销门店", row -> StringUtils.firstNonBlank(row.getUsedMtShopName(), row.getUsedMtShopId() == null ? null : String.valueOf(row.getUsedMtShopId()))),
                ExcelExportUtil.column("核销权益", AdminYouzanAppOrderRowVO::getUsedRightCode),
                ExcelExportUtil.column("核销时间", row -> ExcelExportUtil.dateTime(row.getUsedAt())),
                ExcelExportUtil.column("核销人", AdminYouzanAppOrderRowVO::getUsedByUsername),
                ExcelExportUtil.column("核销备注", AdminYouzanAppOrderRowVO::getUseRemark),
                ExcelExportUtil.column("创建时间", row -> ExcelExportUtil.dateTime(row.getCreateTime()))
        ), rows);
    }

    private AdminYouzanAppOrderQueryParam prepareQuery(AdminYouzanAppOrderQueryParam param) {
        AdminYouzanAppOrderQueryParam query = param == null ? new AdminYouzanAppOrderQueryParam() : param;
        if (query.getPayStartTime() != null
                && query.getPayEndTime() != null
                && query.getPayStartTime().isAfter(query.getPayEndTime())) {
            throw new BusinessException("支付开始时间不能晚于结束时间");
        }
        return query;
    }

    private List<AdminYouzanAppOrderRowVO> selectRows(AdminYouzanAppOrderQueryParam query) {
        return yzAppOrderMapper.selectAdminPage(
                StringUtils.trimToNull(query.getOrderNo()),
                query.getKdtId(),
                StringUtils.trimToNull(query.getShopName()),
                StringUtils.trimToNull(query.getBuyerKeyword()),
                StringUtils.trimToNull(query.getSkuKeyword()),
                query.getStatus(),
                StringUtils.trimToNull(query.getUseStatus()),
                query.getPayStartTime(),
                query.getPayEndTime()
        );
    }

    public AdminYouzanAppOrderDetailVO getDetail(Long id) {
        if (id == null) {
            throw new BusinessException("订单ID不能为空");
        }
        YzAppOrder order = yzAppOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("有赞订单不存在");
        }
        return toDetailVO(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminYouzanOrderRedeemVO redeem(Long orderId, Long mtShopId, String remark) {
        requireYouzanOperator();
        if (orderId == null) {
            throw new BusinessException("有赞订单ID不能为空");
        }
        if (mtShopId == null) {
            throw new BusinessException("美团门店ID不能为空");
        }
        YzAppOrder order = yzAppOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("有赞订单不存在");
        }
        if (!Integer.valueOf(YouzanConstant.ORDER_STATUS_PAID).equals(order.getStatus())) {
            throw new BusinessException("仅支付成功的有赞订单可核销");
        }
        if (StringUtils.equals(order.getUseStatus(), YouzanConstant.ORDER_USE_STATUS_USED)
                || yzOrderRightUseRecordMapper.selectSuccessByOrderId(order.getId()) != null) {
            throw new BusinessException("该有赞订单已核销");
        }
        int intervalDays = parseIntervalDays(order.getSkuIntervalText());
        MtShop shop = mtShopService.findById(mtShopId);
        if (shop == null) {
            throw new BusinessException("美团门店不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        String rightCode = youzanProperties.getRight().getPromotionServiceCode();
        MtShopRight current = mtShopRightMapper.selectByShopIdAndServiceCode(mtShopId, rightCode);
        LocalDateTime previousExpireAt = current == null ? null : current.getExpireAt();
        LocalDateTime newExpireAt = calculateNewExpireAt(now, intervalDays, previousExpireAt);

        if (current == null) {
            current = new MtShopRight();
            current.setShopId(mtShopId);
            current.setServiceCode(rightCode);
            current.setType(RIGHT_TYPE_DURATION);
            current.setCreateTime(now);
        }
        current.setExpireAt(newExpireAt);
        current.setRemainingTimes(null);
        current.setSourceVersion("YOUZAN");
        current.setLastOrderId(order.getOrderNo());
        current.setUpdateTime(now);
        if (current.getId() == null) {
            mtShopRightMapper.insert(current);
        } else {
            mtShopRightMapper.updateById(current);
        }

        YzOrderRightUseRecord record = new YzOrderRightUseRecord();
        record.setYzAppOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setMtShopId(mtShopId);
        record.setRightCode(rightCode);
        record.setIntervalDays(intervalDays);
        record.setPreviousExpireAt(previousExpireAt);
        record.setNewExpireAt(newExpireAt);
        record.setOperatorUserId(UserContext.getUserId());
        record.setOperatorUsername(UserContext.getUserName());
        record.setRemark(StringUtils.trimToNull(remark));
        record.setStatus(YouzanConstant.USE_RECORD_STATUS_SUCCESS);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        yzOrderRightUseRecordMapper.insert(record);

        order.setUseStatus(YouzanConstant.ORDER_USE_STATUS_USED);
        order.setUsedMtShopId(mtShopId);
        order.setUsedRightCode(rightCode);
        order.setUsedAt(now);
        order.setUsedByUserId(UserContext.getUserId());
        order.setUsedByUsername(UserContext.getUserName());
        order.setUseRemark(StringUtils.trimToNull(remark));
        order.setUpdateTime(now);
        yzAppOrderMapper.updateUseStatus(order);

        AdminYouzanOrderRedeemVO vo = new AdminYouzanOrderRedeemVO();
        vo.setRecordId(record.getId());
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setMtShopId(mtShopId);
        vo.setMtShopName(StringUtils.defaultIfBlank(shop.getPoiName(), shop.getName()));
        vo.setRightCode(rightCode);
        vo.setIntervalDays(intervalDays);
        vo.setPreviousExpireAt(previousExpireAt);
        vo.setNewExpireAt(newExpireAt);
        vo.setUsedAt(now);
        return vo;
    }

    private LocalDateTime calculateNewExpireAt(LocalDateTime now, int intervalDays, LocalDateTime previousExpireAt) {
        LocalDateTime base = now.plusDays(intervalDays);
        if (previousExpireAt != null && previousExpireAt.isAfter(now)) {
            return base.plusSeconds(Duration.between(now, previousExpireAt).getSeconds());
        }
        return base;
    }

    private int parseIntervalDays(String skuIntervalText) {
        if (StringUtils.isBlank(skuIntervalText)) {
            throw new BusinessException("有赞订单套餐有效期为空，不能核销");
        }
        try {
            int days = Integer.parseInt(skuIntervalText.trim());
            if (days <= 0) {
                throw new BusinessException("有赞订单套餐有效期必须大于0天");
            }
            return days;
        } catch (NumberFormatException e) {
            throw new BusinessException("有赞订单套餐有效期不是有效天数");
        }
    }

    private void requireYouzanOperator() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null) {
            throw new BusinessException(401, "当前用户未登录");
        }
        if (loginUser.isSuperAdmin()
                || (loginUser.getRoles() != null && loginUser.getRoles().contains("youzan_operator"))) {
            return;
        }
        throw new BusinessException(403, "仅超级管理员或有赞运营可执行该操作");
    }

    private String resolvePayStatusDesc(Integer status) {
        if (status == null) {
            return "";
        }
        if (status == YouzanConstant.ORDER_STATUS_PAID) {
            return "支付成功";
        }
        return String.valueOf(status);
    }

    private String resolveUseStatusDesc(String useStatus) {
        if (StringUtils.equals(useStatus, YouzanConstant.ORDER_USE_STATUS_USED)) {
            return "已核销";
        }
        if (StringUtils.equals(useStatus, YouzanConstant.ORDER_USE_STATUS_UNUSED)) {
            return "未核销";
        }
        return StringUtils.defaultString(useStatus);
    }

    private AdminYouzanAppOrderDetailVO toDetailVO(YzAppOrder order) {
        AdminYouzanAppOrderDetailVO vo = new AdminYouzanAppOrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setYzShopId(order.getYzShopId());
        vo.setKdtId(order.getKdtId());
        vo.setRootKdtId(order.getRootKdtId());
        vo.setAppId(order.getAppId());
        vo.setBuyerId(order.getBuyerId());
        vo.setBuyerPhone(order.getBuyerPhone());
        vo.setOpenId(order.getOpenId());
        vo.setShopName(order.getShopName());
        vo.setShopDisplayNo(order.getShopDisplayNo());
        vo.setShopType(order.getShopType());
        vo.setShopRole(order.getShopRole());
        vo.setSkuVersionText(order.getSkuVersionText());
        vo.setSkuIntervalText(order.getSkuIntervalText());
        vo.setPrice(order.getPrice());
        vo.setStatus(order.getStatus());
        vo.setPayTime(order.getPayTimeAt());
        vo.setEnv(order.getEnv());
        vo.setUseStatus(order.getUseStatus());
        vo.setUsedMtShopId(order.getUsedMtShopId());
        vo.setUsedRightCode(order.getUsedRightCode());
        vo.setUsedAt(order.getUsedAt());
        vo.setUsedByUserId(order.getUsedByUserId());
        vo.setUsedByUsername(order.getUsedByUsername());
        vo.setUseRemark(order.getUseRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());
        return vo;
    }
}
