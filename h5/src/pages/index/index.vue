<template>
  <view class="page">
    <view class="page-shell">
      <view class="header">
        <app-top-notice
          message="登录 PC 端: https://aicadmin.waimaidashi.com"
          copy-text="https://aicadmin.waimaidashi.com"
          :show-icon="false"
          safe-top
        />

        <view class="shop-row">
          <view class="shop-info">
            <image
              class="shop-icon"
              src="/static/icons/shop.png"
              mode="aspectFit"
            ></image>
            <text class="shop-name">{{ shopDisplayName }}</text>
          </view>
          <text class="shop-link" @tap="openSupportPrompt('USE_GUIDE')">使用指导 ></text>
        </view>

        <view class="vip-card">
          <view>
            <text class="vip-title">高级版</text>
            <text class="vip-date">{{ vipExpireText }}</text>
          </view>
          <view class="vip-button" @tap="openRenewalPage">立即续费</view>
        </view>
      </view>

      <view class="content">
        <view class="section-card feature-card ai-card">
          <view class="feature-head">
            <view class="llm-title-wrap">
              <image
                class="llm-logo"
                src="/static/icons/zhuq.png"
                mode="widthFix"
              ></image>
              <text class="llm-title">ZhuQ-1 / 自研大模型:朱雀</text>
            </view>
            <view
              class="feature-head-status ai-head-status"
              :class="{ 'feature-head-status-off': !autoReplyEnabled }"
            >
              <image
                class="feature-head-status-icon"
                :src="
                  autoReplyEnabled
                    ? '/static/icons/open.svg'
                    : '/static/icons/close.svg'
                "
                mode="aspectFit"
              ></image>
              <text>{{ autoReplyEnabled ? "运行中" : "已关闭" }}</text>
            </view>
          </view>

          <view class="feature-body">
            <view class="feature-icon robot-icon robot-icon-wrap">
              <image
                class="robot-icon-image"
                src="/static/icons/promotion_robot.png"
                mode="aspectFit"
              ></image>
            </view>
            <view class="feature-copy">
              <view class="feature-row">
                <view class="ai-main-copy">
                  <text class="feature-name">一键开启AI回复</text>
                  <text class="feature-desc">AI自动回复顾客消息</text>
                </view>
                <view
                  class="toggle"
                  :class="{
                    'toggle-on': autoReplyEnabled,
                    'toggle-off': !autoReplyEnabled,
                    'toggle-disabled': operationSettingsSubmitting,
                  }"
                  @tap.stop="toggleAutoReply"
                >
                  <view class="toggle-dot"></view>
                </view>
              </view>
            </view>
          </view>

          <view
            v-if="hasPowerStats"
            class="power-balance-bar"
            :class="powerBalanceStatusClass"
          >
            <view
              class="power-balance-fill"
              :style="{ width: powerBalanceBarWidth }"
            >
              <text class="power-balance-text">{{
                powerBalanceStatusText
              }}</text>
            </view>
          </view>

          <view v-if="reauthRequired" class="promotion-warning">
            <text class="promotion-warning-text"
              >门店授权已失效，请重新授权后继续自动回复。</text
            >
          </view>

          <view
            class="broadcast-entry"
            :class="{ 'broadcast-entry-disabled': !broadcastRightGranted }"
            @tap="openBroadcastDialog"
          >
            <view class="broadcast-entry-copy">
              <text class="broadcast-entry-title">消息群发</text>
              <text class="broadcast-entry-count"
                >（已群发{{ broadcastTotalSentCount }}条）</text
              >
            </view>
            <image
              class="broadcast-entry-icon"
              src="/static/icons/msg.svg"
              mode="aspectFit"
            ></image>
            <image
              class="broadcast-entry-badge"
              src="/static/icons/hot.svg"
              mode="aspectFit"
            ></image>
          </view>
        </view>

        <view class="section-card feature-card">
          <view class="feature-head">
            <text class="feature-title">知识库</text>
            <text class="feature-head-status">{{ knowledgeStatusText }}</text>
          </view>

          <view class="feature-body">
            <image
              class="feature-icon"
              src="/static/icons/doc.svg"
              mode="aspectFit"
            ></image>
            <view class="feature-copy">
              <view class="feature-row">
                <view>
                  <text class="feature-name">让AI学习知识库</text>
                  <text class="feature-desc feature-desc-inline"
                    >让AI根据知识库内容回复</text
                  >
                </view>
                <view class="outline-button" @tap="goToKnowledgePage"
                  >配置</view
                >
              </view>
            </view>
          </view>
        </view>

        <view class="section-card feature-card log-entry-card">
          <view class="feature-head log-entry-head">
            <text class="feature-title">查看日志</text>
          </view>
          <view class="feature-body feature-body-center">
            <image
              class="feature-icon"
              src="/static/icons/log.svg"
              mode="aspectFit"
            ></image>
            <view class="feature-copy">
              <view class="feature-row">
                <view>
                  <text class="feature-name">查看AI回复记录</text>
                  <text class="feature-desc log-entry-desc"
                    >实时查看AI回复内容</text
                  >
                </view>
                <view class="outline-button" @tap="goToLogPage">查看</view>
              </view>
            </view>
          </view>
        </view>

        <view class="section-card stats-card">
          <view class="stats-block">
            <view class="stats-toolbar">
              <picker
                mode="selector"
                :range="statsTitleOptions"
                :value="selectedStatsRangeIndex"
                @change="handleStatsRangeChange"
              >
                <view class="stats-range-picker">
                  <text class="stats-range-picker-text">{{ statsTitle }}</text>
                  <text class="stats-range-picker-caret">▼</text>
                </view>
              </picker>
              <view
                v-if="hasPowerStats"
                class="stats-text-switch"
                @tap="toggleStatsMode"
              >
                <text
                  class="stats-text-switch-item"
                  :class="{
                    'stats-text-switch-active': statsMode === 'messages',
                  }"
                  >效果统计</text
                >
                <text class="stats-text-switch-divider">|</text>
                <text
                  class="stats-text-switch-item"
                  :class="{ 'stats-text-switch-active': statsMode === 'power' }"
                  >算力统计</text
                >
              </view>
            </view>
            <view class="stats-grid">
              <view
                v-for="item in currentStats"
                :key="item.label"
                class="stat-card"
                :class="item.cardClass"
              >
                <text class="stat-label">{{ item.label }}</text>
                <text class="stat-value" :class="item.valueClass">{{
                  item.value
                }}</text>
                <view class="stat-foot">
                  <text
                    v-if="item.showCompare !== false"
                    class="stat-foot-label"
                    >{{ item.compareLabel || statsCompareLabel }}</text
                  >
                  <text class="stat-foot-value" :class="item.trendClass">{{
                    item.trend
                  }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <view class="feedback-link" @tap="openSupportPrompt('FEEDBACK')">建议与反馈</view>
      </view>
    </view>

    <app-tabbar active-tab="home" />

    <view
      v-if="activityPopupVisible"
      class="activity-popup-mask"
      @tap="closeActivityPopup"
      @touchmove.stop.prevent="noop"
    >
      <view class="activity-popup-dialog">
        <image
          class="activity-popup-image"
          src="/static/images/home_302.png"
          mode="widthFix"
        ></image>
        <view
          class="activity-popup-close-zone"
          role="button"
          @tap.stop="closeActivityPopup"
        ></view>
        <view
          class="activity-popup-action-zone"
          role="button"
          @tap.stop="openActivityPage"
        ></view>
      </view>
    </view>

    <view
      v-if="supportPromptVisible"
      class="modal-mask"
      @tap="closeSupportPrompt"
    >
      <view class="support-modal" @tap.stop>
        <text class="support-modal-title">联系客服了解更多功能</text>
        <view class="support-modal-qrcode-frame">
          <image
            class="support-modal-qrcode"
            src="/static/images/qrcode.jpg"
            mode="aspectFit"
          ></image>
        </view>
        <text class="support-modal-subtitle">扫码添加客服/进群咨询</text>
        <text class="support-modal-footer"
          >连锁店/多店铺/代运营商家*专人对接</text
        >
        <view class="support-modal-button" @tap="closeSupportPrompt"
          >我知道了</view
        >
      </view>
    </view>

    <view
      v-if="broadcastDialogVisible"
      class="modal-mask"
      @tap="closeBroadcastDialog"
    >
      <view class="broadcast-modal" @tap.stop>
        <text class="broadcast-modal-title">群发设置</text>

        <view class="broadcast-field">
          <text class="broadcast-field-label">日期：</text>
          <view
            class="broadcast-select broadcast-select-grow"
            @tap="toggleWeekDayPanel"
          >
            <text class="broadcast-select-text">{{ weekDaysText }}</text>
            <text class="broadcast-select-arrow">▼</text>
          </view>
          <view class="broadcast-select" @tap="toggleRepeatModePanel">
            <text class="broadcast-select-text">{{ repeatModeText }}</text>
            <text class="broadcast-select-arrow">▼</text>
          </view>
        </view>

        <view v-if="repeatModePanelVisible" class="broadcast-option-panel">
          <view
            v-for="option in repeatModeOptions"
            :key="option.value"
            class="broadcast-option-item"
            :class="{
              'broadcast-option-item-active': form.repeatMode === option.value,
            }"
            @tap="selectRepeatMode(option.value)"
            >{{ option.label }}</view
          >
        </view>

        <view v-if="weekDayPanelVisible" class="broadcast-weekday-panel">
          <view
            v-for="option in weekDayOptions"
            :key="option.value"
            class="broadcast-weekday-item"
            @tap="toggleWeekDay(option.value)"
          >
            <view
              class="broadcast-weekday-checkbox"
              :class="{
                'broadcast-weekday-checkbox-checked': isWeekDaySelected(
                  option.value,
                ),
              }"
            >
              <text v-if="isWeekDaySelected(option.value)">✓</text>
            </view>
            <text class="broadcast-weekday-label">{{ option.label }}</text>
          </view>
        </view>

        <view class="broadcast-field">
          <text class="broadcast-field-label">时间：</text>
          <view
            class="broadcast-select broadcast-select-grow"
            @tap="toggleTimePanel"
          >
            <text class="broadcast-select-text">{{ form.sendTime }}</text>
            <text class="broadcast-select-arrow">▼</text>
          </view>
        </view>

        <view v-if="timePanelVisible" class="broadcast-time-panel">
          <picker-view
            class="broadcast-time-picker"
            :value="timePickerValue"
            @change="handleSendTimeChange"
          >
            <picker-view-column
              v-for="(column, columnIndex) in timePickerRange"
              :key="columnIndex"
            >
              <view
                v-for="label in column"
                :key="label"
                class="broadcast-time-item"
                >{{ label }}</view
              >
            </picker-view-column>
          </picker-view>
        </view>

        <view class="broadcast-field broadcast-field-top">
          <text class="broadcast-field-label">内容：</text>
          <view class="broadcast-textarea-wrap">
            <textarea
              class="broadcast-textarea"
              v-model="form.content"
              placeholder="请填写发送的内容…"
              placeholder-class="broadcast-textarea-placeholder"
              :maxlength="broadcastContentMaxLength"
            ></textarea>
            <view class="broadcast-textarea-footer">
              <text class="broadcast-textarea-rule"
                >规则：禁止发送敏感词信息！</text
              >
              <text class="broadcast-textarea-counter"
                >{{ broadcastContentLength }}/{{
                  broadcastContentMaxLength
                }}</text
              >
            </view>
          </view>
        </view>

        <view class="broadcast-modal-actions">
          <view
            class="broadcast-modal-button broadcast-modal-button-primary"
            :class="{ 'broadcast-modal-button-disabled': broadcastSubmitting }"
            @tap="submitBroadcastSetting"
            >发送</view
          >
          <view
            v-if="broadcastConfigured"
            class="broadcast-modal-button"
            :class="{ 'broadcast-modal-button-disabled': broadcastSubmitting }"
            @tap="deleteBroadcastSetting"
            >删除</view
          >
          <view
            v-else
            class="broadcast-modal-button"
            @tap="closeBroadcastDialog"
            >取消</view
          >
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import AppTabbar from "../../components/app-tabbar.vue";
import AppTopNotice from "../../components/app-top-notice.vue";
import {
  deleteMtH5BroadcastSetting,
  getMtH5HomeDashboard,
  getShopInfo,
  reportMtH5BehaviorEvent,
  reportMtH5PageView,
  saveMtH5BroadcastSetting,
  updateMtH5OperationSettings,
} from "../../utils/mt-auth";
import { openMtExternalPage } from "../../utils/ui";

