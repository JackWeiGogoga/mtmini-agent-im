package com.gogoga.backend.service;

import com.gogoga.backend.entity.mt.MtPowerAccount;
import com.gogoga.backend.exception.BusinessException;
import com.gogoga.backend.mapper.MtPowerAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 门店算力账户服务
 *
 * @author system
 * @since 2026-03-23
 */
@Service
@RequiredArgsConstructor
public class MtPowerAccountService {

    private static final int MAX_RETRY_TIMES = 3;

    private final MtPowerAccountMapper mtPowerAccountMapper;

    public MtPowerAccount getOrCreateAccount(Long shopId) {
        MtPowerAccount account = mtPowerAccountMapper.selectByShopId(shopId);
        return account == null ? buildZeroAccount(shopId) : account;
    }

    public MtPowerAccount ensureAccountInitialized(Long shopId) {
        LocalDateTime now = LocalDateTime.now();
        mtPowerAccountMapper.initAccountIfAbsent(shopId, now);
        MtPowerAccount account = mtPowerAccountMapper.selectByShopId(shopId);
        if (account == null) {
            throw new BusinessException(500, "初始化算力账户失败");
        }
        return account;
    }

    public MtPowerAccount getAccount(Long shopId) {
        return getOrCreateAccount(shopId);
    }

    public MtPowerAccount addPaidRecharge(Long shopId, Integer powerAmount) {
        return retryCredit(shopId, powerAmount, CreditMode.PAID_RECHARGE);
    }

    public MtPowerAccount addAdminGrant(Long shopId, Integer powerAmount) {
        return retryCredit(shopId, powerAmount, CreditMode.ADMIN_GRANT);
    }

    private MtPowerAccount retryCredit(Long shopId, Integer powerAmount, CreditMode creditMode) {
        ensureAccountInitialized(shopId);
        for (int i = 0; i < MAX_RETRY_TIMES; i++) {
            MtPowerAccount account = mtPowerAccountMapper.selectByShopId(shopId);
            if (account == null) {
                throw new BusinessException(500, "初始化算力账户失败");
            }
            int rows = creditMode == CreditMode.PAID_RECHARGE
                    ? mtPowerAccountMapper.addPaidRecharge(shopId, powerAmount, account.getVersion(), LocalDateTime.now())
                    : mtPowerAccountMapper.addAdminGrant(shopId, powerAmount, account.getVersion(), LocalDateTime.now());
            if (rows > 0) {
                return getOrCreateAccount(shopId);
            }
        }
        throw new BusinessException(409, "算力入账冲突，请稍后重试");
    }

    private enum CreditMode {
        PAID_RECHARGE,
        ADMIN_GRANT
    }

    private MtPowerAccount buildZeroAccount(Long shopId) {
        MtPowerAccount account = new MtPowerAccount();
        account.setShopId(shopId);
        account.setBalance(0);
        account.setRechargeBalance(0);
        account.setGrantBalance(0);
        account.setTotalRecharge(0);
        account.setTotalGrant(0);
        account.setTotalConsume(0);
        account.setVersion(0);
        return account;
    }
}
