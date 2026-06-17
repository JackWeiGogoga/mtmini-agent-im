<template>
  <view class="markdown-preview">
    <view v-if="expanded" class="markdown-expanded">
      <rich-text :nodes="richTextNodes"></rich-text>
    </view>
    <text v-else class="markdown-collapsed">{{ previewText }}</text>
    <text class="markdown-toggle" @tap="toggleExpanded">
      {{ expanded ? "收起详情" : "展开详情" }}
    </text>
  </view>
</template>

<script>
function escapeHtml(text) {
  return String(text || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\"/g, "&quot;");
}

function formatInline(text) {
  return escapeHtml(text).replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
}

function markdownToHtml(markdown) {
  const lines = String(markdown || "")
    .split(/\r?\n/)
    .map((item) => item.trim())
    .filter(Boolean);
  const parts = [];
  let listItems = [];

  const flushList = () => {
    if (!listItems.length) {
      return;
    }
    const items = listItems
      .map(
        (item) =>
          `<li style="margin-bottom:8rpx;padding-left:12rpx;line-height:1.7;">${formatInline(item)}</li>`,
      )
      .join("");
    parts.push(
      `<ul style="margin:0 0 16rpx 0;padding:0 0 0 40rpx;list-style-position:outside;">${items}</ul>`,
    );
    listItems = [];
  };

  lines.forEach((line) => {
    if (line.startsWith("- ")) {
      listItems.push(line.slice(2).trim());
      return;
    }
    flushList();
    parts.push(`<p style="margin:0 0 16rpx;line-height:1.7;">${formatInline(line)}</p>`);
  });
  flushList();
  return parts.join("");
}

function stripMarkdown(markdown) {
  return String(markdown || "")
    .replace(/\*\*(.*?)\*\*/g, "$1")
    .replace(/^[-*]\s+/gm, "")
    .replace(/\n+/g, " ")
    .trim();
}

export default {
  props: {
    markdown: {
      type: String,
      default: "",
    },
    fallbackText: {
      type: String,
      default: "",
    },
  },
  data() {
    return {
      expanded: false,
    };
  },
  computed: {
    content() {
      return (this.markdown || this.fallbackText || "").trim();
    },
    previewText() {
      return stripMarkdown(this.content);
    },
    richTextNodes() {
      return markdownToHtml(this.content);
    },
  },
  methods: {
    toggleExpanded() {
      this.expanded = !this.expanded;
    },
  },
};
</script>

<style scoped>
.markdown-preview {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.markdown-expanded,
.markdown-collapsed {
  font-size: 26rpx;
  line-height: 1.7;
  color: #4a4d57;
}

.markdown-collapsed {
  display: -webkit-box;
  overflow: hidden;
  text-overflow: ellipsis;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.markdown-toggle {
  align-self: flex-start;
  font-size: 24rpx;
  color: #1270ff;
}
</style>
