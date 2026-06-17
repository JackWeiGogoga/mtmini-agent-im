import request from "@/utils/request";
import { API_PREFIX } from "@/utils/env";
import type {
  AdminBehaviorEventType,
  UserBehaviorAnalyticsPage,
  UserBehaviorAnalyticsQuery,
} from "@/types";

export async function getUserBehaviorAnalytics(
  params: UserBehaviorAnalyticsQuery,
): Promise<UserBehaviorAnalyticsPage> {
  const response = await request.get<
    unknown,
    { data: UserBehaviorAnalyticsPage }
  >("/system/behavior-analytics", {
    params,
  });
  return response.data;
}

export function reportAdminBehaviorEvent(
  eventType: AdminBehaviorEventType,
  shopId: number,
  context?: unknown,
): void {
  const body: Record<string, unknown> = {
    eventType,
    shopId,
  };
  if (context !== undefined) {
    body.contextJson =
      typeof context === "string" ? context : JSON.stringify(context);
  }

  void fetch(`${API_PREFIX}/api/system/behavior-events`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  }).catch(() => {
    // 埋点失败不能影响管理后台主流程。
  });
}