const POWER_BALANCE_BAR_MAX = 10000000;
const POWER_BALANCE_SUFFICIENT_THRESHOLD = 6000000;
const POWER_BALANCE_TIGHT_THRESHOLD = 1000000;
const RENEWAL_PAGE_URL =
  "https://waimaieapp.meituan.com/ad/mycraft/activity?MYCRAFT_PAGE_ID=2486&appId=138";
// 临时关闭首页引流弹窗；恢复展示时改为 true。
const ACTIVITY_POPUP_ENABLED = false;
let activityPopupShownInCurrentApp = false;

const BROADCAST_CONTENT_MAX_LENGTH = 100;
const BROADCAST_WEEK_DAY_OPTIONS = [
  { value: 1, label: "周一" },
  { value: 2, label: "周二" },
  { value: 3, label: "周三" },
  { value: 4, label: "周四" },
  { value: 5, label: "周五" },
  { value: 6, label: "周六" },
  { value: 7, label: "周日" },
];
const BROADCAST_REPEAT_MODE_OPTIONS = [
  { value: "EVERY", label: "每次" },
  { value: "ONCE", label: "一次" },
];

function padTwoDigits(value) {
  return String(value).padStart(2, "0");
}

function buildRangeLabels(size) {
  return Array.from({ length: size }, (_, index) => padTwoDigits(index));
}

