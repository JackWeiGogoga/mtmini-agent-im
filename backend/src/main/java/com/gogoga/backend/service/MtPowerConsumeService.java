package com.gogoga.backend.service;

import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.dto.PowerConsumeResultDTO;
import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.entity.mt.MtPowerConsume;
import com.gogoga.backend.entity.mt.MtPowerConsumeDetail;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerAccountMapper;
import com.gogoga.backend.mapper.MtPowerConsumeDetailMapper;
import com.gogoga.backend.mapper.MtPowerConsumeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 门店算力消费服务
 *
 * @author system
 * @since 2026-03-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MtPowerConsumeService {

    public static final String SOURCE_BALANCE_GRANT = "GRANT";
    public static final String SOURCE_BALANCE_RECHARGE = "RECHARGE";
    private static final int MAX_RETRY_TIMES = 3;

    private final MtPowerAccountService mtPowerAccountService;
    private final MtPowerAccountMapper mtPowerAccountMapper;
    private final MtPowerConsumeMapper mtPowerConsumeMapper;
    private final MtPowerConsumeDetailMapper mtPowerConsumeDetailMapper;

    /**
     * 执行算力消费
     *
     * @param request 消费请求
     * @return 消费结果
     */
    @Transactional(rollbackFor = Exception.class)
    public PowerConsumeResultDTO consume(PowerConsumeRequestDTO request) {
        validateRequest(request);

        MtPowerConsume existing = mtPowerConsumeMapper.selectByShopIdAndRequestNo(request.getShopId(), request.getRequestNo());
        if (existing != null) {
            return buildDuplicateResult(existing);
        }

        for (int i = 0; i < MAX_RETRY_TIMES; i++) {
            MtPowerAccount account = mtPowerAccountService.getOrCreateAccount(request.getShopId());
            if (defaultInt(account.getBalance()) < request.getPowerAmount()) {
                throw new BusinessException(400, "当前算力余额不足");
            }

            DeductionSplit split = DeductionSplit.of(account, request.getPowerAmount());
            int rows = mtPowerAccountMapper.deductBySource(
                    request.getShopId(),
                    request.getPowerAmount(),
                    split.getGrantAmount(),
                    split.getRechargeAmount(),
                    account.getVersion(),
                    LocalDateTime.now()
            );
            if (rows <= 0) {
                continue;
            }

            MtPowerConsume consume = buildConsume(request, defaultInt(account.getBalance()), defaultInt(account.getBalance()) - request.getPowerAmount());
            try {
                mtPowerConsumeMapper.insert(consume);
                mtPowerConsumeDetailMapper.insertBatch(buildDetails(consume, account, split));
            } catch (DuplicateKeyException e) {
                log.info("算力消费请求重复，shopId={}, requestNo={}", request.getShopId(), request.getRequestNo());
                MtPowerConsume duplicate = mtPowerConsumeMapper.selectByShopIdAndRequestNo(request.getShopId(), request.getRequestNo());
                if (duplicate == null) {
                    throw e;
                }
                return buildDuplicateResult(duplicate);
            }

            PowerConsumeResultDTO result = new PowerConsumeResultDTO();
            result.setDuplicate(false);
            result.setConsumeNo(consume.getConsumeNo());
            result.setBalanceAfter(consume.getBalanceAfter());
            return result;
        }

        throw new BusinessException(409, "算力消费处理冲突，请稍后重试");
    }

    private List<MtPowerConsumeDetail> buildDetails(MtPowerConsume consume, MtPowerAccount account, DeductionSplit split) {
        LocalDateTime now = LocalDateTime.now();
        List<MtPowerConsumeDetail> details = new ArrayList<>();
        if (split.getGrantAmount() > 0) {
            MtPowerConsumeDetail detail = new MtPowerConsumeDetail();
            detail.setConsumeId(consume.getId());
            detail.setConsumeNo(consume.getConsumeNo());
            detail.setShopId(consume.getShopId());
            detail.setSourceBalanceType(SOURCE_BALANCE_GRANT);
            detail.setPowerAmount(split.getGrantAmount());
            detail.setBalanceBefore(defaultInt(account.getGrantBalance()));
            detail.setBalanceAfter(defaultInt(account.getGrantBalance()) - split.getGrantAmount());
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            details.add(detail);
        }
        if (split.getRechargeAmount() > 0) {
            MtPowerConsumeDetail detail = new MtPowerConsumeDetail();
            detail.setConsumeId(consume.getId());
            detail.setConsumeNo(consume.getConsumeNo());
            detail.setShopId(consume.getShopId());
            detail.setSourceBalanceType(SOURCE_BALANCE_RECHARGE);
            detail.setPowerAmount(split.getRechargeAmount());
            detail.setBalanceBefore(defaultInt(account.getRechargeBalance()));
            detail.setBalanceAfter(defaultInt(account.getRechargeBalance()) - split.getRechargeAmount());
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            details.add(detail);
        }
        return details;
    }

    private PowerConsumeResultDTO buildDuplicateResult(MtPowerConsume existing) {
        PowerConsumeResultDTO result = new PowerConsumeResultDTO();
        result.setDuplicate(true);
        result.setConsumeNo(existing.getConsumeNo());
        result.setBalanceAfter(existing.getBalanceAfter());
        return result;
    }

    private void validateRequest(PowerConsumeRequestDTO request) {
        if (request == null || request.getShopId() == null) {
            throw new BusinessException(400, "店铺信息不能为空");
        }
        if (StringUtils.isBlank(request.getSourceType()) || StringUtils.isBlank(request.getSourceId())) {
            throw new BusinessException(400, "消费来源不能为空");
        }
        if (StringUtils.isBlank(request.getRequestNo())) {
            throw new BusinessException(400, "请求号不能为空");
        }
        if (request.getPowerAmount() == null || request.getPowerAmount() <= 0) {
            throw new BusinessException(400, "消费算力必须大于0");
        }
    }

    private MtPowerConsume buildConsume(PowerConsumeRequestDTO request, Integer balanceBefore, Integer balanceAfter) {
        LocalDateTime now = LocalDateTime.now();
        MtPowerConsume consume = new MtPowerConsume();
        consume.setConsumeNo("PC" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        consume.setShopId(request.getShopId());
        consume.setSourceType(request.getSourceType());
        consume.setSourceId(request.getSourceId());
        consume.setRequestNo(request.getRequestNo());
        consume.setPowerAmount(request.getPowerAmount());
        consume.setBalanceBefore(balanceBefore);
        consume.setBalanceAfter(balanceAfter);
        consume.setRemark(request.getRemark());
        consume.setCreateTime(now);
        consume.setUpdateTime(now);
        return consume;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static class DeductionSplit {

        private final int grantAmount;
        private final int rechargeAmount;

        private DeductionSplit(int grantAmount, int rechargeAmount) {
            this.grantAmount = grantAmount;
            this.rechargeAmount = rechargeAmount;
        }

        static DeductionSplit of(MtPowerAccount account, int totalAmount) {
            int grantBalance = account == null || account.getGrantBalance() == null ? 0 : account.getGrantBalance();
            int grantAmount = Math.min(grantBalance, totalAmount);
            int rechargeAmount = totalAmount - grantAmount;
            return new DeductionSplit(grantAmount, rechargeAmount);
        }

        int getGrantAmount() {
            return grantAmount;
        }

        int getRechargeAmount() {
            return rechargeAmount;
        }
    }
}
