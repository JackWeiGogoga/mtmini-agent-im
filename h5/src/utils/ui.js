export function showToast(title) {
  uni.showToast({
    title,
    icon: "none",
  });
}

export function showErrorToast(error, fallbackTitle) {
  showToast(error?.message || fallbackTitle);
}