function parseTimeParts(timeText) {
  const [hour = 10, minute = 0, second = 0] = String(timeText || "10:00:00")
    .split(":")
    .map((part) => Number(part) || 0);
  return [hour, minute, second];
}

function toNumber(value, fallback = 0) {
  const result = Number(value);
  return Number.isFinite(result) ? result : fallback;
}

function formatDisplayNumber(value) {
  const numeric = toNumber(value, 0);
  if (Number.isInteger(numeric)) {
    return String(numeric);
  }
  return numeric.toFixed(2).replace(/\.?0+$/, "");
}

function formatCompactNumber(value, maxDecimals = 2) {
  const numeric = Number(value) || 0;
  return numeric.toFixed(maxDecimals).replace(/\.?0+$/, "");
}

function formatPowerAmount(value) {
  const amount = Number(value) || 0;
  if (amount >= 100000000) {
    return `${formatCompactNumber(amount / 100000000, 2)}亿`;
  }
  if (amount >= 10000) {
    return `${formatCompactNumber(amount / 10000, 2)}万`;
  }
  return `${amount}`;
}

function formatPercentValue(value) {
  return `${formatDisplayNumber(toNumber(value, 0) * 100)}%`;
}

function buildTrend(currentValue, previousValue) {
  const current = toNumber(currentValue, 0);
  const previous = toNumber(previousValue, 0);

  if (current === previous) {
    return {
      text: "持平--",
      trendClass: "trend-muted",
    };
  }

  if (previous === 0) {
    return {
      text: current > 0 ? "+100%" : "-100%",
      trendClass: current > previous ? "trend-red" : "trend-green",
    };
  }

  const deltaRate = ((current - previous) / Math.abs(previous)) * 100;
  const prefix = deltaRate > 0 ? "+" : "";
  return {
    text: `${prefix}${formatDisplayNumber(deltaRate)}%`,
    trendClass: deltaRate > 0 ? "trend-red" : "trend-green",
  };
}

function formatDateText(value) {
  if (!value) {
    return "未开通";
  }

  const text = String(value);
  if (text.length >= 10) {
    return `${text.slice(0, 10)}到期`;
  }
  return "未开通";
}

