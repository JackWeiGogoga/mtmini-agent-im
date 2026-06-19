package com.gogoga.backend.constant;

/**
 * 算力风险阈值。
 *
 * @author system
 */
public final class PowerThresholdConstant {

    /**
     * 算力余额严重不足阈值。
     */
    public static final int CRITICAL_POWER_BALANCE = 2000;

    /**
     * 算力余额提前提醒阈值。
     */
    public static final int WARNING_POWER_BALANCE = 20000;

    private PowerThresholdConstant() {
    }
}
