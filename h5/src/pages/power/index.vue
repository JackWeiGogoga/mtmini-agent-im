<template>
  <view class="page">
    <app-subpage-header title="算力" />
    <app-top-notice
      :offer="summary.firstRechargeOffer"
      class="power-top-notice"
    />

    <view class="content">
      <view class="summary-card">
        <view class="summary-head">
          <text class="summary-label">当前算力余额</text>
          <text class="summary-refresh" @tap="refreshSummary">刷新</text>
        </view>
        <view class="summary-main">
          <text class="summary-value">{{ formattedBalance }}</text>
          <text class="summary-unit">算力</text>
        </view>
        <view class="summary-meta">
          <text>累计实充 {{ formatPowerAmount(summary.totalRecharge) }}</text>
          <text
            >实充已消耗 {{ formatPowerAmount(summary.rechargeConsumed) }}</text
          >
        </view>
      </view>

      <view class="purchase-card">
        <view class="section-head">
          <view class="section-title">充值算力</view>
          <text class="redemption-entry" @tap="toggleRedemptionPanel">
            兑换码
          </text>
        </view>
        <view v-if="showRedemptionPanel" class="redemption-panel">
          <input
            v-model.trim="redemptionCode"
            class="redemption-input"
            type="text"
            maxlength="6"
            placeholder="输入兑换码"
            placeholder-class="purchase-placeholder"
            @input="handleRedemptionInput"
          />
          <view class="redemption-actions">
            <text class="redemption-action muted" @tap="clearRedemptionCode">
              清空
            </text>
          </view>
          <text class="redemption-status">{{ redemptionStatusText }}</text>
        </view>
        <view class="purchase-input">
          <input
            v-model="purchaseAmount"
            class="purchase-field"
            type="number"
            placeholder="输入万算力"
            placeholder-class="purchase-placeholder"
            @input="handlePurchaseInput"
          />
          <view class="purchase-divider"></view>
          <text class="purchase-unit">{{ purchaseUnitText }}</text>
        </view>
        <text class="purchase-tip">{{ purchaseTip }}</text>
        <view v-if="summary.recommendedPackages?.length" class="package-list">
          <view
            v-for="item in summary.recommendedPackages"
            :key="item.powerAmount"
            class="package-item"
            :class="{
              'package-item-active': selectedPackageAmount === item.powerAmount,
            }"
            @tap="selectPackage(item)"
          >
            <text
              class="package-corner-tag"
              :class="{ 'package-corner-tag-hidden': !item.discountTag }"
              >{{ item.discountTag }}</text
            >
            <view class="package-head">
              <text class="package-price">{{ formatPackagePrice(item) }}</text>
            </view>
            <text class="package-power"
              >{{ formatPackagePower(item.powerAmount) }} 算力</text
            >
            <text v-if="item.bonusPowerAmount" class="package-bonus"
              >首充送 {{ formatPackagePower(item.bonusPowerAmount) }} 算力</text
            >
            <text
              class="package-original-price"
              :class="{
                'package-original-price-hidden': !(
                  item.originalAmountFen &&
                  item.originalAmountFen > item.totalAmountFen
                ),
              }"
            >
              {{
                item.originalAmountFen &&
                item.originalAmountFen > item.totalAmountFen
                  ? `原价 ¥${formatFenToYuan(item.originalAmountFen)}`
                  : "原价占位"
              }}
            </text>
          </view>
        </view>
        <text class="purchase-hint">{{ validationMessage }}</text>
      </view>

      <view
        class="pay-button"
        :class="{ 'pay-button-disabled': isPayDisabled }"
        @tap="handlePay"
      >
        {{ payButtonText }}
      </view>

      <view v-if="paymentMessage" class="status-card">
        <text class="status-title">支付状态</text>
        <text class="status-message">{{ paymentMessage }}</text>
      </view>

      <view v-if="summary.latestRecharge" class="latest-card">
        <text class="latest-title">最近一笔充值</text>
        <view class="latest-row">
          <text>单号</text>
          <text>{{ summary.latestRecharge.rechargeNo }}</text>
        </view>
        <view class="latest-row">
          <text>金额</text>
          <text
            >¥{{ formatFenToYuan(summary.latestRecharge.totalAmountFen) }}</text
          >
        </view>
        <view class="latest-row">
          <text>数量</text>
          <text
            >{{
              formatPowerAmount(summary.latestRecharge.powerAmount)
            }}
            算力</text
          >
        </view>
        <view class="latest-row">
          <text>状态</text>
          <text>{{ getStatusText(summary.latestRecharge.payStatus) }}</text>
        </view>
      </view>
    </view>

    <app-tabbar active-tab="power" />
  </view>
