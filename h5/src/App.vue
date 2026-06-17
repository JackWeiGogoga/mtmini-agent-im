<script>
function redirectMtLaunchEntry() {
  if (typeof window === "undefined") {
    return;
  }

  const search = window.location.search || "";
  if (!search) {
    return;
  }

  const params = new URLSearchParams(search);
  const hasMtLaunchParams = ["wmPoiId", "bizToken", "platform", "appKey"].some(
    (key) => params.has(key),
  );
  if (!hasMtLaunchParams) {
    return;
  }

  const hash = window.location.hash || "";
  if (hash.startsWith("#/pages/auth/callback/index")) {
    return;
  }

  const target = `${window.location.origin}${window.location.pathname}#/pages/auth/callback/index${search}`;
  window.location.replace(target);
}

export default {
  onLaunch: function () {
    redirectMtLaunchEntry();
  },
  onShow: function () {
    redirectMtLaunchEntry();
  },
  onHide: function () {},
};
</script>

<style>
/*每个页面公共css */
</style>
