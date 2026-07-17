export function showToast(title) {
  uni.showToast({
    title,
    icon: "none",
  });
}

export function showErrorToast(error, fallbackTitle) {
  showToast(error?.message || fallbackTitle);
}

export function openMtExternalPage(url) {
  const targetUrl = String(url || "").trim();
  if (!targetUrl || typeof window === "undefined") {
    showToast("请在美团商家端打开活动页面");
    return;
  }

  if (window.parent && window.parent !== window) {
    window.parent.postMessage(
      { action: "isvJumpToSalePage", url: targetUrl },
      "*",
    );
    return;
  }

  window.location.href = targetUrl;
}
