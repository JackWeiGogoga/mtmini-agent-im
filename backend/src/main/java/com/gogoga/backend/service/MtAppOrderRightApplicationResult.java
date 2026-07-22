package com.gogoga.backend.service;

import java.util.Set;

/**
 * 单笔应用订单权益事务处理结果。
 */
public record MtAppOrderRightApplicationResult(
        boolean applied,
        Set<String> consumedBuyInfoCreditKeys
) {

    public MtAppOrderRightApplicationResult {
        consumedBuyInfoCreditKeys = consumedBuyInfoCreditKeys == null
                ? Set.of()
                : Set.copyOf(consumedBuyInfoCreditKeys);
    }
}