export default {
  components: {
    AppTabbar,
    AppTopNotice,
  },
  data() {
    return {
      shopName: "加载中...",
      shopPoiId: "--",
      membership: {
        active: false,
        expireAt: null,
      },
      reauthRequired: false,
      autoReplyEnabled: true,
      operationSettingsSubmitting: false,
      pageEntrySource: "APP",
      statsMode: "messages",
      hasPowerStats: true,
      supportPromptVisible: false,
      activityPopupVisible: false,
      broadcastDialogVisible: false,
      broadcastSubmitting: false,
      broadcastConfigured: false,
      broadcastTotalSentCount: 0,
      // 默认放行：接口数据回来前不置灰，避免首屏闪一下灰态。
      broadcastRightGranted: true,
      weekDayPanelVisible: false,
      repeatModePanelVisible: false,
      timePanelVisible: false,
      weekDayOptions: BROADCAST_WEEK_DAY_OPTIONS,
      repeatModeOptions: BROADCAST_REPEAT_MODE_OPTIONS,
      form: {
        weekDays: [],
        repeatMode: "EVERY",
        sendTime: "10:00:00",
        content: "",
      },
      statsRange: "TODAY",
      statsRangeOptions: [
        { label: "今日", value: "TODAY", compareLabel: "较昨日" },
        { label: "近7天", value: "LAST_7_DAYS", compareLabel: "较前7日" },
        { label: "近30天", value: "LAST_30_DAYS", compareLabel: "较前30日" },
      ],
      messageStats: {
        currentMessageReplyCount: 0,
        previousMessageReplyCount: 0,
        currentAfterSaleMessageCount: 0,
        previousAfterSaleMessageCount: 0,
        messageReplyRate: 0,
        previousMessageReplyRate: 0,
      },
      powerStats: {
        currentConsumedPower: 0,
        previousConsumedPower: 0,
        currentEstimatedPower: 0,
        previousEstimatedPower: 0,
        balance: 0,
      },
    };
  },
  computed: {
    statsTitle() {
      const selectedRange = this.statsRangeOptions.find(
        (item) => item.value === this.statsRange,
      );
      return `${selectedRange?.label || "今日"}数据统计`;
    },
    statsTitleOptions() {
      return this.statsRangeOptions.map((item) => `${item.label}数据统计`);
    },
    selectedStatsRangeIndex() {
      const index = this.statsRangeOptions.findIndex(
        (item) => item.value === this.statsRange,
      );
      return index === -1 ? 0 : index;
    },
    statsCompareLabel() {
      return (
        this.statsRangeOptions.find((item) => item.value === this.statsRange)
          ?.compareLabel || "较昨日"
      );
    },
    currentStats() {
      if (this.statsMode === "power") {
        const consumeTrend = buildTrend(
          this.powerStats.currentConsumedPower,
          this.powerStats.previousConsumedPower,
        );
        const estimatedTrend = buildTrend(
          this.powerStats.currentEstimatedPower,
          this.powerStats.previousEstimatedPower,
        );
        return [
          {
            label: "消耗算力",
            value: formatDisplayNumber(this.powerStats.currentConsumedPower),
            trend: consumeTrend.text,
            cardClass: "stat-card-warm",
            valueClass: "stat-value-dark",
            trendClass: consumeTrend.trendClass,
          },
          {
            label: "预计消耗",
            value: formatDisplayNumber(this.powerStats.currentEstimatedPower),
            trend: estimatedTrend.text,
            cardClass: "stat-card-cool",
            valueClass: "stat-value-dark",
            trendClass: estimatedTrend.trendClass,
          },
          {
            label: "算力余额",
            value: formatPowerAmount(this.powerStats.balance),
            trend: "当前余额",
            showCompare: false,
            cardClass: "stat-card-pink",
            valueClass: "stat-value-dark stat-value-compact",
            trendClass: "trend-muted",
          },
        ];
      }

      const replyTrend = buildTrend(
        this.messageStats.currentMessageReplyCount,
        this.messageStats.previousMessageReplyCount,
      );
      const afterSaleTrend = buildTrend(
        this.messageStats.currentAfterSaleMessageCount,
        this.messageStats.previousAfterSaleMessageCount,
      );
      const replyRateTrend = buildTrend(
        this.messageStats.messageReplyRate,
        this.messageStats.previousMessageReplyRate,
      );
      return [
        {
          label: "消息回复数",
          value: formatDisplayNumber(
            this.messageStats.currentMessageReplyCount,
          ),
          trend: replyTrend.text,
          cardClass: "stat-card-warm",
          valueClass: "stat-value-dark",
          trendClass: replyTrend.trendClass,
        },
        {
          label: "售后消息数",
          value: formatDisplayNumber(
            this.messageStats.currentAfterSaleMessageCount,
          ),
          trend: afterSaleTrend.text,
          cardClass: "stat-card-cool",
          valueClass: "stat-value-dark",
          trendClass: afterSaleTrend.trendClass,
        },
        {
          label: "消息回复率",
          value: formatPercentValue(this.messageStats.messageReplyRate),
          trend: replyRateTrend.text,
          cardClass: "stat-card-pink",
          valueClass: "stat-value-dark",
          trendClass: replyRateTrend.trendClass,
        },
      ];
    },
    shopDisplayName() {
      const poiId = String(this.shopPoiId || "").trim();
      return poiId && poiId !== "--"
        ? `${this.shopName}（门店 ID： ${poiId}）`
        : this.shopName;
    },
    vipExpireText() {
      return formatDateText(this.membership.expireAt);
    },
    knowledgeStatusText() {
      return "已配置";
    },
    broadcastContentMaxLength() {
      return BROADCAST_CONTENT_MAX_LENGTH;
    },
    broadcastContentLength() {
      return String(this.form.content || "").length;
    },
    weekDaysText() {
      if (!this.form.weekDays.length) {
        return "请选择日期";
      }
      return this.form.weekDays
        .map(
          (day) =>
            BROADCAST_WEEK_DAY_OPTIONS.find((item) => item.value === day)
              ?.label,
        )
        .filter(Boolean)
        .join("、");
    },
    selectedRepeatModeIndex() {
      const index = BROADCAST_REPEAT_MODE_OPTIONS.findIndex(
        (item) => item.value === this.form.repeatMode,
      );
      return index === -1 ? 0 : index;
    },
    repeatModeText() {
      return (
        BROADCAST_REPEAT_MODE_OPTIONS[this.selectedRepeatModeIndex]?.label ||
        "每次"
      );
    },
    timePickerRange() {
      return [buildRangeLabels(24), buildRangeLabels(60), buildRangeLabels(60)];
    },
    timePickerValue() {
      return parseTimeParts(this.form.sendTime);
    },
    powerBalanceAmount() {
      return Math.max(0, Number(this.powerStats.balance || 0));
    },
    powerBalancePercent() {
      if (!POWER_BALANCE_BAR_MAX) {
        return 0;
      }
      return Math.min(
        100,
        (this.powerBalanceAmount / POWER_BALANCE_BAR_MAX) * 100,
      );
    },
    powerBalanceBarWidth() {
      const percent = this.powerBalancePercent;
      if (percent <= 0) {
        return "132rpx";
      }
      return `${Math.max(percent, 24)}%`;
    },
    powerBalanceStatusText() {
      if (this.powerBalanceAmount >= POWER_BALANCE_SUFFICIENT_THRESHOLD) {
        return "算力充足";
      }
      if (this.powerBalanceAmount >= POWER_BALANCE_TIGHT_THRESHOLD) {
        return "算力紧张";
      }
      return "算力不足";
    },
    powerBalanceStatusClass() {
      if (this.powerBalanceAmount >= POWER_BALANCE_SUFFICIENT_THRESHOLD) {
        return "power-balance-bar-sufficient";
      }
      if (this.powerBalanceAmount >= POWER_BALANCE_TIGHT_THRESHOLD) {
        return "power-balance-bar-tight";
      }
      return "power-balance-bar-insufficient";
    },
  },
  onLoad(options) {
    this.pageEntrySource = options?.entrySource || "APP";
  },
  onShow() {
    reportMtH5PageView("HOME", this.pageEntrySource);
    this.showActivityPopupOnce();
    this.loadDashboard();
  },
  methods: {
    showActivityPopupOnce() {
      if (!ACTIVITY_POPUP_ENABLED || activityPopupShownInCurrentApp) {
        return;
      }
      activityPopupShownInCurrentApp = true;
      this.activityPopupVisible = true;
    },
    closeActivityPopup() {
      this.activityPopupVisible = false;
    },
    openActivityPage() {
      this.closeActivityPopup();
      reportMtH5BehaviorEvent("H5_EXTERNAL_LINK_CLICK", {
        targetCode: "RENEWAL_ACTIVITY",
        entrySource: "ACTIVITY_POPUP",
      });
      openMtExternalPage(RENEWAL_PAGE_URL);
    },
    noop() {},
    async loadDashboard() {
      try {
        const dashboard = await getMtH5HomeDashboard(this.statsRange);
        this.applyDashboardData(dashboard);
      } catch (error) {
        const shopInfo = getShopInfo();
        this.shopName = shopInfo.nickName || shopInfo.userName || "美团门店";
        this.shopPoiId = shopInfo.poiId || "--";
        uni.showToast({
          title: error?.message || "首页信息加载失败",
          icon: "none",
        });
      }
    },
    applyDashboardData(dashboard = {}) {
      const shop = dashboard.shop || {};
      this.shopName = shop.shopName || "美团门店";
      this.shopPoiId = shop.poiId || "--";
      this.membership = {
        active: Boolean(dashboard.membership?.active),
        expireAt: dashboard.membership?.expireAt || null,
      };
      this.reauthRequired = Boolean(dashboard.authorization?.reauthRequired);
      const operationSettings = dashboard.operationSettings || {};
      this.autoReplyEnabled = operationSettings.autoReplyEnabled !== false;
      this.applyBroadcastSetting(dashboard.broadcastSetting);
      this.messageStats = {
        ...this.messageStats,
        ...(dashboard.messageStats || {}),
      };
      this.hasPowerStats = Boolean(dashboard.powerStats);
      if (this.hasPowerStats) {
        this.powerStats = {
          ...this.powerStats,
          ...(dashboard.powerStats || {}),
        };
      } else {
        this.statsMode = "messages";
      }
    },
    handleStatsRangeChange(event) {
      const index = Number(event?.detail?.value || 0);
      const selectedRange = this.statsRangeOptions[index];
      if (!selectedRange || selectedRange.value === this.statsRange) {
        return;
      }
      this.statsRange = selectedRange.value;
      this.loadDashboard();
    },
    toggleStatsMode() {
      if (!this.hasPowerStats) {
        return;
      }
      this.statsMode = this.statsMode === "messages" ? "power" : "messages";
    },
    async toggleAutoReply() {
      await this.updateOperationSettings({
        autoReplyEnabled: !this.autoReplyEnabled,
      });
    },
    async updateOperationSettings(nextSettings) {
      if (this.operationSettingsSubmitting) {
        return;
      }
      const previousAutoReplyEnabled = this.autoReplyEnabled;
      this.autoReplyEnabled = nextSettings.autoReplyEnabled;
      this.operationSettingsSubmitting = true;
      try {
        const savedSettings = await updateMtH5OperationSettings(nextSettings);
        this.autoReplyEnabled = savedSettings.autoReplyEnabled !== false;
        uni.showToast({ title: "已保存", icon: "none" });
      } catch (error) {
        this.autoReplyEnabled = previousAutoReplyEnabled;
        uni.showToast({
          title: error?.message || "保存失败",
          icon: "none",
        });
      } finally {
        this.operationSettingsSubmitting = false;
      }
    },
    applyBroadcastSetting(setting = {}) {
      const broadcastSetting = setting || {};
      this.broadcastConfigured = Boolean(broadcastSetting.configured);
      this.broadcastTotalSentCount = Number(
        broadcastSetting.totalSentCount || 0,
      );
      // 只有后端明确回 false 才置灰，字段缺失时按放行处理。
      this.broadcastRightGranted = broadcastSetting.rightGranted !== false;
      this.form = {
        weekDays: Array.isArray(broadcastSetting.weekDays)
          ? [...broadcastSetting.weekDays]
          : [],
        repeatMode: broadcastSetting.repeatMode || "EVERY",
        sendTime: broadcastSetting.sendTime || "10:00:00",
        content: broadcastSetting.content || "",
      };
    },
    openBroadcastDialog() {
      if (!this.broadcastRightGranted) {
        uni.showToast({
          title: "当前版本不支持消息群发，请升级后使用",
          icon: "none",
        });
        return;
      }
      this.closeBroadcastPanels();
      this.broadcastDialogVisible = true;
    },
    closeBroadcastDialog() {
      this.broadcastDialogVisible = false;
      this.closeBroadcastPanels();
    },
    /** 三个下拉面板互斥展开，避免弹窗被撑高后按钮被挤出可视区。 */
    closeBroadcastPanels() {
      this.weekDayPanelVisible = false;
      this.repeatModePanelVisible = false;
      this.timePanelVisible = false;
    },
    toggleWeekDayPanel() {
      const next = !this.weekDayPanelVisible;
      this.closeBroadcastPanels();
      this.weekDayPanelVisible = next;
    },
    toggleRepeatModePanel() {
      const next = !this.repeatModePanelVisible;
      this.closeBroadcastPanels();
      this.repeatModePanelVisible = next;
    },
    toggleTimePanel() {
      const next = !this.timePanelVisible;
      this.closeBroadcastPanels();
      this.timePanelVisible = next;
    },
    selectRepeatMode(mode) {
      this.form.repeatMode = mode;
      this.repeatModePanelVisible = false;
    },
    isWeekDaySelected(day) {
      return this.form.weekDays.includes(day);
    },
    toggleWeekDay(day) {
      const selected = this.form.weekDays.includes(day)
        ? this.form.weekDays.filter((item) => item !== day)
        : [...this.form.weekDays, day];
      this.form.weekDays = selected.sort((left, right) => left - right);
    },
    handleSendTimeChange(event) {
      const [hour = 0, minute = 0, second = 0] = event?.detail?.value || [];
      this.form.sendTime = `${padTwoDigits(hour)}:${padTwoDigits(
        minute,
      )}:${padTwoDigits(second)}`;
    },
    async submitBroadcastSetting() {
      if (this.broadcastSubmitting) {
        return;
      }
      if (!this.form.weekDays.length) {
        uni.showToast({ title: "请至少选择一个发送日期", icon: "none" });
        return;
      }
      if (!String(this.form.content).trim()) {
        uni.showToast({ title: "请填写发送的内容", icon: "none" });
        return;
      }
      this.broadcastSubmitting = true;
      try {
        const saved = await saveMtH5BroadcastSetting({
          weekDays: this.form.weekDays,
          repeatMode: this.form.repeatMode,
          sendTime: this.form.sendTime,
          content: String(this.form.content).trim(),
        });
        this.applyBroadcastSetting(saved);
        this.closeBroadcastDialog();
        uni.showToast({ title: "已保存", icon: "none" });
      } catch (error) {
        uni.showToast({ title: error?.message || "保存失败", icon: "none" });
      } finally {
        this.broadcastSubmitting = false;
      }
    },
    async deleteBroadcastSetting() {
      if (this.broadcastSubmitting) {
        return;
      }
      this.broadcastSubmitting = true;
      try {
        const cleared = await deleteMtH5BroadcastSetting();
        this.applyBroadcastSetting(cleared);
        this.closeBroadcastDialog();
        uni.showToast({ title: "已删除定时群发", icon: "none" });
      } catch (error) {
        uni.showToast({ title: error?.message || "删除失败", icon: "none" });
      } finally {
        this.broadcastSubmitting = false;
      }
    },
    goToLogPage() {
      uni.redirectTo({
        url: "/pages/reply-logs/index?entrySource=HOME_CARD",
      });
    },
    goToKnowledgePage() {
      uni.navigateTo({
        url: "/pages/knowledge/index?entrySource=HOME_CARD",
      });
    },
    openSupportPrompt(entrySource = "HOME") {
      reportMtH5BehaviorEvent("H5_SUPPORT_ENTRY_CLICK", {
        entrySource,
      });
      this.supportPromptVisible = true;
    },
    closeSupportPrompt() {
      this.supportPromptVisible = false;
    },
    openRenewalPage() {
      reportMtH5BehaviorEvent("H5_EXTERNAL_LINK_CLICK", {
        targetCode: "RENEWAL",
        entrySource: "VIP_CARD",
      });
      openMtExternalPage(RENEWAL_PAGE_URL);
    },
  },
};
</script>

