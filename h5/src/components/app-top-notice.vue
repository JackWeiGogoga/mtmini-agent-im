<template>
  <view
    v-if="noticeMessage"
    class="top-notice"
    :class="{ 'top-notice-safe': safeTop }"
  >
    <image
      class="top-notice-icon"
      :src="iconSrc"
      mode="aspectFit"
    ></image>
    <text class="top-notice-text">{{ noticeMessage }}</text>
  </view>
  <view v-else-if="safeTop" class="top-notice-safe-placeholder"></view>
</template>

<script>
const DEFAULT_SEEDING_MESSAGE =
  "大胆放心地把工作交给AI，因为他会比你做得更好！";

export default {
  props: {
    offer: {
      type: Object,
      default: null,
    },
    message: {
      type: String,
      default: DEFAULT_SEEDING_MESSAGE,
    },
    safeTop: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      remainingSeconds: 0,
      countdownTimer: null,
    };
  },
  computed: {
    iconSrc() {
      return this.offer?.eligible && this.remainingSeconds > 0
        ? "/static/icons/info.svg"
        : "/static/icons/like.svg";
    },
    noticeMessage() {
      if (!this.offer?.eligible || this.remainingSeconds <= 0) {
        return this.message || DEFAULT_SEEDING_MESSAGE;
      }
      return `首充算力：立享8折优惠！（仅1次）剩余： ${this.formatRemainingTime(this.remainingSeconds)}`;
    },
  },
  watch: {
    offer: {
      immediate: true,
      deep: true,
      handler() {
        this.syncCountdown();
      },
    },
  },
  beforeUnmount() {
    this.clearCountdownTimer();
  },
  methods: {
    syncCountdown() {
      this.clearCountdownTimer();
      if (!this.offer?.eligible) {
        this.remainingSeconds = 0;
        return;
      }

      const nextSeconds = Math.max(
        Number(this.offer?.remainingSeconds || 0),
        0,
      );
      this.remainingSeconds = nextSeconds;
      if (nextSeconds <= 0) {
        return;
      }

      this.countdownTimer = setInterval(() => {
        if (this.remainingSeconds <= 1) {
          this.remainingSeconds = 0;
          this.clearCountdownTimer();
          return;
        }
        this.remainingSeconds -= 1;
      }, 1000);
    },
    clearCountdownTimer() {
      if (!this.countdownTimer) {
        return;
      }
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    },
    formatRemainingTime(totalSeconds) {
      const safeSeconds = Math.max(Number(totalSeconds || 0), 0);
      const days = Math.floor(safeSeconds / 86400);
      const hours = Math.floor((safeSeconds % 86400) / 3600);
      const minutes = Math.floor((safeSeconds % 3600) / 60);
      const seconds = safeSeconds % 60;
      const dayPrefix = days > 0 ? `${days}天` : "";
      return `${dayPrefix}${String(hours).padStart(2, "0")}时${String(minutes).padStart(2, "0")}分${String(seconds).padStart(2, "0")}秒`;
    },
  },
};
</script>

<style scoped>
.top-notice {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 16rpx 18rpx;
  background: #ff1b10;
  box-shadow: none;
}

.top-notice-safe {
  padding-top: calc(env(safe-area-inset-top) + 16rpx);
}

.top-notice-safe-placeholder {
  height: env(safe-area-inset-top);
}

.top-notice-icon {
  width: 30rpx;
  height: 30rpx;
  flex-shrink: 0;
  margin-top: 2rpx;
}

.top-notice-text {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
  line-height: 1.35;
  font-weight: 500;
  color: #ffffff;
  white-space: normal;
}
</style>
