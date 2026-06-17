<template>
  <view class="page">
    <view
      class="page-shell"
      :class="{ 'page-shell-with-recharge': homeRechargeEntryVisible }"
    >
      <view class="header">
        <app-top-notice :offer="firstRechargeOffer" safe-top />

        <view class="shop-row">
          <view class="shop-info">
            <image
              class="shop-icon"
              src="/static/icons/shop.png"
              mode="aspectFit"
            ></image>
            <text class="shop-name">{{ shopDisplayName }}</text>
          </view>
          <text class="shop-link" @tap="openSupportPrompt">使用指导 ></text>
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
        <view
          class="section-card feature-card ai-card guide-target-ai"
          :class="{ 'home-guide-active-target': isGuideStep('ai') }"
          @tap="handleGuideTargetTap('ai')"
        >
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
              :class="{ 'feature-head-status-off': !aiPromotionEnabled }"
            >
              <image
                class="feature-head-status-icon"
                :src="
                  aiPromotionEnabled
                    ? '/static/icons/open.svg'
                    : '/static/icons/close.svg'
                "
                mode="aspectFit"
              ></image>
              <text>{{ aiPromotionEnabled ? "开启中" : "已关闭" }}</text>
            </view>
          </view>

          <view class="feature-body">
            <view
              class="feature-icon robot-icon robot-icon-wrap"
              :class="{ 'robot-icon-bounce': !aiPromotionEnabled }"
            >
              <image
                class="robot-icon-image"
                :src="aiPromotionRobotStaticSrc"
                mode="aspectFit"
              ></image>
            </view>
            <view class="feature-copy">
              <view class="feature-row">
                <view class="ai-main-copy">
                  <text class="feature-name">一键开启AI推广</text>
                  <text class="feature-desc">AI识别高峰期/点金自动出价</text>
                </view>
                <view
                  class="toggle"
                  :class="{
                    'toggle-on': aiPromotionEnabled,
                    'toggle-off': !aiPromotionEnabled,
                    'toggle-disabled': isToggleBusy,
                  }"
                  @tap="handleToggle('aiPromotionEnabled')"
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

          <view class="current-bid-main ai-current-bid-main">
            <view class="current-bid-title-wrap">
              <text class="current-bid-title">当前AI出价</text>
              <text
                v-if="currentBid.previousBidAmount !== null"
                class="current-bid-subtitle"
                >（上轮出价{{
                  formatBidAmount(currentBid.previousBidAmount)
                }}元）</text
              >
            </view>
            <view
              v-if="currentBid.inPromotionWindow"
              class="current-bid-value-wrap"
            >
              <text class="current-bid-value" :class="currentBidStatusClass">{{
                currentBidDisplayText
              }}</text>
              <image
                v-if="currentBid.editable"
                class="current-bid-edit"
                src="/static/icons/edit.svg"
                mode="aspectFit"
                @tap="openBidModal"
              ></image>
            </view>
            <text v-else class="current-bid-disabled">非推广时段</text>
          </view>
          <view v-if="promotionDisabledReasonText" class="promotion-warning">
            <text class="promotion-warning-text">{{
              promotionDisabledReasonText
            }}</text>
          </view>
        </view>

        <view
          class="section-card feature-card guide-target-strategy"
          :class="{ 'home-guide-active-target': isGuideStep('strategy') }"
          @tap="handleGuideTargetTap('strategy')"
        >
          <view class="feature-head">
            <text class="feature-title">推广策略</text>
            <text
              class="feature-head-status"
              :class="{
                'feature-head-status-off':
                  !strategyConfigured || !strategyEntryVisible,
              }"
            >
              {{ strategyStatusText }}
            </text>
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
                  <text class="feature-name">让AI根据策略推广</text>
                  <text class="feature-desc feature-desc-inline">{{
                    strategyDescription
                  }}</text>
                </view>
                <view
                  class="outline-button"
                  :class="{ 'outline-button-disabled': !strategyEntryVisible }"
                  @tap="handleStrategyClick"
                  >{{ strategyButtonText }}</view
                >
              </view>
            </view>
          </view>
        </view>

        <view
          class="section-card feature-card log-entry-card guide-target-log"
          :class="{ 'home-guide-active-target': isGuideStep('log') }"
          @tap="handleGuideTargetTap('log')"
        >
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
                  <text class="feature-name">查看AI操作记录</text>
                  <text class="feature-desc log-entry-desc"
                    >实时查看AI出价策略</text
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
                    'stats-text-switch-active': statsMode === 'marketing',
                  }"
                  >效果统计</text
                >
                <text class="stats-text-switch-divider">|</text>
                <text
                  class="stats-text-switch-item"
                  :class="{
                    'stats-text-switch-active': statsMode === 'power',
                  }"
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

        <view class="feedback-link" @tap="openSupportPrompt">建议与反馈</view>
      </view>
    </view>

    <view
      v-if="homeRechargeEntryVisible"
      class="home-recharge-entry"
      @tap="goToPowerRecharge"
    >
      <image
        class="home-recharge-image"
        src="/static/images/home_recharge.gif"
        mode="widthFix"
      ></image>
    </view>

    <app-tabbar active-tab="home" />

    <view
      v-if="promotionGuidePopupVisible"
      class="home-onboarding-mask"
      @tap.stop="dismissPromotionGuidePopup"
      @touchmove.stop.prevent="noop"
    >
      <view
        class="home-onboarding-dialog home-promotion-guide-dialog"
        @tap.stop="openPromotionGuideStrategyModal"
      >
        <image
          class="home-onboarding-image"
          src="/static/images/quick_toast.png"
          mode="widthFix"
        ></image>
        <image
          class="home-promotion-guide-button"
          src="/static/images/quick_toast_button.png"
          mode="widthFix"
        ></image>
        <view
          class="home-onboarding-close-zone"
          @tap.stop="dismissPromotionGuidePopup"
        ></view>
      </view>
    </view>

    <view
      v-if="rechargeGuidePopupVisible"
      class="home-onboarding-mask"
      @tap.stop="dismissRechargeGuidePopup"
      @touchmove.stop.prevent="noop"
    >
      <view
        class="home-onboarding-dialog home-recharge-guide-dialog"
        @tap.stop="openRechargeGuidePage"
      >
        <image
          class="home-onboarding-image"
          src="/static/images/home_recharge.png"
          mode="widthFix"
        ></image>
        <view
          class="home-onboarding-close-zone"
          @tap.stop="dismissRechargeGuidePopup"
        ></view>
      </view>
    </view>

    <view
      v-if="guideVisible"
      class="home-guide-mask"
      @tap.stop.prevent="advanceGuide"
      @touchmove.stop.prevent="noop"
    ></view>
    <view
      v-if="guideVisible"
      class="home-guide-copy"
      :class="currentGuideCopyClass"
      :style="guideCopyStyle"
      @tap.stop.prevent="advanceGuide"
      @touchmove.stop.prevent="noop"
    >
      <text class="home-guide-title">{{ currentGuideTitle }}</text>
      <text class="home-guide-desc">{{ currentGuideDesc }}</text>
      <text class="home-guide-next">{{ currentGuideNextText }}</text>
    </view>

    <view
      v-if="strategyModalVisible"
      class="modal-mask"
      @tap="closeStrategyModal(true)"
    >
      <view class="strategy-modal" @tap.stop="hidePeakWindowTips">
        <view class="strategy-modal-help" @tap="openSupportPrompt">?</view>
        <view class="strategy-modal-body">
          <view class="strategy-section">
            <text class="strategy-section-title">推广策略：</text>
            <view class="strategy-option-grid">
              <view
                class="strategy-select-card strategy-half-card strategy-simple-card"
                :class="{
                  'strategy-select-card-active':
                    strategyForm.priority === 'rate',
                }"
                @tap="selectStrategyPriority('rate')"
              >
                <view class="strategy-recommend-title-wrap">
                  <text class="strategy-select-title">省钱模式</text>
                  <image
                    class="strategy-recommend-icon"
                    src="/static/icons/rec.svg"
                    mode="aspectFit"
                  ></image>
                </view>
                <radio
                  class="strategy-radio"
                  color="#efc548"
                  :checked="strategyForm.priority === 'rate'"
                />
              </view>
              <view
                class="strategy-select-card strategy-half-card strategy-simple-card"
                :class="{
                  'strategy-select-card-active':
                    strategyForm.priority === 'exposure',
                }"
                @tap="selectStrategyPriority('exposure')"
              >
                <text class="strategy-select-title">曝光优先</text>
                <radio
                  class="strategy-radio"
                  color="#efc548"
                  :checked="strategyForm.priority === 'exposure'"
                />
              </view>
            </view>
          </view>

          <view class="strategy-section">
            <text class="strategy-section-title">推广费率：</text>
            <view
              class="strategy-select-card strategy-rate-card"
              :class="{
                'strategy-select-card-active':
                  strategyForm.rateMode === 'control',
                'strategy-select-card-disabled': isExposurePriority,
              }"
              @tap="handleRateCardTap"
            >
              <view class="strategy-custom-head">
                <text class="strategy-select-title"
                  >费率控制<text class="strategy-rate-explanation"
                    >（*费率：推广花费占收入比）</text
                  ></text
                >
                <radio
                  class="strategy-radio"
                  color="#efc548"
                  :checked="strategyForm.rateMode === 'control'"
                />
              </view>
              <view
                v-if="isExposurePriority"
                class="strategy-rate-range strategy-rate-range-readonly"
              >
                <view
                  class="strategy-time-pill strategy-rate-pill strategy-rate-pill-disabled"
                >
                  <text>1%</text>
                </view>
                <text class="strategy-slot-dash">-</text>
                <view
                  class="strategy-time-pill strategy-rate-pill strategy-rate-pill-disabled"
                >
                  <text>20%</text>
                </view>
                <text class="strategy-rate-readonly-tip"
                  >曝光优先下费率固定展示，不可编辑</text
                >
              </view>
              <view v-else class="strategy-rate-range" @tap.stop>
                <picker
                  mode="selector"
                  :range="rateOptions"
                  :value="getRateOptionIndex(strategyForm.rateMin)"
                  @change="handleRateChange('rateMin', $event)"
                >
                  <view class="strategy-time-pill strategy-rate-pill">
                    <text>{{ strategyForm.rateMin }}</text>
                    <text class="strategy-time-caret">▼</text>
                  </view>
                </picker>
                <text class="strategy-slot-dash">-</text>
                <picker
                  mode="selector"
                  :range="rateOptions"
                  :value="getRateOptionIndex(strategyForm.rateMax)"
                  @change="handleRateChange('rateMax', $event)"
                >
                  <view class="strategy-time-pill strategy-rate-pill">
                    <text>{{ strategyForm.rateMax }}</text>
                    <text class="strategy-time-caret">▼</text>
                  </view>
                </picker>
              </view>
            </view>
          </view>

          <view class="strategy-section">
            <view class="strategy-section-title-row">
              <text class="strategy-section-title">推广时段：</text>
              <view
                v-if="autoTimeModeAvailable"
                class="strategy-peak-button"
                @tap.stop="showAutoTimeModePeakWindows"
              >
                <text>AI 识别高峰期：</text>
                <text class="strategy-unlock-underline">点击查看</text>
              </view>
              <view
                v-else
                class="strategy-peak-button strategy-peak-button-locked"
                :class="{
                  'strategy-peak-button-disabled': unlockingAutoTimeMode,
                }"
                @tap.stop="unlockAutoTimeMode"
              >
                <text v-if="unlockingAutoTimeMode">识别中...</text>
                <text v-else>
                  <text>AI 识别高峰期：</text>
                  <text class="strategy-unlock-underline">点击查看</text>
                </text>
              </view>
              <view
                v-if="peakWindowTipsVisible"
                class="peak-window-tips"
                @tap.stop
              >
                <view class="peak-window-tips-arrow"></view>
                <text class="peak-window-tips-title">高峰状态</text>
                <view class="peak-window-tips-row">
                  <text class="peak-window-tips-label">当前状态</text>
                  <text class="peak-window-tips-value">{{
                    peakMatchedText
                  }}</text>
                </view>
                <view class="peak-window-tips-row">
                  <text class="peak-window-tips-label">当前日型</text>
                  <text class="peak-window-tips-value">{{
                    autoTimeModeDayTypeText
                  }}</text>
                </view>
                <view class="peak-window-tips-row peak-window-tips-row-top">
                  <text class="peak-window-tips-label">高峰时段</text>
                  <view class="peak-window-tips-times">
                    <text
                      v-for="windowText in displayedPeakWindowTips"
                      :key="windowText"
                      class="peak-window-tips-time"
                      >{{ windowText }}</text
                    >
                  </view>
                </view>
              </view>
            </view>
            <view
              class="strategy-select-card strategy-simple-card"
              :class="{
                'strategy-select-card-active': strategyForm.timeMode === 'auto',
                'strategy-select-card-disabled': !autoTimeModeAvailable,
              }"
              @tap="selectTimeMode('auto')"
            >
              <view class="strategy-time-mode-copy">
                <text class="strategy-select-title">
                  让AI自动管理
                  <text class="strategy-select-note"
                    >（{{ autoTimeModeHintText }}）</text
                  >
                </text>
              </view>
              <radio
                v-if="autoTimeModeAvailable"
                class="strategy-radio"
                color="#efc548"
                :checked="strategyForm.timeMode === 'auto'"
              />
              <image
                v-else
                class="strategy-lock-icon"
                src="/static/icons/lock.svg"
                mode="aspectFit"
              ></image>
            </view>
            <view
              class="strategy-select-card strategy-simple-card"
              :class="{
                'strategy-select-card-active':
                  strategyForm.timeMode === 'business',
              }"
              @tap="selectTimeMode('business')"
            >
              <text class="strategy-select-title">按营业时长</text>
              <radio
                class="strategy-radio"
                color="#efc548"
                :checked="strategyForm.timeMode === 'business'"
              />
            </view>
            <view
              class="strategy-select-card strategy-custom-card"
              :class="{
                'strategy-select-card-active':
                  strategyForm.timeMode === 'custom',
              }"
              @tap="selectTimeMode('custom')"
            >
              <view class="strategy-custom-head">
                <text class="strategy-select-title">自定义时段</text>
                <radio
                  class="strategy-radio"
                  color="#efc548"
                  :checked="strategyForm.timeMode === 'custom'"
                />
              </view>
              <view
                v-if="strategyForm.timeMode === 'custom'"
                class="strategy-slot-list"
                @tap.stop
              >
                <view
                  v-for="(slot, index) in strategyForm.customSlots"
                  :key="`${slot.start}-${slot.end}-${index}`"
                  class="strategy-slot-row"
                >
                  <picker
                    mode="time"
                    :value="slot.start"
                    @change="handleCustomTimeChange(index, 'start', $event)"
                  >
                    <view class="strategy-time-pill">
                      <text>{{ slot.start }}</text>
                      <text class="strategy-time-caret">▼</text>
                    </view>
                  </picker>
                  <text class="strategy-slot-dash">-</text>
                  <picker
                    mode="time"
                    :value="slot.end"
                    @change="handleCustomTimeChange(index, 'end', $event)"
                  >
                    <view class="strategy-time-pill">
                      <text>{{ slot.end }}</text>
                      <text class="strategy-time-caret">▼</text>
                    </view>
                  </picker>
                  <view
                    class="strategy-remove"
                    @tap.stop="removeCustomSlot(index)"
                    >-</view
                  >
                </view>
                <view class="strategy-add-slot" @tap.stop="addCustomSlot"
                  >+新增时段</view
                >
              </view>
            </view>
          </view>

          <view class="strategy-section">
            <view class="strategy-section-title-row strategy-bid-title-row">
              <text class="strategy-section-title">AI 出价范围：</text>
              <text class="strategy-bid-title-value">
                {{ formatStrategyBidAmount(strategyForm.bidMin) }}-{{
                  formatStrategyBidAmount(strategyForm.bidMax)
                }}元
              </text>
            </view>
            <view class="strategy-bid-range-card">
              <view
                class="strategy-bid-range-axis"
                @touchstart.stop.prevent="startBidRangeDrag('', $event)"
                @touchmove.stop.prevent="moveBidRangeDrag"
                @touchend.stop.prevent="endBidRangeDrag"
                @touchcancel.stop.prevent="endBidRangeDrag"
              >
                <view class="strategy-bid-axis-track"></view>
                <view
                  class="strategy-bid-axis-active"
                  :style="strategyBidRangeTrackStyle"
                ></view>
                <view
                  class="strategy-bid-axis-thumb"
                  :class="{
                    'strategy-bid-axis-thumb-active':
                      activeBidRangeHandle === 'bidMin',
                  }"
                  :style="strategyBidMinThumbStyle"
                  @touchstart.stop.prevent="startBidRangeDrag('bidMin', $event)"
                  @touchmove.stop.prevent="moveBidRangeDrag"
                  @touchend.stop.prevent="endBidRangeDrag"
                  @touchcancel.stop.prevent="endBidRangeDrag"
                ></view>
                <view
                  class="strategy-bid-axis-thumb"
                  :class="{
                    'strategy-bid-axis-thumb-active':
                      activeBidRangeHandle === 'bidMax',
                  }"
                  :style="strategyBidMaxThumbStyle"
                  @touchstart.stop.prevent="startBidRangeDrag('bidMax', $event)"
                  @touchmove.stop.prevent="moveBidRangeDrag"
                  @touchend.stop.prevent="endBidRangeDrag"
                  @touchcancel.stop.prevent="endBidRangeDrag"
                ></view>
              </view>
              <view class="strategy-bid-range-labels">
                <text>{{ strategyBidMinLimit.toFixed(1) }}元</text>
                <text>{{ strategyBidMaxLimit.toFixed(1) }}元</text>
              </view>
            </view>
          </view>
        </view>

        <view class="strategy-actions">
          <view class="strategy-confirm" @tap="submitStrategyConfig">确认</view>
          <view class="strategy-cancel" @tap="closeStrategyModal(true)"
            >取消</view
          >
        </view>
      </view>
    </view>

    <view v-if="authFailurePromptVisible" class="modal-mask" @tap.stop>
      <view class="auth-failure-modal" @tap.stop>
        <view
          class="auth-failure-close"
          aria-label="关闭"
          @tap="closeAuthFailurePrompt"
          >×</view
        >
        <image
          class="auth-failure-icon"
          src="/static/icons/fail.svg"
          mode="aspectFit"
        ></image>
        <text class="auth-failure-title">授权失败，请重新授权！</text>
        <view class="auth-failure-button" @tap="openAuthFailureSupport"
          >联系客服处理</view
        >
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

    <view v-if="bidModalVisible" class="modal-mask" @tap="closeBidModal">
      <view class="bid-modal" @tap.stop>
        <text class="bid-modal-title">修改当前出价</text>
        <view class="bid-input-row">
          <input
            v-model="bidFormAmount"
            class="bid-input"
            type="digit"
            placeholder="0.1-4"
            placeholder-class="purchase-placeholder"
          />
          <text class="bid-input-unit">元/次</text>
        </view>
        <text class="bid-modal-tip"
          >修改后，当前窗口后续出价将以此为基础继续计算</text
        >
        <view class="bid-modal-actions">
          <view class="bid-modal-cancel" @tap="closeBidModal">取消</view>
          <view
            class="bid-modal-confirm"
            :class="{ 'bid-modal-confirm-disabled': submittingManualBid }"
            @tap="submitManualBid"
            >确认</view
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
  clearMtH5HomeGuideStep,
  completeMtH5Guide,
  dismissMtH5HomeGuide,
  getMtH5HomeDashboard,
  getMtH5PowerSummary,
  getMtH5PromotionSettings,
  getShopInfo,
  getVersion,
  reportMtH5BehaviorEvent,
  setMtH5HomeGuideStep,
  unlockMtH5AutoTimeMode,
  updateMtH5ManualPromotionBid,
  updateMtH5PromotionSettings,
} from "../../utils/mt-auth";
import { showErrorToast, showToast } from "../../utils/ui";