</template>

<script>
import AppTabbar from "../../components/app-tabbar.vue";
import AppSubpageHeader from "../../components/app-subpage-header.vue";
import AppTopNotice from "../../components/app-top-notice.vue";
import {
  createMtH5PowerRecharge,
  getMtH5PowerRechargeResult,
  getMtH5PowerSummary,
  mockSuccessMtH5PowerRecharge,
  reportMtH5BehaviorEvent,
  reportMtH5PageView,
} from "../../utils/mt-auth";
import { showErrorToast, showToast } from "../../utils/ui";

function normalizePowerAmountParam(value) {
  const amount = Number(value);
  if (!Number.isInteger(amount) || amount <= 0) {
    return null;
  }
  return amount;
}

const REDEMPTION_CODE_PATTERN = /^[A-Z0-9]{6}$/;

export default {
  components: {
    AppTabbar,
    AppSubpageHeader,
    AppTopNotice,
  },
  data() {
    return {
      purchaseAmount: "",
      summary: {
        balance: 0,
        totalRecharge: 0,
        totalConsume: 0,
        totalGrant: 0,
        rechargeConsumed: 0,
        grantConsumed: 0,
        unitPower: 100000,
        unitPriceFen: 100,
        minPurchaseAmount: 1000000,
        maxPurchaseAmount: 1000000000,
        firstRecharge: true,
        firstRechargeOffer: null,
        recommendedPackages: [],
        pricingTiers: [],
        latestRecharge: null,
      },
      selectedPackageAmount: null,
      redemptionCode: "",
      appliedRedemptionCode: "",
      redemptionStatusText: "兑换码选填；不填则按当前默认价格结算",
      showRedemptionPanel: false,
      isLoading: false,
      isSubmitting: false,
      isRedemptionChecking: false,
      redemptionCheckTimer: null,
      redemptionCheckSeq: 0,
      pollingTimer: null,
      activeRechargeNo: "",
      paymentMessage: "",
      defaultPowerAmount: null,
      defaultPowerApplied: false,
      pageEntrySource: "DIRECT",
    };
  },
  onLoad(options) {
    this.pageEntrySource = options?.entrySource || "DIRECT";
    if (options?.rechargeNo) {
      this.activeRechargeNo = options.rechargeNo;
      this.paymentMessage = "支付结果确认中，请稍候...";
    }
    this.defaultPowerAmount = normalizePowerAmountParam(options?.powerAmount);
  },
  onShow() {
    reportMtH5PageView("POWER", this.pageEntrySource);
    this.loadSummary(this.appliedRedemptionCode);
    if (this.activeRechargeNo) {
      this.startPollingResult(this.activeRechargeNo, { silentPending: true });
    }
  },
  onUnload() {
    this.clearRedemptionCheckTimer();
    this.clearPollingTimer();
  },
  computed: {
    formattedBalance() {
      return this.formatPowerAmount(this.summary.balance);
    },
    purchaseUnitText() {
      return "万算力";
    },
    purchaseTip() {
      const baseTip = `输入单位：万算力。基础换算：每${this.formatPowerAmount(this.summary.unitPower || 100000)}算力=¥${this.formatFenToYuan(this.summary.unitPriceFen || 100)}，当前按${this.summary.firstRecharge ? "首充" : "续充"}档位折后结算`;
      return this.hasSpecialPackagePrice
        ? `${baseTip}，推荐档位如有活动价以下方卡片和支付金额为准`
        : baseTip;
    },
    currentDisplayAmount() {
      const amount = Number(this.purchaseAmount);
      return Number.isFinite(amount) ? amount : 0;
    },
    currentPowerAmount() {
      return this.currentDisplayAmount * 10000;
    },
    matchedPackage() {
      return (
        this.summary.recommendedPackages?.find(
          (item) => item.powerAmount === this.currentPowerAmount,
        ) || null
      );
    },
    matchedPricingTier() {
      return (
        this.summary.pricingTiers?.find((item) => {
          if (
            !this.currentPowerAmount ||
            this.currentPowerAmount < item.minPowerAmount
          ) {
            return false;
          }
          return (
            !item.maxPowerAmount ||
            this.currentPowerAmount <= item.maxPowerAmount
          );
        }) || null
      );
    },
    currentOriginalTotalAmountFen() {
      if (this.matchedPackage?.originalAmountFen) {
        return this.matchedPackage.originalAmountFen;
      }
      if (!this.currentPowerAmount || !this.summary.unitPower) {
        return 0;
      }
      return (
        Math.floor(this.currentPowerAmount / this.summary.unitPower) *
        this.summary.unitPriceFen
      );
    },
    hasDiscount() {
      return (
        !!this.matchedPricingTier?.discountTag &&
        this.currentOriginalTotalAmountFen > this.currentTotalAmountFen
      );
    },
    currentTotalAmountFen() {
      if (!this.currentPowerAmount) {
        return 0;
      }
      if (this.matchedPackage?.totalAmountFen) {
        return this.matchedPackage.totalAmountFen;
      }
      if (!this.matchedPricingTier?.payRateBasisPoints) {
        return this.currentOriginalTotalAmountFen;
      }
      return Math.round(
        (this.currentOriginalTotalAmountFen *
          this.matchedPricingTier.payRateBasisPoints) /
          10000,
      );
    },
    hasSpecialPackagePrice() {
      if (
        !this.summary.recommendedPackages?.length ||
        !this.summary.unitPower
      ) {
        return false;
      }
      return this.summary.recommendedPackages.some((item) => {
        const defaultOriginal =
          Math.floor((item.powerAmount || 0) / this.summary.unitPower) *
          this.summary.unitPriceFen;
        return item.originalAmountFen !== defaultOriginal;
      });
    },
    validationMessage() {
      if (!this.purchaseAmount) {
        return `最小充值单位 ${this.formatPowerAmount(this.summary.unitPower)} 算力，最少 ${this.formatPowerAmount(this.summary.minPurchaseAmount)} 算力，最多 ${this.formatPowerAmount(this.summary.maxPurchaseAmount)} 算力`;
      }
      if (
        !this.currentDisplayAmount ||
        !Number.isInteger(this.currentDisplayAmount)
      ) {
        return "请输入整数万算力数量";
      }
      if (this.currentPowerAmount < this.summary.minPurchaseAmount) {
        return `最少购买 ${this.formatPowerAmount(this.summary.minPurchaseAmount)} 算力`;
      }
      if (this.currentPowerAmount > this.summary.maxPurchaseAmount) {
        return `最多购买 ${this.formatPowerAmount(this.summary.maxPurchaseAmount)} 算力`;
      }
      if (this.currentPowerAmount % this.summary.unitPower !== 0) {
        return `购买量需为 ${this.formatPowerAmount(this.summary.unitPower)} 算力的整数倍`;
      }
      if (this.hasDiscount) {
        return `原价 ¥${this.formatFenToYuan(
          this.currentOriginalTotalAmountFen,
        )}，折后 ¥${this.formatFenToYuan(this.currentTotalAmountFen)}（${
          this.matchedPricingTier.discountTag
        }）`;
      }
      if (this.appliedRedemptionCode) {
        return `兑换码 ${this.appliedRedemptionCode} 已生效，本次支付 ¥${this.formatFenToYuan(this.currentTotalAmountFen)}`;
      }
      return `本次支付 ¥${this.formatFenToYuan(this.currentTotalAmountFen)}`;
    },
    isPayDisabled() {
      return (
        this.isSubmitting ||
        !this.purchaseAmount ||
        !this.currentDisplayAmount ||
        this.currentPowerAmount < this.summary.minPurchaseAmount ||
        this.currentPowerAmount > this.summary.maxPurchaseAmount ||
        this.currentPowerAmount % this.summary.unitPower !== 0
      );
    },
    payButtonText() {
      if (this.isSubmitting) {
        return "支付处理中...";
      }
      if (!this.currentTotalAmountFen) {
        return "立即支付";
      }
      if (this.hasDiscount) {
        return `${this.matchedPricingTier.discountTag}优惠支付`;
      }
      return `支付 ¥${this.formatFenToYuan(this.currentTotalAmountFen)}`;
    },
  },
  methods: {
    refreshSummary() {
      return this.loadSummary();
    },
    async loadSummary(redemptionCode, options = {}) {
      if (this.isLoading) {
        return;
      }
      this.isLoading = true;
      try {
        const effectiveRedemptionCode =
          typeof redemptionCode === "undefined"
            ? this.appliedRedemptionCode || undefined
            : redemptionCode || undefined;
        const summary = await getMtH5PowerSummary(effectiveRedemptionCode);
        this.applyPowerSummary(summary);
        return summary;
      } catch (error) {
        if (!options.silentError) {
          showErrorToast(error, "加载算力信息失败");
        }
        if (options.rethrow) {
          throw error;
        }
      } finally {
        this.isLoading = false;
      }
    },
    toggleRedemptionPanel() {
      this.showRedemptionPanel = !this.showRedemptionPanel;
    },
    handleRedemptionInput(event) {
      const rawValue = event?.detail?.value ?? this.redemptionCode;
      const normalizedCode = this.normalizeRedemptionCode(rawValue);
      if (normalizedCode !== this.redemptionCode) {
        this.redemptionCode = normalizedCode;
      }
      this.scheduleRedemptionAutoCheck(normalizedCode);
    },
    normalizeRedemptionCode(value) {
      return String(value || "")
        .toUpperCase()
        .replace(/[^A-Z0-9]/g, "")
        .slice(0, 6);
    },
    scheduleRedemptionAutoCheck(code) {
      this.clearRedemptionCheckTimer();
      this.redemptionCheckSeq += 1;
      const checkSeq = this.redemptionCheckSeq;
      const normalizedCode = this.normalizeRedemptionCode(code);

      if (!normalizedCode) {
        this.isRedemptionChecking = false;
        if (this.appliedRedemptionCode) {
          this.appliedRedemptionCode = "";
          this.loadSummary("");
        }
        this.redemptionStatusText = "兑换码选填；不填则按当前默认价格结算";
        return;
      }

      if (!REDEMPTION_CODE_PATTERN.test(normalizedCode)) {
        this.isRedemptionChecking = false;
        if (this.appliedRedemptionCode) {
          this.appliedRedemptionCode = "";
          this.loadSummary("");
          this.redemptionStatusText = "兑换码选填；不填则按当前默认价格结算";
        }
        return;
      }

      if (normalizedCode === this.appliedRedemptionCode) {
        this.isRedemptionChecking = false;
        this.redemptionStatusText = `兑换码 ${normalizedCode} 已生效，本次充值按当前优惠口径结算`;
        return;
      }

      this.redemptionCheckTimer = setTimeout(() => {
        this.autoApplyRedemptionCode(normalizedCode, checkSeq);
      }, 300);
    },
    async autoApplyRedemptionCode(code, checkSeq) {
      if (this.isLoading) {
        this.redemptionCheckTimer = setTimeout(() => {
          this.autoApplyRedemptionCode(code, checkSeq);
        }, 300);
        return;
      }
      this.isRedemptionChecking = true;
      this.isLoading = true;
      let shouldRestoreDefaultSummary = false;
      try {
        const summary = await getMtH5PowerSummary(code);
        if (
          checkSeq !== this.redemptionCheckSeq ||
          this.redemptionCode !== code
        ) {
          return;
        }
        this.applyPowerSummary(summary);
        this.appliedRedemptionCode = code;
        this.redemptionStatusText = `兑换码 ${code} 已生效，本次充值按当前优惠口径结算`;
        reportMtH5BehaviorEvent("H5_REDEMPTION_APPLY_RESULT", {
          result: "SUCCESS",
        });
        showToast("兑换码已自动应用");
      } catch (error) {
        if (
          checkSeq !== this.redemptionCheckSeq ||
          this.redemptionCode !== code
        ) {
          return;
        }
        this.appliedRedemptionCode = "";
        this.redemptionStatusText = "兑换码无效，请核对后重新输入";
        reportMtH5BehaviorEvent("H5_REDEMPTION_APPLY_RESULT", {
          result: "FAILED",
        });
        shouldRestoreDefaultSummary = true;
      } finally {
        this.isLoading = false;
        if (checkSeq === this.redemptionCheckSeq) {
          this.isRedemptionChecking = false;
        }
      }
      if (shouldRestoreDefaultSummary) {
        await this.loadSummary("");
      }
    },
    applyPowerSummary(summary) {
      this.summary = {
        ...this.summary,
        ...summary,
      };
      this.applyDefaultPowerAmountSelection();
    },
    async clearRedemptionCode() {
      this.clearRedemptionCheckTimer();
      this.redemptionCheckSeq += 1;
      this.isRedemptionChecking = false;
      this.redemptionCode = "";
      this.appliedRedemptionCode = "";
      this.redemptionStatusText = "兑换码已清空；本次充值将按当前默认价格结算";
      await this.loadSummary("");
    },
    handlePurchaseInput(event) {
      const value = event?.detail?.value ?? this.purchaseAmount;
      this.purchaseAmount = value ? String(value).replace(/[^\d]/g, "") : "";
      const amount = (Number(this.purchaseAmount) || 0) * 10000;
      const matchedPackage = this.summary.recommendedPackages?.find(
        (item) => item.powerAmount === amount,
      );
      this.selectedPackageAmount = matchedPackage
        ? matchedPackage.powerAmount
        : null;
    },
    selectPackage(item) {
      this.selectedPackageAmount = item.powerAmount;
      this.purchaseAmount = String(item.powerAmount / 10000);
    },
    applyDefaultPowerAmountSelection() {
      if (this.defaultPowerApplied || !this.defaultPowerAmount) {
        return;
      }
      const matchedPackage = this.summary.recommendedPackages?.find(
        (item) => Number(item.powerAmount) === this.defaultPowerAmount,
      );
      if (!matchedPackage) {
        this.defaultPowerApplied = true;
        return;
      }
      this.selectPackage(matchedPackage);
      this.defaultPowerApplied = true;
    },
    async handlePay() {
      if (this.isPayDisabled) {
        showToast(this.validationMessage);
        return;
      }

      reportMtH5BehaviorEvent("H5_RECHARGE_SUBMIT_CLICK", {
        powerAmount: this.currentPowerAmount,
        selectedPackage: Boolean(this.selectedPackageAmount),
        hasRedemptionCode: Boolean(this.appliedRedemptionCode),
      });

      this.isSubmitting = true;
      try {
        const payload = await createMtH5PowerRecharge(
          this.currentPowerAmount,
          this.appliedRedemptionCode,
        );
        this.activeRechargeNo = payload.rechargeNo;
        this.paymentMessage = "支付发起成功，等待支付确认...";
        this.redemptionCode = "";
        this.appliedRedemptionCode = "";
        this.redemptionStatusText = "兑换码选填；不填则按当前默认价格结算";

        if (payload.mockPayEnabled) {
          await mockSuccessMtH5PowerRecharge(payload.rechargeNo);
          await this.startPollingResult(payload.rechargeNo);
          return;
        }

        if (!payload.mwebUrl) {
          throw new Error("当前支付方式未返回微信拉起地址");
        }

        this.redirectToWechatPay(payload);
      } catch (error) {
        this.paymentMessage = error.message || "支付发起失败，请稍后重试";
        showToast(this.paymentMessage);
      } finally {
        this.isSubmitting = false;
      }
    },
    redirectToWechatPay(payload) {
      const mwebUrl = payload.mwebUrl;
      if (typeof window === "undefined") {
        throw new Error("当前环境不支持 H5 微信支付跳转");
      }

      const redirectUrl = `${window.location.origin}${window.location.pathname}#/pages/power/index?rechargeNo=${payload.rechargeNo}`;
      const joiner = mwebUrl.includes("?") ? "&" : "?";
      window.location.href = `${mwebUrl}${joiner}redirect_url=${encodeURIComponent(redirectUrl)}`;
    },
    async startPollingResult(rechargeNo, options = {}) {
      const { silentPending = false } = options;
      this.clearPollingTimer();

      let attempt = 0;
      const poll = async () => {
        attempt += 1;
        try {
          const result = await getMtH5PowerRechargeResult(rechargeNo);
          if (result.payStatus === 2) {
            this.paymentMessage = `支付成功，已到账 ${result.powerAmount} 算力`;
            this.activeRechargeNo = "";
            this.clearPollingTimer();
            await this.loadSummary();
            return;
          }

          if (result.payStatus === 3 || result.payStatus === 4) {
            this.paymentMessage = "支付未完成，请重新发起充值";
            this.activeRechargeNo = "";
            this.clearPollingTimer();
            await this.loadSummary();
            return;
          }

          if (!silentPending) {
            this.paymentMessage = "支付确认中，请稍候...";
          }

          if (attempt >= 8) {
            this.paymentMessage = "支付结果暂未确认，可稍后刷新查看";
            this.clearPollingTimer();
            await this.loadSummary();
            return;
          }

          this.pollingTimer = setTimeout(poll, 1500);
        } catch (error) {
          this.paymentMessage = error.message || "查询支付结果失败";
          this.clearPollingTimer();
        }
      };

      await poll();
    },
    clearPollingTimer() {
      if (this.pollingTimer) {
        clearTimeout(this.pollingTimer);
        this.pollingTimer = null;
      }
    },
    clearRedemptionCheckTimer() {
      if (this.redemptionCheckTimer) {
        clearTimeout(this.redemptionCheckTimer);
        this.redemptionCheckTimer = null;
      }
    },
    formatFenToYuan(value) {
      return ((Number(value) || 0) / 100).toFixed(2);
    },
    formatPackagePrice(item) {
      if (
        item?.originalAmountFen &&
        item.originalAmountFen > item.totalAmountFen
      ) {
        return `¥${this.formatFenToYuan(item.totalAmountFen)}`;
      }
      return `¥${this.formatFenToYuan(item?.totalAmountFen)}`;
    },
    formatPackagePower(value) {
      const amount = Number(value) || 0;
      return this.formatPowerAmount(amount);
    },
    formatPowerAmount(value) {
      const amount = Number(value) || 0;
      if (amount >= 100000000) {
        return `${this.formatCompactNumber(amount / 100000000, 2)}亿`;
      }
      if (amount >= 10000) {
        return `${this.formatCompactNumber(amount / 10000, 2)}万`;
      }
      return `${amount}`;
    },
    formatCompactNumber(value, maxDecimals = 2) {
      const numeric = Number(value) || 0;
      return numeric.toFixed(maxDecimals).replace(/\.?0+$/, "");
    },
    getStatusText(payStatus) {
      if (payStatus === 2) {
        return "支付成功";
      }
      if (payStatus === 1) {
        return "支付中";
      }
      if (payStatus === 3) {
        return "支付失败";
      }
      if (payStatus === 4) {
        return "已关闭";
      }
      return "待支付";
    },
  },
};
</script>

