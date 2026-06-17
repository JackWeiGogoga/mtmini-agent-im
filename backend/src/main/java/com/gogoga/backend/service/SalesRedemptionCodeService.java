package com.gogoga.backend.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gogoga.backend.entity.Role;
import com.gogoga.backend.entity.SalesRedemptionCode;
import com.gogoga.backend.entity.SalesRedemptionUseRecord;
import com.gogoga.backend.entity.SalesUserProfile;
import com.gogoga.backend.entity.User;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.RoleMapper;
import com.gogoga.backend.mapper.SalesRedemptionCodeMapper;
import com.gogoga.backend.mapper.SalesRedemptionUseRecordMapper;
import com.gogoga.backend.mapper.SalesUserProfileMapper;
import com.gogoga.backend.mapper.UserMapper;
import com.gogoga.backend.util.PhoneUtil;
import com.gogoga.backend.util.ExcelExportUtil;
import com.gogoga.backend.util.UserContext;
import com.gogoga.backend.vo.AdminSalesRedemptionCodeRowVO;
import com.gogoga.backend.vo.SalesSelfConfigVO;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesRedemptionCodeService {

    private static final String SALES_ROLE_ID = "ROLE_SALES";
    private static final String REDEMPTION_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String RECEIPT_CODE_STORAGE_PROVIDER_COS = "TENCENT_COS";

    private final SalesRedemptionCodeMapper salesRedemptionCodeMapper;
    private final SalesRedemptionUseRecordMapper salesRedemptionUseRecordMapper;
    private final SalesUserProfileMapper salesUserProfileMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final CosStorageService cosStorageService;
    private final SalesReceiptCodePreviewService salesReceiptCodePreviewService;

    public PageInfo<AdminSalesRedemptionCodeRowVO> listAdminRows(String keyword, Integer pageNum, Integer pageSize) {
        requireSuperAdmin();
        int normalizedPageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int normalizedPageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(normalizedPageNum, normalizedPageSize);
        PageInfo<AdminSalesRedemptionCodeRowVO> pageInfo = new PageInfo<>(
                salesRedemptionCodeMapper.selectAdminList(StringUtils.trimToNull(keyword))
        );
        pageInfo.getList().forEach(this::fillReceiptCodeAccessUrl);
        return pageInfo;
    }

    public byte[] exportAdminXlsx(String keyword) {
        requireSuperAdmin();
        PageHelper.startPage(1, ExcelExportUtil.MAX_EXPORT_ROWS + 1, false);
        List<AdminSalesRedemptionCodeRowVO> rows;
        try {
            rows = salesRedemptionCodeMapper.selectAdminList(StringUtils.trimToNull(keyword));
        } finally {
            PageHelper.clearPage();
        }
        return ExcelExportUtil.toXlsx("销售兑换码", List.of(
                ExcelExportUtil.column("兑换码", AdminSalesRedemptionCodeRowVO::getCode),
                ExcelExportUtil.column("网销用户", row -> StringUtils.firstNonBlank(row.getSalesUserName(), row.getSalesUserId())),
                ExcelExportUtil.column("手机号", AdminSalesRedemptionCodeRowVO::getSalesUserPhone),
                ExcelExportUtil.column("收款码", row -> StringUtils.isBlank(row.getReceiptCodeUrl()) && StringUtils.isBlank(row.getReceiptCodeObjectKey()) ? "未上传" : "已上传"),
                ExcelExportUtil.column("生成时间", row -> ExcelExportUtil.dateTime(row.getCreateTime()))
        ), rows);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminSalesRedemptionCodeRowVO generateByPhone(String phone) {
        requireSuperAdmin();
        String normalizedPhone = normalizePhone(phone);
        User user = userMapper.selectByPhone(normalizedPhone);
        if (user == null) {
            throw new BusinessException("手机号对应的后台用户不存在");
        }
        if (!isSalesUser(user.getUserId())) {
            throw new BusinessException("该用户不是网销用户");
        }
        if (salesRedemptionCodeMapper.selectBySalesUserId(user.getUserId()) != null) {
            throw new BusinessException("该网销用户已存在兑换码");
        }

        LocalDateTime now = LocalDateTime.now();
        SalesRedemptionCode code = new SalesRedemptionCode();
        code.setCode(generateUniqueCode());
        code.setSalesUserId(user.getUserId());
        code.setSalesUserPhone(normalizedPhone);
        code.setStatus(1);
        code.setCreatedByUserId(requireCurrentUserId());
        code.setCreateTime(now);
        code.setUpdateTime(now);
        salesRedemptionCodeMapper.insert(code);

        AdminSalesRedemptionCodeRowVO row = new AdminSalesRedemptionCodeRowVO();
        row.setId(code.getId());
        row.setCode(code.getCode());
        row.setSalesUserId(code.getSalesUserId());
        row.setSalesUserName(StringUtils.firstNonBlank(user.getNickname(), user.getUsername(), user.getUserId()));
        row.setSalesUserPhone(code.getSalesUserPhone());
        row.setCreateTime(code.getCreateTime());
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearReceiptCode(Long redemptionCodeId) {
        requireSuperAdmin();
        if (redemptionCodeId == null) {
            throw new BusinessException("兑换码记录不能为空");
        }
        SalesRedemptionCode code = salesRedemptionCodeMapper.selectById(redemptionCodeId);
        if (code == null) {
            throw new BusinessException("兑换码记录不存在");
        }
        SalesUserProfile profile = salesUserProfileMapper.selectByUserId(code.getSalesUserId());
        if (profile != null) {
            cosStorageService.deleteReceiptCodeObject(profile.getReceiptCodeObjectKey());
        }
        salesUserProfileMapper.clearReceiptCodeUrl(code.getSalesUserId());
    }

    public SalesSelfConfigVO getCurrentSalesConfig() {
        String currentUserId = requireCurrentUserId();
        User user = userMapper.selectByUserId(currentUserId);
        SalesRedemptionCode code = salesRedemptionCodeMapper.selectBySalesUserId(currentUserId);
        SalesUserProfile profile = salesUserProfileMapper.selectByUserId(currentUserId);

        SalesSelfConfigVO vo = new SalesSelfConfigVO();
        vo.setSalesUserId(currentUserId);
        vo.setSalesUserName(user == null ? currentUserId : StringUtils.firstNonBlank(user.getNickname(), user.getUsername(), user.getUserId()));
        vo.setSalesUserPhone(user == null ? null : user.getPhone());
        vo.setRedemptionCode(code == null ? null : code.getCode());
        vo.setReceiptCodeUrl(resolveReceiptCodeAccessUrl(profile));
        vo.setReceiptCodeUploadAllowed(profile == null || !hasUploadedReceiptCode(profile));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public SalesSelfConfigVO uploadReceiptCode(MultipartFile file) {
        String currentUserId = requireCurrentUserId();
        SalesUserProfile profile = salesUserProfileMapper.selectByUserId(currentUserId);
        if (profile != null && hasUploadedReceiptCode(profile)) {
            throw new BusinessException("收款码已上传，等待超管删除后才能重新上传");
        }
        CosStorageService.StoredObject storedObject = cosStorageService.uploadReceiptCode(currentUserId, file);
        LocalDateTime now = LocalDateTime.now();
        try {
            if (profile == null) {
                profile = new SalesUserProfile();
                profile.setUserId(currentUserId);
                profile.setCreateTime(now);
            }
            profile.setReceiptCodeUrl(StringUtils.trimToNull(storedObject.getAccessUrl()));
            profile.setReceiptCodeStorageProvider(RECEIPT_CODE_STORAGE_PROVIDER_COS);
            profile.setReceiptCodeObjectKey(storedObject.getObjectKey());
            profile.setReceiptCodeUploadedAt(now);
            profile.setUpdateTime(now);
            if (profile.getId() == null) {
                salesUserProfileMapper.insert(profile);
            } else {
                salesUserProfileMapper.updateById(profile);
            }
        } catch (RuntimeException ex) {
            cosStorageService.deleteReceiptCodeObject(storedObject.getObjectKey());
            throw ex;
        }
        return getCurrentSalesConfig();
    }

    public ResolvedRedemptionCode requireResolvedCode(String rawCode) {
        String normalizedCode = normalizeCode(rawCode);
        SalesRedemptionCode code = salesRedemptionCodeMapper.selectByCode(normalizedCode);
        if (code == null || code.getStatus() == null || code.getStatus() != 1) {
            throw new BusinessException("兑换码无效");
        }
        return ResolvedRedemptionCode.builder()
                .id(code.getId())
                .code(code.getCode())
                .salesUserId(code.getSalesUserId())
                .salesUserPhone(code.getSalesUserPhone())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordRechargeUse(ResolvedRedemptionCode resolvedCode,
                                  Long rechargeId,
                                  String rechargeNo,
                                  Long shopId,
                                  String poiId,
                                  String shopName) {
        if (resolvedCode == null || rechargeId == null || StringUtils.isBlank(rechargeNo)) {
            return;
        }
        if (salesRedemptionUseRecordMapper.selectByRechargeNo(rechargeNo) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SalesRedemptionUseRecord record = new SalesRedemptionUseRecord();
        record.setRechargeId(rechargeId);
        record.setRechargeNo(rechargeNo);
        record.setShopId(shopId);
        record.setPoiId(poiId);
        record.setShopName(shopName);
        record.setSalesUserId(resolvedCode.getSalesUserId());
        record.setSalesUserPhone(resolvedCode.getSalesUserPhone());
        record.setRedemptionCodeId(resolvedCode.getId());
        record.setRedemptionCode(resolvedCode.getCode());
        record.setCreateTime(now);
        record.setUpdateTime(now);
        salesRedemptionUseRecordMapper.insert(record);
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            String code = RandomStringUtils.secure().next(6, REDEMPTION_CODE_ALPHABET);
            if (salesRedemptionCodeMapper.selectByCode(code) == null) {
                return code;
            }
        }
        throw new BusinessException("兑换码生成失败，请重试");
    }

    private boolean isSalesUser(String userId) {
        List<Role> roles = roleMapper.selectByUserId(userId);
        return roles.stream().anyMatch(role -> StringUtils.equals(role.getRoleId(), SALES_ROLE_ID));
    }

    private void requireSuperAdmin() {
        if (!UserContext.isSuperAdmin()) {
            throw new BusinessException(403, "无权执行该操作");
        }
    }

    private String requireCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new BusinessException("当前用户未登录");
        }
        return userId;
    }

    private String normalizePhone(String phone) {
        String normalizedPhone = PhoneUtil.normalizeCnPhone(phone);
        if (!PhoneUtil.isValidCnPhone(normalizedPhone)) {
            throw new BusinessException("手机号格式不正确");
        }
        return normalizedPhone;
    }

    private String normalizeCode(String code) {
        String normalizedCode = StringUtils.upperCase(StringUtils.trimToNull(code));
        if (StringUtils.isBlank(normalizedCode)) {
            throw new BusinessException("兑换码不能为空");
        }
        return normalizedCode;
    }

    private String resolveReceiptCodeAccessUrl(SalesUserProfile profile) {
        if (profile == null) {
            return null;
        }
        return salesReceiptCodePreviewService.buildPreviewUrl(profile.getUserId(), profile);
    }

    private void fillReceiptCodeAccessUrl(AdminSalesRedemptionCodeRowVO row) {
        SalesUserProfile profile = new SalesUserProfile();
        profile.setUserId(row.getSalesUserId());
        profile.setReceiptCodeObjectKey(row.getReceiptCodeObjectKey());
        profile.setReceiptCodeUrl(row.getReceiptCodeUrl());
        row.setReceiptCodeUrl(salesReceiptCodePreviewService.buildPreviewUrl(row.getSalesUserId(), profile));
    }

    private boolean hasUploadedReceiptCode(SalesUserProfile profile) {
        return profile != null && (
                StringUtils.isNotBlank(profile.getReceiptCodeUrl())
                        || StringUtils.isNotBlank(profile.getReceiptCodeObjectKey())
        );
    }

    @Data
    @Builder
    public static class ResolvedRedemptionCode {

        private Long id;

        private String code;

        private String salesUserId;

        private String salesUserPhone;
    }
}