const MINUTES_PER_DAY = 24 * 60;
const RENEWAL_PAGE_URL =
  "https://waimaieapp.meituan.com/ad/mycraft/activity?MYCRAFT_PAGE_ID=2486&appId=138";
const HOME_GUIDE_CODE = "HOME_FIRST_AUTH_GUIDE_V2";
const HOME_RECHARGE_POWER_AMOUNT = 10000000;
const PROMOTION_ROBOT_STATIC_ICON = "/static/icons/promotion_robot.png";
const POWER_BALANCE_BAR_MAX = 10_000_000;
const POWER_BALANCE_SUFFICIENT_THRESHOLD = 6_000_000;
const POWER_BALANCE_TIGHT_THRESHOLD = 1_000_000;
const HOME_ONBOARDING_POPUP_SESSION_STATE = {
  promotionGuideShown: false,
  rechargeGuideShown: false,
};
const GUIDE_COPY_HEIGHT = 112;
const GUIDE_COPY_GAP = 14;
const GUIDE_VIEWPORT_TOP_SAFE = 28;
const GUIDE_VIEWPORT_BOTTOM_SAFE = 118;
const MT_AUTH_STATUS_REAUTH_REQUIRED = 2;
const MT_AUTH_REAUTH_TEXT_PATTERN =
  /AUTH_FAILURE|OP_UNIAUTH_FAILED|鉴权失败|授权失败|重新授权/;
const DEFAULT_BID_MIN = 0.1;
const DEFAULT_BID_MAX = 4;
const BID_STEP = 0.1;
const HOME_GUIDE_STEPS = [
  {
    key: "strategy",
    targetClass: "guide-target-strategy",
    copyClass: "home-guide-copy-strategy",
    title: "第一步：",
    desc: "配置AI推广策略。",
    nextText: "下一步 >>>",
  },
  {
    key: "ai",
    targetClass: "guide-target-ai",
    copyClass: "home-guide-copy-ai",
    title: "第二步：",
    desc: "开启按钮，AI即可自动出价。",
    nextText: "下一步 >>>",
  },
  {
    key: "log",
    targetClass: "guide-target-log",
    copyClass: "home-guide-copy-log",
    title: "查看日志：",
    desc: "查看AI出价记录。高峰期状态。",
    nextText: "完成 >>>",
  },
];