<style>
page {
  background: #f6f6f8;
}
</style>

<style scoped>
.page {
  min-height: 100vh;
  padding-bottom: calc(env(safe-area-inset-bottom) + 132rpx);
  box-sizing: border-box;
  background: #f6f6f8;
}

.content {
  margin-top: 20rpx;
  padding: 0 20rpx;
}

.power-top-notice {
  margin-bottom: 8rpx;
}

.summary-card {
  padding: 34rpx 32rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #2f3137 0%, #434754 100%);
  color: #ffffff;
  box-shadow: 0 20rpx 48rpx rgba(30, 31, 40, 0.14);
}

.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.summary-label {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.72);
}

.summary-refresh {
  font-size: 24rpx;
  color: #f7d56f;
}

.summary-main {
  display: flex;
  align-items: baseline;
  margin-top: 24rpx;
}

.summary-value {
  font-size: 68rpx;
  font-weight: 700;
  line-height: 1;
}

.summary-unit {
  margin-left: 14rpx;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.72);
}

.summary-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx 24rpx;
  margin-top: 24rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.72);
}

.purchase-card {
  margin-top: 32rpx;
  padding: 32rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 16rpx 44rpx rgba(21, 28, 42, 0.06);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}

.section-title {
  font-size: 28rpx;
  color: #2a2f35;
  font-weight: 700;
}