<style scoped>
.page {
  min-height: 100vh;
  position: relative;
  color: #252525;
  background: linear-gradient(
    180deg,
    #efba4f 0%,
    #f7d581 160rpx,
    #f6f6f8 320rpx,
    #f6f6f8 100%
  );
}

.page-shell {
  padding: 0 0 calc(env(safe-area-inset-bottom) + 176rpx);
}

.header {
  padding: 0;
}

.shop-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30rpx;
  margin-bottom: 18rpx;
  margin-top: 18rpx;
}

.shop-info {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.shop-icon {
  width: 26rpx;
  height: 26rpx;
  margin-right: 8rpx;
  flex-shrink: 0;
}

.shop-name {
  flex: 1;
  min-width: 0;
  font-size: 28rpx;
  font-weight: 600;
  color: #2f2414;
}

.shop-link {
  font-size: 24rpx;
  color: #6a5531;
  text-decoration: underline;
}

.vip-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 20rpx;
  padding: 24rpx 28rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #2f2f32 0%, #17181b 100%);
  box-shadow: 0 18rpx 40rpx rgba(108, 73, 8, 0.18);
}

.vip-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #f7e4b3;
  margin-bottom: 8rpx;
}

.vip-date {
  display: block;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.75);
}

.vip-button {
  min-width: 136rpx;
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, #f5e3b6 0%, #debe82 100%);
  color: #5b441b;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 56rpx;
  text-align: center;
  box-sizing: border-box;
}