function buildTimeOptions() {
  return Array.from({ length: MINUTES_PER_DAY }, (_, minute) =>
    formatMinute(minute),
  );
}

function toMinute(value) {
  if (!value || typeof value !== "string") {
    return Number.NaN;
  }

  const [hourText, minuteText] = value.split(":");
  const hour = Number(hourText);
  const minute = Number(minuteText);
  if (
    Number.isNaN(hour) ||
    Number.isNaN(minute) ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59
  ) {
    return Number.NaN;
  }
  return hour * 60 + minute;
}

function formatMinute(minute) {
  const normalizedMinute =
    ((minute % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
  const hour = Math.floor(normalizedMinute / 60);
  const minutePart = normalizedMinute % 60;
  return `${String(hour).padStart(2, "0")}:${String(minutePart).padStart(2, "0")}`;
}

function buildNormalizedCustomSlots(slots = []) {
  if (!Array.isArray(slots) || !slots.length) {
    return { error: "自定义时段不能为空", slots: [] };
  }

  const segments = [];
  for (const slot of slots) {
    if (!slot?.start || !slot?.end) {
      return { error: "自定义时段不完整", slots: [] };
    }

    const startMinute = toMinute(slot.start);
    const endMinute = toMinute(slot.end);
    if (Number.isNaN(startMinute) || Number.isNaN(endMinute)) {
      return { error: "自定义时段格式不正确", slots: [] };
    }
    if (startMinute === endMinute) {
      return { error: "自定义时段范围不正确", slots: [] };
    }

    if (startMinute < endMinute) {
      segments.push({ startMinute, endMinute });
      continue;
    }

    segments.push({ startMinute, endMinute: MINUTES_PER_DAY });
    segments.push({ startMinute: 0, endMinute });
  }

  const sortedSegments = [...segments].sort((left, right) => {
    if (left.startMinute !== right.startMinute) {
      return left.startMinute - right.startMinute;
    }
    return left.endMinute - right.endMinute;
  });

  const mergedSegments = [];
  let mergedAdjacent = false;
  for (const segment of sortedSegments) {
    const lastSegment = mergedSegments[mergedSegments.length - 1];
    if (!lastSegment) {
      mergedSegments.push({ ...segment });
      continue;
    }

    if (segment.startMinute < lastSegment.endMinute) {
      return { error: "自定义时段不能重叠", slots: [] };
    }
    if (segment.startMinute === lastSegment.endMinute) {
      lastSegment.endMinute = segment.endMinute;
      mergedAdjacent = true;
      continue;
    }
    mergedSegments.push({ ...segment });
  }

  if (
    mergedSegments.length === 1 &&
    mergedSegments[0].startMinute === 0 &&
    mergedSegments[0].endMinute === MINUTES_PER_DAY
  ) {
    return {
      error: "自定义时段不能覆盖全天，请使用让AI自动管理",
      slots: [],
    };
  }

  let leadingSegment = null;
  let trailingSegment = null;
  if (
    mergedSegments.length >= 2 &&
    mergedSegments[0].startMinute === 0 &&
    mergedSegments[mergedSegments.length - 1].endMinute === MINUTES_PER_DAY
  ) {
    leadingSegment = mergedSegments[0];
    trailingSegment = mergedSegments[mergedSegments.length - 1];
    mergedAdjacent = true;
  }

  const normalizedSlots = [];
  for (const segment of mergedSegments) {
    if (segment === leadingSegment || segment === trailingSegment) {
      continue;
    }
    normalizedSlots.push({
      start: formatMinute(segment.startMinute),
      end: formatMinute(segment.endMinute),
    });
  }

  if (leadingSegment && trailingSegment) {
    normalizedSlots.push({
      start: formatMinute(trailingSegment.startMinute),
      end: formatMinute(leadingSegment.endMinute),
    });
  }

  normalizedSlots.sort(
    (left, right) => toMinute(left.start) - toMinute(right.start),
  );

  const changed =
    mergedAdjacent ||
    normalizedSlots.length !== slots.length ||
    JSON.stringify(normalizedSlots) !== JSON.stringify(slots);

  return { slots: normalizedSlots, changed };
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

function normalizeBidAmount(value, fallback) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  const stepped = Math.round(numeric / BID_STEP) * BID_STEP;
  return Number(
    Math.min(DEFAULT_BID_MAX, Math.max(DEFAULT_BID_MIN, stepped)).toFixed(1),
  );
}

function normalizeBidRange(bidMin, bidMax) {
  const min = normalizeBidAmount(bidMin, DEFAULT_BID_MIN);
  const max = normalizeBidAmount(bidMax, DEFAULT_BID_MAX);
  return min <= max
    ? { bidMin: min, bidMax: max }
    : { bidMin: max, bidMax: max };
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
      shopEpoiId: "--",
      firstRechargeOffer: null,
      firstRechargePackages: [],
      firstRechargeTotalAmount: 0,
      version: "0",
      membership: {
        active: false,
        expireAt: null,
      },
      statsMode: "marketing",
      hasPowerStats: true,
      statsRange: "TODAY",
      statsRangeOptions: [
        { label: "今日", value: "TODAY", compareLabel: "较昨日" },
        { label: "近7天", value: "LAST_7_DAYS", compareLabel: "较前7日" },
        { label: "近30天", value: "LAST_30_DAYS", compareLabel: "较前30日" },
      ],
      marketingStats: {
        currentExposure: 0,
        previousExposure: 0,
        currentCost: 0,
        previousCost: 0,
        currentCostRatio: 0,
        previousCostRatio: 0,
      },
      powerStats: {
        currentConsumedPower: 0,
        previousConsumedPower: 0,
        currentEstimatedPower: 0,
        previousEstimatedPower: 0,
        balance: 0,
      },
      currentBid: {
        inPromotionWindow: false,
        editable: false,
        currentBidAmount: null,
        previousBidAmount: null,
        latestExecuteStatus: "",
        displayStatusText: "",
        minBidAmount: 0.1,
        maxBidAmount: 4,
      },
      aiPromotionEnabled: false,
      promotionDisabledReasonText: "",
      balanceReminderEnabled: true,
      strategyConfigured: false,
      strategyEntryVisible: false,
      autoTimeModeAvailable: false,
      autoTimeModeHintText: "点击解锁",
      autoTimeModePeakWindows: [],
      autoTimeModeDayTypeText: "工作日",
      autoTimeModePeakMatched: false,
      unlockingAutoTimeMode: false,
      strategyModalVisible: false,
      authFailurePromptVisible: false,
      supportPromptVisible: false,
      bidModalVisible: false,
      peakWindowTipsVisible: false,
      guideVisible: false,
      guideStep: "",
      guideShopKey: "",
      guideCopyStyle: {},
      homeGuideCode: HOME_GUIDE_CODE,
      homeOnboardingReady: false,
      promotionGuidePopupVisible: false,
      rechargeGuidePopupVisible: false,
      bidFormAmount: "",
      submittingManualBid: false,
      promotionLoading: false,
      submittingToggleKey: "",
      submittingStrategy: false,
      appOpenTrackedForShow: false,
      timeOptions: buildTimeOptions(),
      activeBidRangeHandle: "",
      strategyBidMinLimit: DEFAULT_BID_MIN,
      strategyBidMaxLimit: DEFAULT_BID_MAX,
      strategyBidStep: BID_STEP,
      rateOptions: [
        "1%",
        "2%",
        "3%",
        "4%",
        "5%",
        "6%",
        "7%",
        "8%",
        "9%",
        "10%",
        "11%",
        "12%",
        "13%",
        "14%",
        "15%",
        "16%",
        "17%",
        "18%",
        "19%",
        "20%",
      ],
      strategyForm: {
        priority: "rate",
        timeMode: "business",
        rateMode: "control",
        customSlots: [
          { start: "10:00", end: "11:00" },
          { start: "12:00", end: "13:00" },
        ],
        rateMin: "5%",
        rateMax: "6%",
        bidMin: DEFAULT_BID_MIN,
        bidMax: DEFAULT_BID_MAX,
      },
      lastRatePrioritySelection: {
        rateMin: "5%",
        rateMax: "10%",
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
    statsRangeLabels() {
      return this.statsRangeOptions.map((item) => item.label);
    },
    statsTitleOptions() {
      return this.statsRangeLabels.map((label) => `${label}数据统计`);
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
            compareLabel: this.statsCompareLabel,
            cardClass: "stat-card-warm",
            valueClass: "stat-value-dark",
            trendClass: consumeTrend.trendClass,
          },
          {
            label: "预计消耗",
            value: formatDisplayNumber(this.powerStats.currentEstimatedPower),
            trend: estimatedTrend.text,
            compareLabel: this.statsCompareLabel,
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

      const exposureTrend = buildTrend(
        this.marketingStats.currentExposure,
        this.marketingStats.previousExposure,
      );
      const costTrend = buildTrend(
        this.marketingStats.currentCost,
        this.marketingStats.previousCost,
      );
      const ratioTrend = buildTrend(
        this.marketingStats.currentCostRatio,
        this.marketingStats.previousCostRatio,
      );
      return [
        {
          label: "付费曝光/次",
          value: formatDisplayNumber(this.marketingStats.currentExposure),
          trend: exposureTrend.text,
          compareLabel: this.statsCompareLabel,
          cardClass: "stat-card-warm",
          valueClass: "stat-value-dark",
          trendClass: exposureTrend.trendClass,
        },
        {
          label: "推广花费/元",
          value: formatDisplayNumber(this.marketingStats.currentCost),
          trend: costTrend.text,
          compareLabel: this.statsCompareLabel,
          cardClass: "stat-card-cool",
          valueClass: "stat-value-dark",
          trendClass: costTrend.trendClass,
        },
        {
          label: "费用率",
          value: formatPercentValue(this.marketingStats.currentCostRatio),
          trend: ratioTrend.text,
          compareLabel: this.statsCompareLabel,
          cardClass: "stat-card-pink",
          valueClass: "stat-value-dark",
          trendClass: ratioTrend.trendClass,
        },
      ];
    },
    isToggleBusy() {
      return this.promotionLoading || Boolean(this.submittingToggleKey);
    },
    strategyStatusText() {
      return this.strategyConfigured ? "已配置" : "未配置";
    },
    strategyButtonText() {
      return this.strategyConfigured ? "修改" : "配置";
    },
    shopDisplayName() {
      const poiId = String(this.shopPoiId || "").trim();
      return poiId && poiId !== "--"
        ? `${this.shopName}（门店 ID： ${poiId}）`
        : this.shopName;
    },
    strategyDescription() {
      return this.strategyConfigured
        ? "已保存推广策略，AI将按策略执行"
        : "曝光优先/省钱模式";
    },
    isExposurePriority() {
      return this.strategyForm.priority === "exposure";
    },
    vipExpireText() {
      return formatDateText(this.membership.expireAt);
    },
    displayedPeakWindowTips() {
      return this.autoTimeModePeakWindows.length
        ? this.autoTimeModePeakWindows
        : ["暂无高峰时段"];
    },
    peakMatchedText() {
      return this.autoTimeModePeakMatched ? "高峰期" : "非高峰期";
    },
    currentBidDisplayText() {
      if (this.currentBid.displayStatusText) {
        return this.currentBid.displayStatusText;
      }
      if (
        this.currentBid.currentBidAmount === null ||
        this.currentBid.currentBidAmount === undefined
      ) {
        return "--元/次";
      }
      return `${this.formatBidAmount(this.currentBid.currentBidAmount)}元/次`;
    },
    currentBidStatusClass() {
      if (this.currentBid.latestExecuteStatus === "FAILED") {
        return "current-bid-value-failed";
      }
      if (this.currentBid.latestExecuteStatus === "SKIPPED") {
        return "current-bid-value-skipped";
      }
      return "";
    },
    currentGuideStepConfig() {
      return HOME_GUIDE_STEPS.find((item) => item.key === this.guideStep);
    },
    currentGuideCopyClass() {
      return this.currentGuideStepConfig?.copyClass || "";
    },
    currentGuideTitle() {
      return this.currentGuideStepConfig?.title || "";
    },
    currentGuideDesc() {
      return this.currentGuideStepConfig?.desc || "";
    },
    currentGuideNextText() {
      return this.currentGuideStepConfig?.nextText || "下一步 >>>";
    },
    hasHomeRechargePackage() {
      return this.firstRechargePackages.some(
        (item) => Number(item?.powerAmount) === HOME_RECHARGE_POWER_AMOUNT,
      );
    },
    firstRechargeIncomplete() {
      return Number(this.firstRechargeTotalAmount || 0) <= 0;
    },
    homeRechargeEntryVisible() {
      return (
        this.hasPowerStats &&
        this.firstRechargeIncomplete &&
        this.hasHomeRechargePackage
      );
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
    aiPromotionRobotStaticSrc() {
      return PROMOTION_ROBOT_STATIC_ICON;
    },
    strategyBidRangeTrackStyle() {
      const bidRange = normalizeBidRange(
        this.strategyForm.bidMin,
        this.strategyForm.bidMax,
      );
      const span = DEFAULT_BID_MAX - DEFAULT_BID_MIN;
      const left = ((bidRange.bidMin - DEFAULT_BID_MIN) / span) * 100;
      const right = 100 - ((bidRange.bidMax - DEFAULT_BID_MIN) / span) * 100;
      return `left: ${left}%; right: ${right}%;`;
    },
    strategyBidMinThumbStyle() {
      return this.buildStrategyBidThumbStyle(this.strategyForm.bidMin);
    },
    strategyBidMaxThumbStyle() {
      return this.buildStrategyBidThumbStyle(this.strategyForm.bidMax);
    },
  },
  onShow() {
    this.version = getVersion();
    this.appOpenTrackedForShow = false;
    this.loadPageData();
  },
  methods: {
    async loadPageData() {
      this.homeOnboardingReady = false;
      this.promotionGuidePopupVisible = false;
      this.rechargeGuidePopupVisible = false;
      let dashboardLoaded = false;
      try {
        const [dashboardResult, powerSummaryResult] = await Promise.allSettled([
          getMtH5HomeDashboard(this.statsRange),
          getMtH5PowerSummary(),
        ]);

        if (dashboardResult.status === "fulfilled") {
          this.applyDashboardData(dashboardResult.value);
          dashboardLoaded = true;
        } else {
          const shopInfo = getShopInfo();
          this.shopName = shopInfo.nickName || shopInfo.userName || "美团门店";
          this.shopPoiId = shopInfo.poiId || "--";
          this.shopEpoiId = shopInfo.epoiId || "--";
          showErrorToast(dashboardResult.reason, "首页信息加载失败");
        }

        this.applyPowerSummary(
          this.hasPowerStats && powerSummaryResult.status === "fulfilled"
            ? powerSummaryResult.value
            : null,
        );
        this.homeOnboardingReady = dashboardLoaded;
        this.updateHomeOnboardingPopupVisibility();
      } catch (error) {
        const shopInfo = getShopInfo();
        this.shopName = shopInfo.nickName || shopInfo.userName || "美团门店";
        this.shopPoiId = shopInfo.poiId || "--";
        this.shopEpoiId = shopInfo.epoiId || "--";
        this.homeOnboardingReady = false;
        this.promotionGuidePopupVisible = false;
        this.rechargeGuidePopupVisible = false;
        showErrorToast(error, "首页信息加载失败");
      }
    },
    async loadDashboard() {
      try {
        const dashboard = await getMtH5HomeDashboard(this.statsRange);
        this.applyDashboardData(dashboard);
      } catch (error) {
        const shopInfo = getShopInfo();
        this.shopName = shopInfo.nickName || shopInfo.userName || "美团门店";
        this.shopPoiId = shopInfo.poiId || "--";
        this.shopEpoiId = shopInfo.epoiId || "--";
        showErrorToast(error, "首页信息加载失败");
      }
    },
    applyDashboardData(dashboard = {}) {
      const shop = dashboard.shop || {};
      const promotion = dashboard.promotion || {};
      this.shopName = shop.shopName || "美团门店";
      this.shopPoiId = shop.poiId || "--";
      this.shopEpoiId = shop.epoiId || "--";
      this.guideShopKey = this.resolveGuideShopKey(shop);
      this.membership = {
        active: Boolean(dashboard.membership?.active),
        expireAt: dashboard.membership?.expireAt || null,
      };
      this.marketingStats = {
        ...this.marketingStats,
        ...(dashboard.marketingStats || {}),
      };
      this.hasPowerStats = Boolean(dashboard.powerStats);
      if (this.hasPowerStats) {
        this.powerStats = {
          ...this.powerStats,
          ...(dashboard.powerStats || {}),
        };
      } else {
        this.statsMode = "marketing";
        this.powerStats = {
          currentConsumedPower: 0,
          previousConsumedPower: 0,
          currentEstimatedPower: 0,
          previousEstimatedPower: 0,
          balance: 0,
        };
      }
      this.applyCurrentBid(dashboard.currentBid || {});
      this.applyPromotionSettings(promotion);
      this.applyAuthorizationState(dashboard);
      this.applyGuideState(dashboard.guide || {});
      this.trackAppOpenOnce();
    },
    applyPowerSummary(summary = null) {
      this.firstRechargeOffer = this.hasPowerStats
        ? summary?.firstRechargeOffer || null
        : null;
      this.firstRechargePackages =
        this.hasPowerStats && Array.isArray(summary?.recommendedPackages)
          ? summary.recommendedPackages
          : [];
      this.firstRechargeTotalAmount =
        this.hasPowerStats && Number.isFinite(Number(summary?.totalRecharge))
          ? Number(summary.totalRecharge)
          : 0;
    },
    resolveGuideShopKey(shop = {}) {
      return (
        shop.poiId ||
        shop.epoiId ||
        this.shopPoiId ||
        this.shopEpoiId ||
        "unknown"
      );
    },
    applyGuideState(guide = {}) {
      this.homeGuideCode = guide.homeGuideCode || HOME_GUIDE_CODE;
      this.guideVisible = false;
      this.guideStep = "";
    },
    isValidGuideStep(step) {
      return HOME_GUIDE_STEPS.some((item) => item.key === step);
    },
    isGuideStep(step) {
      return this.guideVisible && this.guideStep === step;
    },
    showGuideStep(step) {
      if (!this.isValidGuideStep(step)) {
        return;
      }
      const shopKey = this.guideShopKey || this.resolveGuideShopKey();
      this.guideVisible = false;
      this.guideStep = step;
      this.guideCopyStyle = {};
      setMtH5HomeGuideStep(this.homeGuideCode, shopKey, step);
      this.scrollGuideTargetIntoView();
    },
    handleGuideTargetTap(step) {
      if (!this.isGuideStep(step)) {
        return;
      }
      this.advanceGuide();
    },
    advanceGuide() {
      const currentIndex = HOME_GUIDE_STEPS.findIndex(
        (item) => item.key === this.guideStep,
      );
      if (currentIndex === -1) {
        return;
      }
      if (currentIndex < HOME_GUIDE_STEPS.length - 1) {
        this.showGuideStep(HOME_GUIDE_STEPS[currentIndex + 1].key);
        return;
      }
      this.finishHomeGuide();
    },
    async finishHomeGuide() {
      const shopKey = this.guideShopKey || this.resolveGuideShopKey();
      this.guideVisible = false;
      this.guideStep = "";
      this.guideCopyStyle = {};
      dismissMtH5HomeGuide(this.homeGuideCode, shopKey);
      clearMtH5HomeGuideStep(this.homeGuideCode, shopKey);
      try {
        await completeMtH5Guide(this.homeGuideCode);
      } catch (error) {
        // 已完成三步后当前设备不再打扰；后端完成态等待下次成功同步。
      }
    },
    scrollGuideTargetIntoView() {
      this.$nextTick(() => {
        const config = this.currentGuideStepConfig;
        if (!config?.targetClass) {
          return;
        }
        const query = uni.createSelectorQuery().in(this);
        query.select(`.${config.targetClass}`).boundingClientRect();
        query.selectViewport().scrollOffset();
        query.exec((results) => {
          const rect = results?.[0];
          const viewport = results?.[1];
          if (!rect || !viewport) {
            return;
          }
          const systemInfo = uni.getSystemInfoSync();
          const windowHeight = Number(systemInfo.windowHeight || 667);
          const targetHeight = Number(rect.height || 120);
          const preferredTop =
            GUIDE_COPY_HEIGHT + GUIDE_COPY_GAP + GUIDE_VIEWPORT_TOP_SAFE;
          const maxTop = Math.max(
            GUIDE_VIEWPORT_TOP_SAFE,
            windowHeight - GUIDE_VIEWPORT_BOTTOM_SAFE - targetHeight,
          );
          const targetTop = Math.min(preferredTop, maxTop);
          const scrollTop = Math.max(
            0,
            viewport.scrollTop + rect.top - targetTop,
          );
          uni.pageScrollTo({
            scrollTop,
            duration: 120,
          });
          setTimeout(() => {
            this.updateGuideCopyPosition(true);
          }, 140);
        });
      });
    },
    updateGuideCopyPosition(reveal = false) {
      const config = this.currentGuideStepConfig;
      if (!config?.targetClass) {
        if (reveal) {
          this.guideVisible = true;
        }
        return;
      }
      const query = uni.createSelectorQuery().in(this);
      query.select(`.${config.targetClass}`).boundingClientRect();
      query.exec((results) => {
        const rect = results?.[0];
        if (!rect) {
          if (reveal) {
            this.guideVisible = true;
          }
          return;
        }
        const systemInfo = uni.getSystemInfoSync();
        const windowHeight = Number(systemInfo.windowHeight || 667);
        const aboveTop = rect.top - GUIDE_COPY_HEIGHT - GUIDE_COPY_GAP;
        const belowTop = rect.bottom + GUIDE_COPY_GAP;
        const hasAbove = aboveTop >= GUIDE_VIEWPORT_TOP_SAFE;
        const hasBelow =
          belowTop + GUIDE_COPY_HEIGHT <=
          windowHeight - GUIDE_VIEWPORT_BOTTOM_SAFE;
        let top = hasAbove ? aboveTop : belowTop;
        if (!hasAbove && !hasBelow) {
          top = Math.max(
            GUIDE_VIEWPORT_TOP_SAFE,
            Math.min(
              aboveTop,
              windowHeight - GUIDE_VIEWPORT_BOTTOM_SAFE - GUIDE_COPY_HEIGHT,
            ),
          );
        }
        this.guideCopyStyle = {
          top: `${Math.round(top)}px`,
        };
        if (reveal) {
          this.guideVisible = true;
        }
      });
    },
    noop() {},
    updateHomeOnboardingPopupVisibility() {
      this.promotionGuidePopupVisible = false;
      this.rechargeGuidePopupVisible = false;
      if (!this.homeOnboardingReady || this.strategyModalVisible) {
        return;
      }
      if (!this.aiPromotionEnabled) {
        if (!HOME_ONBOARDING_POPUP_SESSION_STATE.promotionGuideShown) {
          HOME_ONBOARDING_POPUP_SESSION_STATE.promotionGuideShown = true;
          this.promotionGuidePopupVisible = true;
        }
        return;
      }
      if (
        this.homeRechargeEntryVisible &&
        !HOME_ONBOARDING_POPUP_SESSION_STATE.rechargeGuideShown
      ) {
        HOME_ONBOARDING_POPUP_SESSION_STATE.rechargeGuideShown = true;
        this.rechargeGuidePopupVisible = true;
      }
    },
    dismissPromotionGuidePopup() {
      this.promotionGuidePopupVisible = false;
    },
    dismissRechargeGuidePopup() {
      this.rechargeGuidePopupVisible = false;
    },
    openPromotionGuideStrategyModal() {
      this.dismissPromotionGuidePopup();
      this.trackBehavior("H5_HOME_PROMOTION_GUIDE_CLICK");
      this.openStrategyModal();
    },
    openRechargeGuidePage() {
      if (!this.homeRechargeEntryVisible) {
        this.dismissRechargeGuidePopup();
        return;
      }
      this.dismissRechargeGuidePopup();
      this.goToPowerRecharge();
    },
    trackBehavior(eventType, context = undefined) {
      reportMtH5BehaviorEvent(eventType, context);
    },
    trackAppOpenOnce() {
      if (this.appOpenTrackedForShow) {
        return;
      }
      this.appOpenTrackedForShow = true;
      this.trackBehavior("H5_APP_OPEN");
    },
    applyPromotionSettings(settings = {}) {
      this.aiPromotionEnabled = Boolean(settings.promotionEnabled);
      this.promotionDisabledReasonText =
        settings.promotionDisabledReasonText || "";
      this.balanceReminderEnabled =
        settings.balanceReminderEnabled === undefined
          ? true
          : Boolean(settings.balanceReminderEnabled);
      this.strategyConfigured = Boolean(settings.strategyConfigured);
      this.strategyEntryVisible = Boolean(settings.strategyEntryVisible);
      this.autoTimeModeAvailable = Boolean(settings.autoTimeModeAvailable);
      this.autoTimeModeHintText = this.autoTimeModeAvailable
        ? settings.autoTimeModeHintText || "AI识别高峰期"
        : "点击解锁";
      this.autoTimeModePeakWindows = Array.isArray(
        settings.autoTimeModePeakWindows,
      )
        ? settings.autoTimeModePeakWindows.filter(Boolean)
        : [];
      this.autoTimeModeDayTypeText =
        settings.autoTimeModeDayTypeText || "工作日";
      this.autoTimeModePeakMatched = Boolean(settings.autoTimeModePeakMatched);
      this.applyStrategyConfig(settings.strategyConfigJson);
    },
    applyAuthorizationState(dashboard = {}) {
      const promotion = dashboard.promotion || {};
      const authState =
        dashboard.authorization ||
        dashboard.mtAuthorization ||
        dashboard.meituanAuthorization ||
        dashboard.auth ||
        {};
      const rawStatus =
        authState.authStatus ??
        authState.auth_status ??
        authState.status ??
        authState.authorizationStatus ??
        dashboard.authStatus ??
        dashboard.auth_status ??
        dashboard.authorizationStatus ??
        dashboard.mtAuthStatus;
      const rawNeedReauth =
        authState.needReauth ??
        authState.reauthRequired ??
        dashboard.needReauth ??
        dashboard.reauthRequired;
      const rawText = [
        authState.message,
        authState.statusText,
        dashboard.authMessage,
        promotion.promotionDisabledReasonText,
      ]
        .filter(Boolean)
        .join(" ");
      const reauthRequired =
        rawNeedReauth === true ||
        String(rawNeedReauth || "").toLowerCase() === "true" ||
        rawStatus === MT_AUTH_STATUS_REAUTH_REQUIRED ||
        Number(rawStatus) === MT_AUTH_STATUS_REAUTH_REQUIRED ||
        String(rawStatus || "").toUpperCase() === "REAUTH_REQUIRED" ||
        MT_AUTH_REAUTH_TEXT_PATTERN.test(rawText);

      this.authFailurePromptVisible = Boolean(reauthRequired);
    },
    async showAutoTimeModePeakWindows() {
      if (!this.autoTimeModeAvailable) {
        return;
      }
      if (!this.autoTimeModePeakWindows.length) {
        uni.showLoading({
          title: "加载中...",
          mask: true,
        });
        try {
          const settings = await getMtH5PromotionSettings();
          this.applyPromotionSettings(settings);
        } catch (error) {
          showErrorToast(error, "高峰期加载失败");
          return;
        } finally {
          uni.hideLoading();
        }
      }
      this.peakWindowTipsVisible = !this.peakWindowTipsVisible;
    },
    hidePeakWindowTips() {
      this.peakWindowTipsVisible = false;
    },
    async unlockAutoTimeMode() {
      if (this.unlockingAutoTimeMode || this.autoTimeModeAvailable) {
        return;
      }
      this.trackBehavior("H5_UNLOCK_AI_PEAK_CLICK");
      this.unlockingAutoTimeMode = true;
      uni.showLoading({
        title: "AI正在识别高峰期...",
        mask: true,
      });
      try {
        const settings = await unlockMtH5AutoTimeMode();
        this.applyPromotionSettings(settings);
        if (this.autoTimeModeAvailable) {
          showToast(
            settings?.autoTimeModeUnlockMessage || "AI识别高峰期已解锁",
          );
          return;
        }
        showToast(
          settings?.autoTimeModeUnlockMessage ||
            "数据不足，功能开启5天后解锁！",
        );
      } catch (error) {
        showErrorToast(error, "数据不足，功能开启5天后解锁！");
      } finally {
        uni.hideLoading();
        this.unlockingAutoTimeMode = false;
      }
    },
    applyCurrentBid(currentBid = {}) {
      this.currentBid = {
        inPromotionWindow: Boolean(currentBid.inPromotionWindow),
        editable: Boolean(currentBid.editable),
        disabledReason: currentBid.disabledReason || "",
        currentBidAmount:
          currentBid.currentBidAmount === null ||
          currentBid.currentBidAmount === undefined
            ? null
            : Number(currentBid.currentBidAmount),
        previousBidAmount:
          currentBid.previousBidAmount === null ||
          currentBid.previousBidAmount === undefined
            ? null
            : Number(currentBid.previousBidAmount),
        latestExecuteStatus: currentBid.latestExecuteStatus || "",
        displayStatusText: currentBid.displayStatusText || "",
        minBidAmount: Number(currentBid.minBidAmount || 0.1),
        maxBidAmount: Number(currentBid.maxBidAmount || 4),
      };
    },
    formatBidAmount(value) {
      return formatDisplayNumber(value);
    },
    openBidModal() {
      if (!this.currentBid.editable) {
        showToast(this.currentBid.displayStatusText || "非推广时段");
        return;
      }
      this.trackBehavior("H5_MANUAL_BID_EDIT_OPEN");
      this.bidFormAmount =
        this.currentBid.currentBidAmount === null ||
        this.currentBid.currentBidAmount === undefined
          ? ""
          : this.formatBidAmount(this.currentBid.currentBidAmount);
      this.bidModalVisible = true;
    },
    closeBidModal() {
      if (this.submittingManualBid) {
        return;
      }
      this.bidModalVisible = false;
    },
    async submitManualBid() {
      if (this.submittingManualBid) {
        return;
      }
      this.trackBehavior("H5_MANUAL_BID_SUBMIT_CLICK");
      const amount = Number(this.bidFormAmount);
      const minBid = Number(this.currentBid.minBidAmount || 0.1);
      const maxBid = Number(this.currentBid.maxBidAmount || 4);
      if (!Number.isFinite(amount) || amount < minBid || amount > maxBid) {
        showToast(
          `出价范围为${this.formatBidAmount(minBid)}-${this.formatBidAmount(maxBid)}元`,
        );
        return;
      }
      this.submittingManualBid = true;
      try {
        const result = await updateMtH5ManualPromotionBid(amount);
        this.currentBid = {
          ...this.currentBid,
          currentBidAmount: Number(result?.effectiveBidAmount ?? amount),
          previousBidAmount:
            result?.previousBidAmount === null ||
            result?.previousBidAmount === undefined
              ? this.currentBid.previousBidAmount
              : Number(result.previousBidAmount),
          latestExecuteStatus: "SUCCESS",
          displayStatusText: "",
        };
        showToast("当前出价已修改");
        this.bidModalVisible = false;
        await this.loadPageData();
      } catch (error) {
        showErrorToast(error, "修改出价失败");
      } finally {
        this.submittingManualBid = false;
      }
    },
    toggleStatsMode() {
      if (!this.hasPowerStats) {
        this.statsMode = "marketing";
        return;
      }
      this.statsMode = this.statsMode === "power" ? "marketing" : "power";
    },
    handleStatsRangeChange(event) {
      const nextIndex = Number(event?.detail?.value);
      const nextValue = this.statsRangeOptions[nextIndex]?.value;
      this.selectStatsRange(nextValue);
    },
    selectStatsRange(value) {
      if (!value || value === this.statsRange) {
        return;
      }
      this.statsRange = value;
      this.loadDashboard();
    },
    openSupportPrompt() {
      this.supportPromptVisible = true;
    },
    openAuthFailureSupport() {
      this.authFailurePromptVisible = false;
      this.supportPromptVisible = true;
    },
    closeAuthFailurePrompt() {
      this.authFailurePromptVisible = false;
    },
    closeSupportPrompt() {
      this.supportPromptVisible = false;
    },
    openRenewalPage() {
      if (typeof window === "undefined") {
        showToast("请在美团商家端打开续费页面");
        return;
      }

      if (window.parent && window.parent !== window) {
        window.parent.postMessage(
          { action: "isvJumpToSalePage", url: RENEWAL_PAGE_URL },
          "*",
        );
        return;
      }

      showToast("请在美团商家端打开续费页面");
    },
    openStrategyModal() {
      this.strategyModalVisible = true;
    },
    promptStrategyConfigBeforeEnable() {
      return new Promise((resolve) => {
        uni.showModal({
          title: "提示",
          content: "您暂未配置推广策略，请先配置后再开启",
          confirmText: "配置",
          cancelText: "取消",
          success: (res) => {
            if (res.confirm) {
              this.openStrategyModal();
            }
            resolve(Boolean(res.confirm));
          },
          fail: () => resolve(false),
        });
      });
    },
    handleStrategyClick() {
      if (this.guideVisible) {
        return;
      }
      if (!this.strategyEntryVisible) {
        showToast("推广策略暂未开放");
        return;
      }
      this.trackBehavior("H5_CONFIG_BUTTON_CLICK");
      this.openStrategyModal();
    },
    goToLogPage() {
      if (this.guideVisible) {
        return;
      }
      uni.redirectTo({
        url: "/pages/log/index",
      });
    },
    goToPowerRecharge() {
      if (this.guideVisible || !this.homeRechargeEntryVisible) {
        return;
      }
      this.trackBehavior("H5_RECHARGE_GIF_CLICK");
      uni.redirectTo({
        url: `/pages/power/index?powerAmount=${HOME_RECHARGE_POWER_AMOUNT}`,
      });
    },
    applyStrategyConfig(strategyConfigJson) {
      if (!strategyConfigJson) {
        this.strategyForm = this.createDefaultStrategyForm();
        this.syncLastRatePrioritySelection(this.strategyForm);
        return;
      }

      try {
        const parsedConfig = JSON.parse(strategyConfigJson);
        this.strategyForm = this.normalizeStrategyForm(parsedConfig);
        this.syncLastRatePrioritySelection(this.strategyForm);
      } catch (error) {
        this.strategyForm = this.createDefaultStrategyForm();
        this.syncLastRatePrioritySelection(this.strategyForm);
      }
    },
    createDefaultStrategyForm() {
      return {
        priority: "rate",
        timeMode: this.resolveDefaultTimeMode(),
        rateMode: "control",
        customSlots: [],
        rateMin: "5%",
        rateMax: "10%",
        bidMin: DEFAULT_BID_MIN,
        bidMax: DEFAULT_BID_MAX,
      };
    },
    resolveDefaultTimeMode() {
      return this.autoTimeModeAvailable ? "auto" : "business";
    },
    normalizeStrategyForm(input = {}) {
      const defaultForm = this.createDefaultStrategyForm();
      const priority = ["rate", "exposure"].includes(input.priority)
        ? input.priority
        : defaultForm.priority;
      const rawCustomSlots = Array.isArray(input.customSlots)
        ? input.customSlots
            .filter((item) => item?.start && item?.end)
            .map((item) => ({
              start: item.start,
              end: item.end,
            }))
        : defaultForm.customSlots;
      const normalizedCustomSlotResult = rawCustomSlots.length
        ? buildNormalizedCustomSlots(rawCustomSlots)
        : { slots: defaultForm.customSlots };
      const customSlots = normalizedCustomSlotResult.error
        ? rawCustomSlots
        : normalizedCustomSlotResult.slots;
      const bidRange = normalizeBidRange(input.bidMin, input.bidMax);

      return {
        priority,
        timeMode: ["auto", "business", "custom"].includes(input.timeMode)
          ? input.timeMode === "auto" && !this.autoTimeModeAvailable
            ? "business"
            : input.timeMode
          : defaultForm.timeMode,
        rateMode: "control",
        customSlots: customSlots.length ? customSlots : defaultForm.customSlots,
        rateMin:
          priority === "exposure"
            ? "1%"
            : this.rateOptions.includes(input.rateMin)
              ? input.rateMin
              : defaultForm.rateMin,
        rateMax:
          priority === "exposure"
            ? "20%"
            : this.rateOptions.includes(input.rateMax)
              ? input.rateMax
              : defaultForm.rateMax,
        bidMin: bidRange.bidMin,
        bidMax: bidRange.bidMax,
      };
    },
    closeStrategyModal(trackCancel = false) {
      if (trackCancel) {
        this.trackBehavior("H5_CONFIG_MODAL_CANCEL_CLICK");
      }
      this.strategyModalVisible = false;
      this.peakWindowTipsVisible = false;
    },
    selectStrategyPriority(priority) {
      if (priority === this.strategyForm.priority) {
        return;
      }
      if (priority === "exposure") {
        uni.showModal({
          title: "请谨慎开启！",
          content: "该策略将大幅提升曝光量同时增加花费。",
          confirmText: "确认开启",
          cancelText: "取消",
          success: (res) => {
            if (!res.confirm) {
              return;
            }
            if (this.strategyForm.priority === "rate") {
              this.syncLastRatePrioritySelection(this.strategyForm);
            }
            this.strategyForm.priority = "exposure";
            this.strategyForm.rateMin = "1%";
            this.strategyForm.rateMax = "20%";
          },
        });
        return;
      }
      if (this.strategyForm.priority === "rate") {
        this.syncLastRatePrioritySelection(this.strategyForm);
      }
      this.strategyForm.priority = priority;
      this.restoreRatePrioritySelection();
    },
    selectTimeMode(mode) {
      if (mode === "auto" && !this.autoTimeModeAvailable) {
        this.unlockAutoTimeMode();
        return;
      }
      this.strategyForm.timeMode = mode;
    },
    selectRateMode(mode) {
      this.strategyForm.rateMode = mode;
    },
    handleRateCardTap() {
      if (this.isExposurePriority) {
        return;
      }
      this.selectRateMode("control");
    },
    getTimeOptionIndex(value) {
      const index = this.timeOptions.indexOf(value);
      return index === -1 ? 0 : index;
    },
    getRateOptionIndex(value) {
      const index = this.rateOptions.indexOf(value);
      return index === -1 ? 0 : index;
    },
    handleCustomTimeChange(index, field, event) {
      const nextValue =
        typeof event.detail.value === "string"
          ? event.detail.value
          : this.timeOptions[event.detail.value] || this.timeOptions[0];
      this.strategyForm.customSlots[index][field] = nextValue;
      this.strategyForm.customSlots = [...this.strategyForm.customSlots];
      this.mergeAdjacentCustomSlotsForDisplay();
    },
    formatStrategyBidAmount(value) {
      return normalizeBidAmount(value, DEFAULT_BID_MIN).toFixed(1);
    },
    buildStrategyBidThumbStyle(value) {
      const amount = normalizeBidAmount(value, DEFAULT_BID_MIN);
      const span = DEFAULT_BID_MAX - DEFAULT_BID_MIN;
      const left = ((amount - DEFAULT_BID_MIN) / span) * 100;
      return `left: ${left}%;`;
    },
    applyBidRangeValue(field, value) {
      if (field === "bidMin") {
        this.strategyForm.bidMin = Math.min(value, this.strategyForm.bidMax);
        return;
      }
      this.strategyForm.bidMax = Math.max(value, this.strategyForm.bidMin);
    },
    startBidRangeDrag(field, event) {
      this.resolveBidRangeTouchValue(event, (value) => {
        const nextField =
          field ||
          (Math.abs(value - this.strategyForm.bidMin) <=
          Math.abs(value - this.strategyForm.bidMax)
            ? "bidMin"
            : "bidMax");
        this.activeBidRangeHandle = nextField;
        this.applyBidRangeValue(nextField, value);
      });
    },
    moveBidRangeDrag(event) {
      if (!this.activeBidRangeHandle) {
        return;
      }
      const activeField = this.activeBidRangeHandle;
      this.resolveBidRangeTouchValue(event, (value) => {
        this.applyBidRangeValue(activeField, value);
      });
    },
    endBidRangeDrag() {
      this.activeBidRangeHandle = "";
    },
    resolveBidRangeTouchValue(event, callback) {
      const touch = event.touches?.[0] || event.changedTouches?.[0];
      if (!touch) {
        return;
      }
      uni
        .createSelectorQuery()
        .in(this)
        .select(".strategy-bid-range-axis")
        .boundingClientRect((rect) => {
          if (!rect?.width) {
            return;
          }
          const percent = Math.min(
            1,
            Math.max(0, (touch.clientX - rect.left) / rect.width),
          );
          const value =
            DEFAULT_BID_MIN + percent * (DEFAULT_BID_MAX - DEFAULT_BID_MIN);
          callback(normalizeBidAmount(value, DEFAULT_BID_MIN));
        })
        .exec();
    },
    handleRateChange(field, event) {
      if (this.isExposurePriority) {
        return;
      }
      const selectedIndex = Number(event.detail.value) || 0;
      const nextValue = this.rateOptions[selectedIndex] || this.rateOptions[0];
      const currentMinIndex = this.getRateOptionIndex(
        this.strategyForm.rateMin,
      );
      const currentMaxIndex = this.getRateOptionIndex(
        this.strategyForm.rateMax,
      );

      this.strategyForm[field] = nextValue;

      if (field === "rateMin" && selectedIndex > currentMaxIndex) {
        this.strategyForm.rateMax = nextValue;
        this.syncLastRatePrioritySelection(this.strategyForm);
        return;
      }

      if (field === "rateMax" && selectedIndex < currentMinIndex) {
        this.strategyForm.rateMin = nextValue;
      }

      this.syncLastRatePrioritySelection(this.strategyForm);
    },
    syncLastRatePrioritySelection(form = {}) {
      if (form.priority !== "rate") {
        return;
      }
      const defaultForm = this.createDefaultStrategyForm();
      this.lastRatePrioritySelection = {
        rateMin: this.rateOptions.includes(form.rateMin)
          ? form.rateMin
          : defaultForm.rateMin,
        rateMax: this.rateOptions.includes(form.rateMax)
          ? form.rateMax
          : defaultForm.rateMax,
      };
    },
    restoreRatePrioritySelection() {
      const defaultForm = this.createDefaultStrategyForm();
      const rateMin = this.rateOptions.includes(
        this.lastRatePrioritySelection?.rateMin,
      )
        ? this.lastRatePrioritySelection.rateMin
        : defaultForm.rateMin;
      const rateMax = this.rateOptions.includes(
        this.lastRatePrioritySelection?.rateMax,
      )
        ? this.lastRatePrioritySelection.rateMax
        : defaultForm.rateMax;
      this.strategyForm.rateMin = rateMin;
      this.strategyForm.rateMax = rateMax;
    },
    addCustomSlot() {
      this.strategyForm.customSlots = [
        ...this.strategyForm.customSlots,
        { start: "10:00", end: "11:00" },
      ];
      this.mergeAdjacentCustomSlotsForDisplay();
    },
    removeCustomSlot(index) {
      if (this.strategyForm.customSlots.length <= 1) {
        showToast("请至少保留一个时段");
        return;
      }

      this.strategyForm.customSlots = this.strategyForm.customSlots.filter(
        (_, slotIndex) => slotIndex !== index,
      );
      this.mergeAdjacentCustomSlotsForDisplay();
    },
    confirmEnablePromotionWhenDisabled() {
      if (this.aiPromotionEnabled) {
        return Promise.resolve(true);
      }
      return new Promise((resolve) => {
        uni.showModal({
          title: "同步开启 AI 推广开关",
          content:
            "当前推广开关为关闭状态，确认后将保存配置并同步开启 AI 推广。",
          confirmText: "确认",
          cancelText: "取消",
          success: (res) => resolve(Boolean(res.confirm)),
          fail: () => resolve(false),
        });
      });
    },
    async submitStrategyConfig() {
      if (this.submittingStrategy) {
        return;
      }
      this.trackBehavior("H5_CONFIG_MODAL_CONFIRM_CLICK");

      this.submittingStrategy = true;
      try {
        const normalizedForm = this.normalizeStrategyForm(this.strategyForm);
        const bidRange = normalizeBidRange(
          normalizedForm.bidMin,
          normalizedForm.bidMax,
        );
        normalizedForm.bidMin = bidRange.bidMin;
        normalizedForm.bidMax = bidRange.bidMax;
        if (normalizedForm.timeMode === "custom") {
          const normalizedCustomSlotResult = buildNormalizedCustomSlots(
            normalizedForm.customSlots,
          );
          if (normalizedCustomSlotResult.error) {
            showToast(normalizedCustomSlotResult.error);
            return;
          }
          normalizedForm.customSlots = normalizedCustomSlotResult.slots;
          this.strategyForm = {
            ...normalizedForm,
          };
        }

        const payload = {
          strategyConfigJson: JSON.stringify(normalizedForm),
          strategyConfigured: true,
        };
        if (!this.aiPromotionEnabled) {
          const confirmed = await this.confirmEnablePromotionWhenDisabled();
          if (confirmed) {
            payload.promotionEnabled = true;
          }
        }
        const settings = await updateMtH5PromotionSettings(payload);
        this.applyPromotionSettings(settings);
        showToast("推广策略已保存");
        this.closeStrategyModal(false);
      } catch (error) {
        showErrorToast(error, "推广策略保存失败");
      } finally {
        this.submittingStrategy = false;
      }
    },
    mergeAdjacentCustomSlotsForDisplay() {
      const normalizedCustomSlotResult = buildNormalizedCustomSlots(
        this.strategyForm.customSlots,
      );
      if (normalizedCustomSlotResult.error) {
        return;
      }
      if (normalizedCustomSlotResult.changed) {
        this.strategyForm.customSlots = normalizedCustomSlotResult.slots;
      }
    },
    async handleToggle(key) {
      if (this.guideVisible) {
        return;
      }
      if (this.isToggleBusy) {
        return;
      }
      if (key === "aiPromotionEnabled") {
        this.trackBehavior("H5_PROMOTION_SWITCH_CLICK");
      }

      if (
        key === "aiPromotionEnabled" &&
        !this.aiPromotionEnabled &&
        !this.strategyConfigured
      ) {
        await this.promptStrategyConfigBeforeEnable();
        return;
      }

      if (key === "aiPromotionEnabled" && this.aiPromotionEnabled) {
        const confirmResult = await new Promise((resolve) => {
          uni.showModal({
            title: "确认关闭",
            content: "关闭后将同步关闭商家端推广，请谨慎操作！",
            confirmText: "确认关闭",
            cancelText: "取消",
            success: (res) => resolve(Boolean(res.confirm)),
            fail: () => resolve(false),
          });
        });
        if (!confirmResult) {
          return;
        }
      }

      const previousState = {
        aiPromotionEnabled: this.aiPromotionEnabled,
        balanceReminderEnabled: this.balanceReminderEnabled,
      };
      const nextValue = !this[key];
      this[key] = nextValue;
      this.submittingToggleKey = key;

      const featureName = key === "aiPromotionEnabled" ? "AI推广" : "余额提醒";
      const payload =
        key === "aiPromotionEnabled"
          ? { promotionEnabled: nextValue }
          : { balanceReminderEnabled: nextValue };

      try {
        const settings = await updateMtH5PromotionSettings(payload);
        this.applyPromotionSettings(settings);
        showToast(`${featureName}${nextValue ? "已开启" : "已关闭"}`);
      } catch (error) {
        this.aiPromotionEnabled = previousState.aiPromotionEnabled;
        this.balanceReminderEnabled = previousState.balanceReminderEnabled;
        showErrorToast(error, `${featureName}更新失败`);
      } finally {
        this.submittingToggleKey = "";
      }
    },
  },
};
</script>

<style>
page {
  background: linear-gradient(
    180deg,
    #efba4f 0%,
    #f7d581 160rpx,
    #f6f6f8 320rpx,
    #f6f6f8 100%
  );
}
</style>

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

.page-shell-with-recharge {
  padding-bottom: calc(env(safe-area-inset-bottom) + 360rpx);
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

.home-recharge-entry {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(env(safe-area-inset-bottom) + 68rpx);
  z-index: 18;
  display: flex;
  justify-content: center;
  height: 265rpx;
  box-sizing: border-box;
}

.home-recharge-image {
  width: 750rpx;
  height: auto;
  display: block;
}

.home-onboarding-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1004;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: calc(env(safe-area-inset-top) + 20rpx) 16rpx
    calc(env(safe-area-inset-bottom) + 20rpx);
  background: rgba(0, 0, 0, 0.56);
  box-sizing: border-box;
}

.home-onboarding-dialog {
  position: relative;
  width: 720rpx;
  max-width: calc(100vw - 32rpx);
  overflow: hidden;
  box-sizing: border-box;
}

.home-promotion-guide-dialog {
  height: 1060rpx;
}

.home-recharge-guide-dialog {
  height: 720rpx;
}

.home-onboarding-image,
.home-promotion-guide-button {
  position: absolute;
  left: 0;
  width: 100%;
  height: auto;
  display: block;
  pointer-events: none;
}

.home-promotion-guide-dialog .home-onboarding-image,
.home-promotion-guide-button {
  top: -80rpx;
}

.home-recharge-guide-dialog .home-onboarding-image {
  top: -250rpx;
}

.home-promotion-guide-button {
  transform-origin: 50% 82%;
  animation: homeGuideButtonPulse 1.18s ease-in-out infinite;
}

.home-onboarding-close-zone {
  position: absolute;
  top: 28rpx;
  right: 34rpx;
  z-index: 2;
  width: 92rpx;
  height: 92rpx;
}

.home-recharge-guide-dialog .home-onboarding-close-zone {
  top: 78rpx;
  right: 22rpx;
  width: 88rpx;
  height: 88rpx;
}

@keyframes homeGuideButtonPulse {
  0%,
  100% {
    transform: scale(1);
  }

  45% {
    transform: scale(1.075);
  }
}

.section-card {
  margin-bottom: 18rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-shadow: 0 10rpx 30rpx rgba(31, 35, 41, 0.05);
}

.home-guide-active-target {
  position: relative;
  z-index: 1002;
  box-shadow: 0 16rpx 44rpx rgba(0, 0, 0, 0.18);
}

.home-guide-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1001;
  background: rgba(0, 0, 0, 0.46);
}

.home-guide-copy {
  position: fixed;
  left: 56rpx;
  right: 56rpx;
  z-index: 1003;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  pointer-events: auto;
}

.home-guide-title,
.home-guide-desc {
  display: block;
  color: #ffffff;
  font-size: 40rpx;
  line-height: 1.18;
  font-weight: 900;
}

.home-guide-next {
  display: block;
  margin-top: 18rpx;
  color: #ffffff;
  font-size: 26rpx;
  line-height: 1.2;
  font-weight: 700;
}

.stats-card {
  overflow: hidden;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx;
  background: linear-gradient(180deg, #f4eee8 0%, #ede2d6 100%);
}

.section-title {
  font-size: 24rpx;
  color: #62513d;
  font-weight: 600;
}

.section-link {
  font-size: 24rpx;
  color: #62513d;
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

.stat-value-green {
  color: #28c77a;
}

.stat-value-dark {
  color: #2f2f33;
}

.stat-value-red {
  color: #ec5a59;
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

.current-bid-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64rpx;
  gap: 20rpx;
}

.current-bid-title-wrap {
  display: flex;
  align-items: baseline;
  min-width: 0;
  flex: 1;
}

.current-bid-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #2b2b2f;
  white-space: nowrap;
}

.current-bid-subtitle {
  font-size: 22rpx;
  color: #6e7078;
  white-space: nowrap;
}

.current-bid-value-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-shrink: 0;
}

.current-bid-value {
  font-size: 28rpx;
  font-weight: 700;
  color: #6a6d75;
}

.current-bid-value-skipped {
  color: #d19000;
}

.current-bid-value-failed {
  color: #e5484d;
}

.current-bid-edit {
  width: 34rpx;
  height: 34rpx;
}

.current-bid-disabled {
  font-size: 28rpx;
  font-weight: 700;
  color: #9b9da4;
  flex-shrink: 0;
}

.feature-card {
  padding: 24rpx 26rpx;
}

.ai-card {
  padding: 28rpx 28rpx 0;
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

.feature-title-wrap {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
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

.robot-icon-bounce {
  animation: promotionRobotBounce 3s ease-in-out infinite;
}

.warn-icon {
  width: 70rpx;
  height: 70rpx;
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
  animation: promotionToggleGlow 1.5s ease-in-out infinite;
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

@keyframes promotionToggleGlow {
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

@keyframes promotionRobotBounce {
  0%,
  62%,
  100% {
    transform: translateY(0) scale(1);
  }
  7% {
    transform: translateY(-12rpx) scale(1.06);
  }
  14% {
    transform: translateY(0) scale(0.98);
  }
  21% {
    transform: translateY(-8rpx) scale(1.04);
  }
  30% {
    transform: translateY(0) scale(1);
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

.ai-current-bid-main {
  margin: 18rpx -28rpx 0;
  padding: 0 28rpx 28rpx;
}

.ai-current-bid-main .current-bid-title {
  font-size: 32rpx;
}

.ai-current-bid-main .current-bid-subtitle {
  font-size: 24rpx;
}

.ai-current-bid-main .current-bid-value,
.ai-current-bid-main .current-bid-disabled {
  font-size: 30rpx;
}

.tip-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10rpx;
}

.tip-anchor {
  width: 74rpx;
  margin-right: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tip-main {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.tip-badge {
  padding: 4rpx 10rpx;
  border-radius: 10rpx;
  background: #ff4a4a;
  color: #ffffff;
  font-size: 18rpx;
  line-height: 1.3;
  font-weight: 700;
}

.tip-text {
  font-size: 22rpx;
  color: #606167;
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

.outline-button-disabled {
  background: #f6f2ea;
  border-color: #d8d1c6;
  color: #998c75;
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
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx 20rpx;
  background: rgba(88, 63, 11, 0.18);
  box-sizing: border-box;
}

.bid-modal {
  width: 610rpx;
  padding: 34rpx 34rpx 30rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.bid-modal-title {
  display: block;
  margin-bottom: 26rpx;
  font-size: 34rpx;
  font-weight: 700;
  color: #2b2b2f;
  text-align: center;
}

.bid-input-row {
  display: flex;
  align-items: center;
  height: 84rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #f6f6f8;
}

.bid-input {
  flex: 1;
  min-width: 0;
  font-size: 34rpx;
  color: #2b2b2f;
}

.purchase-placeholder {
  color: #b8bac2;
}

.bid-input-unit {
  margin-left: 14rpx;
  font-size: 26rpx;
  color: #6e7078;
}

.bid-modal-tip {
  display: block;
  margin-top: 18rpx;
  font-size: 22rpx;
  line-height: 1.5;
  color: #8d8d94;
}

.bid-modal-actions {
  display: flex;
  gap: 18rpx;
  margin-top: 30rpx;
}

.bid-modal-cancel,
.bid-modal-confirm {
  flex: 1;
  height: 72rpx;
  border-radius: 999rpx;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 72rpx;
  text-align: center;
}

.bid-modal-cancel {
  background: #f1f2f5;
  color: #666a73;
}

.bid-modal-confirm {
  background: #efc548;
  color: #4a3a1c;
}

.bid-modal-confirm-disabled {
  opacity: 0.6;
}

.strategy-modal {
  position: relative;
  width: 100%;
  max-width: 680rpx;
  max-height: 76vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 0;
  border: 2rpx solid rgba(230, 201, 143, 0.9);
  border-radius: 32rpx;
  background: #fffaf2;
  box-sizing: border-box;
}

.strategy-modal-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 32rpx 26rpx 18rpx;
  box-sizing: border-box;
}

.strategy-modal-help {
  position: absolute;
  top: 22rpx;
  right: 24rpx;
  z-index: 4;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  border: 2rpx solid rgba(139, 115, 66, 0.5);
  color: #8b7342;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 40rpx;
  text-align: center;
  background: rgba(255, 248, 232, 0.9);
  box-sizing: border-box;
}

.strategy-section + .strategy-section {
  margin-top: 22rpx;
}

.strategy-section-title {
  display: block;
  margin-bottom: 16rpx;
  font-size: 30rpx;
  font-weight: 700;
  color: #4d3d20;
}

.strategy-section-title-row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.strategy-section-title-row .strategy-section-title {
  margin-bottom: 0;
}

.strategy-peak-button {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44rpx;
  padding: 0 16rpx;
  border-radius: 8rpx;
  background: #ffe8e5;
  color: #d13c32;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 44rpx;
  white-space: nowrap;
}

.strategy-peak-button-locked {
  background: transparent;
  color: #2f2f33;
  padding: 0 4rpx;
}

.strategy-peak-button-disabled {
  opacity: 0.58;
}

.peak-window-tips {
  position: absolute;
  top: 58rpx;
  left: 132rpx;
  z-index: 3;
  width: 340rpx;
  padding: 20rpx 22rpx 18rpx;
  border-radius: 10rpx;
  background: rgba(16, 33, 48, 0.94);
  box-shadow: 0 12rpx 28rpx rgba(20, 28, 38, 0.18);
  box-sizing: border-box;
}

.peak-window-tips-arrow {
  position: absolute;
  top: -14rpx;
  left: 150rpx;
  width: 0;
  height: 0;
  border-right: 14rpx solid transparent;
  border-bottom: 14rpx solid rgba(16, 33, 48, 0.94);
  border-left: 14rpx solid transparent;
}

.peak-window-tips-title {
  display: block;
  margin-bottom: 14rpx;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.2;
}

.peak-window-tips-row {
  display: flex;
  gap: 18rpx;
  margin-top: 10rpx;
}

.peak-window-tips-row-top {
  align-items: flex-start;
}

.peak-window-tips-label {
  width: 108rpx;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.62);
  font-size: 22rpx;
  line-height: 1.25;
}

.peak-window-tips-value,
.peak-window-tips-time {
  color: #ffffff;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.25;
}

.peak-window-tips-times {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8rpx;
}

.peak-window-tips-time {
  display: block;
}

.strategy-option-grid {
  display: flex;
  gap: 16rpx;
}

.strategy-select-card {
  position: relative;
  margin-bottom: 16rpx;
  padding: 20rpx 24rpx;
  border: 2rpx solid #ecd9b1;
  border-radius: 24rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.strategy-half-card {
  flex: 1;
  min-width: 0;
  margin-bottom: 0;
}

.strategy-recommend-title-wrap {
  position: relative;
}

.strategy-recommend-icon {
  position: absolute;
  top: -30rpx;
  right: -54rpx;
  width: 56rpx;
  height: 56rpx;
  z-index: 1;
}

.strategy-simple-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  min-height: 88rpx;
}

.strategy-custom-card,
.strategy-rate-card {
  padding-bottom: 22rpx;
}

.strategy-select-card-active {
  border-color: #efc548;
  background: #fff8e8;
}

.strategy-select-card-disabled {
  background: #fffcf5;
  opacity: 0.92;
}

.strategy-custom-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.strategy-select-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.25;
  color: #2f2f33;
}

.strategy-rate-explanation {
  font-size: 22rpx;
  font-weight: 500;
  color: #8b7342;
}

.strategy-simple-card .strategy-select-title {
  white-space: nowrap;
}

.strategy-time-mode-copy {
  flex: 1;
  min-width: 0;
}

.strategy-time-mode-copy .strategy-select-title {
  white-space: normal;
}

.strategy-select-note {
  font-size: 24rpx;
  font-weight: 500;
  color: #8b7342;
}

.strategy-radio {
  flex-shrink: 0;
  pointer-events: none;
}

.strategy-lock-icon {
  width: 34rpx;
  height: 34rpx;
  flex-shrink: 0;
}

.strategy-unlock-button {
  flex-shrink: 0;
  height: 42rpx;
  padding: 0 4rpx;
  border-radius: 8rpx;
  background: transparent;
  color: #2f2f33;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 42rpx;
}

.strategy-unlock-underline {
  text-decoration: underline;
}

.strategy-unlock-button-disabled {
  opacity: 0.58;
}

.strategy-slot-list {
  margin-top: 16rpx;
}

.strategy-slot-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.strategy-slot-row + .strategy-slot-row {
  margin-top: 14rpx;
}

.strategy-time-pill {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 120rpx;
  height: 48rpx;
  padding: 0 14rpx;
  border-radius: 14rpx;
  background: #f9f3e4;
  color: #23252b;
  font-size: 22rpx;
  font-weight: 600;
  border: 2rpx solid #efdfbc;
  box-sizing: border-box;
}

.strategy-rate-pill {
  min-width: 96rpx;
}

.strategy-time-caret {
  margin-left: 8rpx;
  font-size: 16rpx;
  color: #8b7342;
}

.strategy-slot-dash {
  color: #a18857;
  font-size: 26rpx;
  font-weight: 600;
}

.strategy-remove {
  width: 40rpx;
  height: 40rpx;
  border: 2rpx solid #d5c29d;
  border-radius: 50%;
  color: #8b7342;
  font-size: 28rpx;
  line-height: 34rpx;
  text-align: center;
  background: #fffaf0;
  box-sizing: border-box;
}

.strategy-add-slot {
  margin-top: 16rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #5d4a22;
}

.strategy-rate-range {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 16rpx;
}

.strategy-rate-range-readonly {
  flex-wrap: wrap;
}

.strategy-rate-pill-disabled {
  background: #f5efe0;
  border-color: #e2d5b7;
  color: #7d6a3f;
}

.strategy-rate-readonly-tip {
  width: 100%;
  font-size: 24rpx;
  line-height: 1.5;
  color: #8b7342;
}

.strategy-bid-range-card {
  padding: 22rpx 24rpx 20rpx;
  border: 2rpx solid #ecd9b1;
  border-radius: 24rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.strategy-bid-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
}

.strategy-bid-title-value {
  color: #8b7342;
  font-size: 26rpx;
  font-weight: 800;
  line-height: 1.2;
}

.strategy-bid-range-axis {
  position: relative;
  height: 76rpx;
  margin: 0 4rpx;
}

.strategy-bid-axis-track,
.strategy-bid-axis-active {
  position: absolute;
  top: 34rpx;
  height: 8rpx;
  border-radius: 999rpx;
}

.strategy-bid-axis-track {
  left: 0;
  right: 0;
  background: #eadfc4;
}

.strategy-bid-axis-active {
  background: #efc548;
}

.strategy-bid-axis-thumb {
  position: absolute;
  top: 19rpx;
  z-index: 2;
  width: 38rpx;
  height: 38rpx;
  border: 4rpx solid #efc548;
  border-radius: 50%;
  background: #ffffff;
  box-shadow: 0 6rpx 14rpx rgba(116, 87, 26, 0.18);
  transform: translateX(-50%);
  box-sizing: border-box;
}

.strategy-bid-axis-thumb-active {
  border-color: #d7a920;
  box-shadow: 0 8rpx 18rpx rgba(116, 87, 26, 0.28);
}

.strategy-bid-range-labels {
  display: flex;
  justify-content: space-between;
  color: #8b7342;
  font-size: 22rpx;
  font-weight: 700;
}

.strategy-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 36rpx;
  margin-top: 0;
  padding: 22rpx 26rpx 26rpx;
  border-top: 2rpx solid rgba(236, 217, 177, 0.72);
  background: #fffaf2;
  box-sizing: border-box;
}

.strategy-confirm {
  min-width: 144rpx;
  height: 64rpx;
  padding: 0 34rpx;
  border-radius: 999rpx;
  background: #efc548;
  color: #4a3a1c;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 64rpx;
  text-align: center;
  box-sizing: border-box;
}

.strategy-cancel {
  color: #6b572b;
  font-size: 30rpx;
  font-weight: 700;
}

.auth-failure-modal {
  position: relative;
  width: 100%;
  max-width: 620rpx;
  padding: 52rpx 44rpx 40rpx;
  border-radius: 28rpx;
  background: #ffffff;
  box-sizing: border-box;
  text-align: center;
}

.auth-failure-close {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  color: #8f8a7d;
  font-size: 44rpx;
  font-weight: 300;
  line-height: 52rpx;
  text-align: center;
}

.auth-failure-icon {
  display: block;
  width: 132rpx;
  height: 132rpx;
  margin: 0 auto;
}

.auth-failure-title {
  display: block;
  margin-top: 28rpx;
  color: #2b220f;
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.35;
}

.auth-failure-button {
  margin-top: 42rpx;
  height: 84rpx;
  border-radius: 18rpx;
  background: #f2ce46;
  color: #2a2416;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 84rpx;
  text-align: center;
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
</style>