.redemption-entry {
  color: #92400e;
  font-size: 24rpx;
  font-weight: 500;
  line-height: 1.4;
  text-decoration: underline;
  text-underline-offset: 6rpx;
}

.redemption-panel {
  margin: 20rpx 0 24rpx;
  padding: 20rpx 24rpx;
  border-radius: 24rpx;
  background: #fff7ed;
}

.redemption-input {
  width: 100%;
  min-height: 84rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
  background: #ffffff;
  font-size: 28rpx;
  color: #111827;
  box-sizing: border-box;
}

.redemption-actions {
  display: flex;
  gap: 24rpx;
  margin-top: 16rpx;
}

.redemption-action {
  font-size: 24rpx;
  font-weight: 600;
  color: #c2410c;
}

.redemption-action.muted {
  color: #9ca3af;
}

.redemption-status {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #7c2d12;
  line-height: 1.5;
}

.purchase-input {
  display: flex;
  align-items: center;
  height: 98rpx;
  padding: 0 26rpx;
  border: 2rpx solid #e4e1dc;
  border-radius: 20rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.purchase-field {
  flex: 1;
  min-width: 0;
  font-size: 34rpx;
  color: #36373c;
}

.purchase-placeholder {
  color: #c9c9cc;
}

.purchase-divider {
  width: 2rpx;
  height: 44rpx;
  margin: 0 24rpx;
  background: #ded9d2;
}

.purchase-unit {
  font-size: 34rpx;
  color: #36373c;
  font-weight: 500;
  white-space: nowrap;
}

.purchase-tip {
  display: block;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #5f8de6;
}

.package-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 28rpx;
}