.content {
  margin-top: 20rpx;
  padding: 0 20rpx;
}

.section-card {
  margin-bottom: 18rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 30rpx rgba(31, 35, 41, 0.05);
}

.stats-card {
  overflow: hidden;
}

.stats-block {
  padding: 22rpx;
}

.stats-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 18rpx;
}

.stats-range-picker {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  background: #f3efe8;
}

.stats-range-picker-text {
  font-size: 22rpx;
  color: #6a655d;
  font-weight: 600;
}

.stats-range-picker-caret {
  font-size: 18rpx;
  color: #8b847a;
}

.stats-text-switch {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
  min-width: 214rpx;
  height: 44rpx;
  flex-shrink: 0;
}

.stats-text-switch-item {
  font-size: 24rpx;
  color: #2b2b2f;
  line-height: 1.2;
  text-decoration: underline;
  white-space: nowrap;
}

.stats-text-switch-active {
  color: #9b7a28;
  font-weight: 700;
}

.stats-text-switch-divider {
  font-size: 22rpx;
  color: #a7a7a7;
}

.stats-grid {
  display: flex;
  gap: 16rpx;
}

.stat-card {
  flex: 1;
  padding: 26rpx 14rpx 20rpx;
  border-radius: 22rpx;
  text-align: center;
  box-sizing: border-box;
}

.stat-card-warm {
  background: linear-gradient(180deg, #f6f1df 0%, #f4f2e8 100%);
}

.stat-card-cool {
  background: linear-gradient(180deg, #ecf1f8 0%, #edf3f8 100%);
}

.stat-card-pink {
  background: linear-gradient(180deg, #f7ecef 0%, #f9eef1 100%);
}

.stat-label {
  display: block;
  min-height: 68rpx;
  font-size: 22rpx;
  line-height: 1.45;
  color: #5d5d5d;
}

.stat-value {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 2rpx 0 18rpx;
  height: 50rpx;
  font-size: 50rpx;
  line-height: 1;
  font-weight: 700;
}

.stat-value-compact {
  font-size: 42rpx;
  white-space: nowrap;
}

.stat-value-dark {
  color: #2f2f33;
}

.stat-foot {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 6rpx;
  font-size: 20rpx;
}

.stat-foot-label {
  color: #9d9d9d;
}

.stat-foot-value {
  font-weight: 600;
}

.trend-red {
  color: #ec6b6a;
}

.trend-green {
  color: #65bb7f;
}

.trend-muted {
  color: #8e8e8e;
}

.feature-card {
  padding: 24rpx 26rpx;
}

.ai-card {
  padding: 28rpx 28rpx;
  overflow: hidden;
}

.ai-card .feature-head {
  margin-bottom: 30rpx;
}

.ai-card .feature-body {
  align-items: center;
  min-height: 118rpx;
}

.feature-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 22rpx;
}

.feature-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #2b2b2f;
}

.llm-title-wrap {
  display: flex;
  align-items: center;
  gap: 6rpx;
  min-width: 0;
  flex: 1;
}

.llm-logo {
  width: 34rpx;
}

.llm-title {
  font-size: 24rpx;
  color: #2b2b2f;
  text-decoration: underline;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feature-body {
  display: flex;
  align-items: flex-start;
}

.feature-body-center {
  align-items: center;
}

.feature-icon {
  width: 64rpx;
  height: 64rpx;
  margin-right: 18rpx;
  margin-top: 6rpx;
  flex-shrink: 0;
}

.robot-icon {
  width: 74rpx;
  height: 74rpx;
  margin-top: 0;
}

.ai-card .robot-icon {
  width: 100rpx;
  height: 100rpx;
  margin-right: 22rpx;
}

.robot-icon-wrap {
  position: relative;
  display: block;
  overflow: visible;
  transform-origin: 50% 82%;
}

.robot-icon-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  display: block;
}

.feature-copy {
  flex: 1;
  min-width: 0;
}

.feature-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.ai-main-copy {
  min-width: 0;
}

.ai-main-copy .feature-name {
  font-size: 38rpx;
  line-height: 1.2;
}

.feature-name {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #2f2f33;
}

.feature-desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #f0af59;
}

