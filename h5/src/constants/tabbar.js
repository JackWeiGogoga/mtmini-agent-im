const TABBAR_ITEMS = [
  {
    key: "home",
    label: "首页",
    path: "/pages/index/index",
    icon: "/static/tabbar/home.png",
    activeIcon: "/static/tabbar/home-fill.png",
  },
  {
    key: "logs",
    label: "日志",
    path: "/pages/reply-logs/index",
    icon: "/static/tabbar/log.png",
    activeIcon: "/static/tabbar/log-fill.png",
  },
  {
    key: "power",
    label: "算力",
    path: "/pages/power/index",
    icon: "/static/tabbar/power.svg",
    activeIcon: "/static/tabbar/power-fill.svg",
  },
  {
    key: "more",
    label: "更多",
    externalUrl:
      "https://waimaieapp.meituan.com/ad/mycraft/activity?MYCRAFT_PAGE_ID=2486&appId=138",
    icon: "/static/tabbar/more.svg",
    activeIcon: "/static/tabbar/more-fill.svg",
  },
  {
    key: "support",
    label: "客服",
    path: "/pages/support/index",
    icon: "/static/tabbar/qa.png",
    activeIcon: "/static/tabbar/qa-fill.png",
  },
];

export function buildTabbarItems(activeKey) {
  return TABBAR_ITEMS.map((item) => ({
    ...item,
    active: item.key === activeKey,
    currentIcon: item.key === activeKey ? item.activeIcon : item.icon,
  }));
}