.package-item {
  position: relative;
  overflow: hidden;
  width: calc(50% - 8rpx);
  min-width: 0;
  padding: 60rpx 22rpx 22rpx;
  border: 2rpx solid #ece7de;
  border-radius: 20rpx;
  background: #faf8f3;
  box-sizing: border-box;
}

.package-item-active {
  border-color: #f1c74e;
  background: #fff7da;
}

.package-corner-tag {
  position: absolute;
  top: 0;
  right: 0;
  min-width: 120rpx;
  height: 40rpx;
  padding: 0 16rpx;
  border-bottom-left-radius: 18rpx;
  background: linear-gradient(135deg, #ff8a3d 0%, #ff5b2e 100%);
  color: #ffffff;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 40rpx;
  text-align: center;
  box-shadow: 0 8rpx 18rpx rgba(255, 91, 46, 0.2);
}

.package-corner-tag-hidden {
  visibility: hidden;
  box-shadow: none;
}

.package-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.package-price {
  display: block;
  font-size: 30rpx;
  color: #8f6f1f;
  font-weight: 700;
}

.package-power {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #2a2f35;
  font-weight: 600;
}

.package-bonus {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #ff4f28;
  font-weight: 600;
}

.package-original-price {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #a7a195;
  text-decoration: line-through;
}

.package-original-price-hidden {
  visibility: hidden;
}

.purchase-hint {
  display: block;
  margin-top: 20rpx;
  font-size: 24rpx;
  color: #8a8f98;
}

.pay-button {
  margin-top: 28rpx;
  height: 98rpx;
  border-radius: 18rpx;
  background: #f2ce46;
  color: #2a2416;
  font-size: 42rpx;
  font-weight: 500;
  line-height: 98rpx;
  text-align: center;
}

.pay-button-disabled {
  background: #e8ddaa;
  color: rgba(42, 36, 22, 0.5);
}

.status-card,
.latest-card {
  margin-top: 28rpx;
  padding: 28rpx 32rpx;
  border-radius: 24rpx;
  background: #ffffff;
  box-shadow: 0 12rpx 30rpx rgba(21, 28, 42, 0.05);
}

.status-title,
.latest-title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #2a2f35;
}

.status-message {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #5a6070;
  line-height: 1.6;
}

.latest-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #5a6070;
}
</style>