.feature-desc-inline {
  margin-top: 10rpx;
}

.ai-main-copy .feature-desc {
  margin-top: 12rpx;
  font-size: 27rpx;
  line-height: 1.25;
  color: #d7a34a;
  font-weight: 700;
}

.log-entry-card {
  padding-top: 28rpx;
  padding-bottom: 28rpx;
}

.log-entry-head {
  margin-bottom: 14rpx;
}

.log-entry-desc {
  margin-top: 10rpx;
  color: #8d8d94;
}

.feature-head-status {
  flex-shrink: 0;
  font-size: 24rpx;
  font-weight: 700;
  color: #64cd93;
}

.feature-head-status-off {
  color: #b8b8b8;
}

.ai-head-status {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #2f2f33;
}

.ai-head-status.feature-head-status-off {
  color: #4f4f4f;
}

.feature-head-status-icon {
  width: 26rpx;
  height: 26rpx;
}

.toggle {
  width: 164rpx;
  height: 68rpx;
  border-radius: 999rpx;
  position: relative;
  flex-shrink: 0;
  box-sizing: border-box;
  transition: background-color 0.2s ease;
}

.ai-card .toggle {
  width: 172rpx;
  height: 72rpx;
}

.toggle-on {
  background: #efcb3f;
}

.toggle-off {
  background: #d9dbe2;
}

.toggle-off::after {
  content: "";
  position: absolute;
  inset: 0;
  border: 3rpx solid transparent;
  border-radius: inherit;
  box-sizing: border-box;
  pointer-events: none;
  animation: autoReplyToggleGlow 1.5s ease-in-out infinite;
}

.toggle-disabled {
  opacity: 0.6;
}

.toggle-disabled::after {
  animation: none;
}

.toggle-dot {
  position: absolute;
  top: 4rpx;
  z-index: 1;
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.12);
  transition:
    left 0.2s ease,
    transform 0.2s ease;
}

.ai-card .toggle-dot {
  width: 64rpx;
  height: 64rpx;
}

.ai-card .toggle-on .toggle-dot {
  left: calc(100% - 68rpx);
}

.toggle-on .toggle-dot {
  left: calc(100% - 64rpx);
}

.toggle-off .toggle-dot {
  left: 4rpx;
}

@keyframes autoReplyToggleGlow {
  0%,
  100% {
    border-color: rgba(239, 203, 63, 0);
    box-shadow: 0 0 0 0 rgba(239, 203, 63, 0);
  }

  50% {
    border-color: #efc548;
    box-shadow: 0 0 18rpx 4rpx rgba(239, 203, 63, 0.42);
  }
}

.power-balance-bar {
  margin: 26rpx -8rpx 0;
  height: 24rpx;
  border-radius: 999rpx;
  overflow: hidden;
  background: #eef0f4;
}

.power-balance-fill {
  display: flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  height: 100%;
  min-width: 132rpx;
  border-radius: inherit;
  transition: width 0.2s ease;
  box-sizing: border-box;
}

.power-balance-text {
  color: #ffffff;
  font-size: 16rpx;
  line-height: 1;
  font-weight: 800;
  white-space: nowrap;
}

.power-balance-bar-sufficient .power-balance-fill {
  background: #2bd946;
}

.power-balance-bar-tight .power-balance-fill {
  background: #f0b400;
}

.power-balance-bar-insufficient .power-balance-fill {
  background: #ef2d2d;
}

.promotion-warning {
  margin-top: 20rpx;
  padding: 18rpx 22rpx;
  border-radius: 22rpx;
  background: rgba(209, 67, 51, 0.1);
}

.promotion-warning-text {
  font-size: 22rpx;
  line-height: 1.6;
  color: #a13a2c;
}

.outline-button {
  width: 110rpx;
  height: 50rpx;
  padding: 0;
  border: 2rpx solid #efc548;
  border-radius: 999rpx;
  background: #efc548;
  color: #4a3a1c;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 46rpx;
  text-align: center;
  box-sizing: border-box;
  white-space: nowrap;
}

.feedback-link {
  padding: 20rpx 0 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: #b2b2b2;
  text-decoration: underline;
}

.modal-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx;
  background: rgba(0, 0, 0, 0.45);
  box-sizing: border-box;
}

.activity-popup-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(env(safe-area-inset-top) + 20rpx) 24rpx
    calc(env(safe-area-inset-bottom) + 20rpx);
  background: rgba(0, 0, 0, 0.56);
  box-sizing: border-box;
}

.activity-popup-dialog {
  position: relative;
  width: calc(56.25vh - 48rpx);
  max-width: calc(100vw - 48rpx);
  max-height: calc(
    100vh - env(safe-area-inset-top) - env(safe-area-inset-bottom) - 40rpx
  );
}

.activity-popup-image {
  display: block;
  width: 100%;
  height: auto;
  pointer-events: none;
}

.activity-popup-close-zone {
  position: absolute;
  top: 7%;
  right: 4%;
  width: 14%;
  aspect-ratio: 1;
  z-index: 1;
}

.activity-popup-action-zone {
  position: absolute;
  right: 20%;
  bottom: 14%;
  left: 20%;
  height: 10%;
  z-index: 1;
}

.support-modal {
  width: 100%;
  max-width: 620rpx;
  padding: 42rpx 32rpx 34rpx;
  border-radius: 28rpx;
  background: #f7e6a2;
  box-sizing: border-box;
  text-align: center;
}

.support-modal-title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  line-height: 1.35;
  color: #2b220f;
}

.support-modal-qrcode-frame {
  margin: 38rpx auto 0;
  display: inline-block;
  padding: 18rpx;
  border-radius: 18rpx;
  background: #e2b62a;
}

.support-modal-qrcode {
  width: 320rpx;
  height: 320rpx;
  display: block;
  background: #ffffff;
}

.support-modal-subtitle {
  display: block;
  margin-top: 22rpx;
  font-size: 28rpx;
  color: #4d3d19;
}

.support-modal-footer {
  display: block;
  margin-top: 44rpx;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.4;
  color: #231c0d;
}

.support-modal-button {
  margin-top: 34rpx;
  height: 84rpx;
  border-radius: 18rpx;
  background: #f2ce46;
  color: #2a2416;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 84rpx;
  text-align: center;
}

/* 消息群发入口：负边距抵消 ai-card 的内边距，让行尾角标贴齐卡片边缘 */
.broadcast-entry {
  position: relative;
  display: flex;
  align-items: center;
  margin: 24rpx -28rpx -28rpx;
  padding: 24rpx 28rpx;
  overflow: hidden;
}

/* 置灰但不隐藏：入口仍可点，点击后提示升级，让商家知道有这个能力。 */
.broadcast-entry-disabled {
  opacity: 0.6;
}

.broadcast-entry-copy {
  flex: 1;
  display: flex;
  align-items: baseline;
  min-width: 0;
}

.broadcast-entry-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2329;
}

.broadcast-entry-count {
  font-size: 26rpx;
  color: #8a9099;
}

.broadcast-entry-icon {
  width: 56rpx;
  height: 56rpx;
  margin-right: 36rpx;
}

.broadcast-entry-badge {
  position: absolute;
  top: 0;
  right: 0;
  width: 72rpx;
  height: 72rpx;
}

.broadcast-modal {
  width: 100%;
  max-width: 620rpx;
  /* 展开时间面板后弹窗会变高，限高加滚动，保证底部按钮始终够得着 */
  max-height: 86vh;
  overflow-y: auto;
  padding: 36rpx 32rpx 28rpx;
  border-radius: 20rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.broadcast-modal-title {
  display: block;
  margin-bottom: 32rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #1f2329;
  text-align: center;
  text-decoration: underline;
}

.broadcast-field {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.broadcast-field-top {
  align-items: flex-start;
}

.broadcast-field-label {
  width: 96rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: #1f2329;
  text-decoration: underline;
}

.broadcast-select {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  min-width: 168rpx;
  padding: 12rpx 16rpx;
  border: 2rpx solid #d5d8dd;
  border-radius: 8rpx;
  box-sizing: border-box;
}

.broadcast-select-grow {
  flex: 1;
  min-width: 0;
}

.broadcast-select-text {
  flex: 1;
  font-size: 26rpx;
  color: #1f2329;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.broadcast-select-arrow {
  font-size: 20rpx;
  color: #8a9099;
}

/* 频率与时间都改成弹窗内联面板：uni-app 原生 picker 的弹层挂在页面根节点，
   层级低于自定义蒙层，会被弹窗盖住，只能内联展开。 */
.broadcast-option-panel {
  display: flex;
  gap: 16rpx;
  margin: -8rpx 0 24rpx 112rpx;
  padding: 16rpx 20rpx;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  background: #fafbfc;
}

.broadcast-option-item {
  padding: 10rpx 28rpx;
  border: 2rpx solid #d5d8dd;
  border-radius: 8rpx;
  background: #ffffff;
  font-size: 26rpx;
  color: #1f2329;
}

.broadcast-option-item-active {
  border-color: #f5c73c;
  background: #fdf4d6;
}

.broadcast-time-panel {
  margin: -8rpx 0 24rpx 112rpx;
  padding: 12rpx 20rpx;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  background: #fafbfc;
}

/* 用 px 而非 rpx：picker-view 默认指示器固定 34px，rpx 换算随屏宽浮动会对不齐 */
.broadcast-time-picker {
  width: 100%;
  height: 170px;
}

.broadcast-time-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  font-size: 28rpx;
  color: #1f2329;
}

.broadcast-weekday-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx 20rpx;
  margin: -8rpx 0 24rpx 112rpx;
  padding: 20rpx;
  border: 2rpx solid #e5e7eb;
  border-radius: 12rpx;
  background: #fafbfc;
}

.broadcast-weekday-item {
  display: flex;
  align-items: center;
  gap: 10rpx;
  width: 152rpx;
}

.broadcast-weekday-checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid #c9ced6;
  border-radius: 6rpx;
  background: #ffffff;
  font-size: 22rpx;
  color: #ffffff;
}

.broadcast-weekday-checkbox-checked {
  border-color: #f5c73c;
  background: #f5c73c;
}

.broadcast-weekday-label {
  font-size: 26rpx;
  color: #1f2329;
}

.broadcast-textarea-wrap {
  flex: 1;
  min-width: 0;
  padding: 16rpx;
  border: 2rpx solid #d5d8dd;
  border-radius: 8rpx;
  box-sizing: border-box;
}

.broadcast-textarea {
  width: 100%;
  height: 132rpx;
  font-size: 26rpx;
  color: #1f2329;
  box-sizing: border-box;
}

.broadcast-textarea-placeholder {
  color: #b6bbc3;
}

.broadcast-textarea-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 8rpx;
}

.broadcast-textarea-rule {
  font-size: 22rpx;
  color: #8a9099;
}

.broadcast-textarea-counter {
  font-size: 22rpx;
  color: #b6bbc3;
}

.broadcast-modal-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32rpx;
  margin-top: 32rpx;
}

.broadcast-modal-button {
  min-width: 128rpx;
  padding: 0 32rpx;
  border-radius: 36rpx;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 68rpx;
  color: #5a6069;
  text-align: center;
}

.broadcast-modal-button-primary {
  background: #f5c73c;
  color: #1f2329;
}

.broadcast-modal-button-disabled {
  opacity: 0.6;
}
</style>
