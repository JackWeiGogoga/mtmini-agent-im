const TOKEN_KEY = "token";
const SHOP_INFO_KEY = "shopInfo";
const VERSION_KEY = "version";
const HOME_GUIDE_STEP_KEY_PREFIX = "mt_h5_home_guide_step:";
const HOME_GUIDE_DISMISSED_KEY_PREFIX = "mt_h5_home_guide_dismissed:";
let behaviorEventSequence = 0;
let behaviorSessionId = createBehaviorId("h5-session");
let appOpenReported = false;

function createBehaviorId(prefix) {
  behaviorEventSequence += 1;
  const randomPart = Math.random().toString(36).slice(2, 10);
  return `${prefix}-${Date.now().toString(36)}-${behaviorEventSequence.toString(36)}-${randomPart}`;
}

function resetBehaviorSession() {
  behaviorSessionId = createBehaviorId("h5-session");
  appOpenReported = false;
}

function request(url, method = "GET", data, withAuth = true) {
  const header = {
    "Content-Type": "application/json",
  };

  if (withAuth) {
    const token = getToken();
    if (token) {
      header.Authorization = `Bearer ${token}`;
    }
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data,
      header,
      success: (response) => {
        const { statusCode, data: body } = response;
        if (statusCode !== 200) {
          reject(new Error(body?.msg || "请求失败"));
          return;
        }
        if (body?.code !== 200) {
          reject(new Error(body?.msg || "请求失败"));
          return;
        }
        resolve(body.data);
      },
      fail: (error) => {
        reject(new Error(error?.errMsg || "网络异常，请稍后重试"));
      },
    });
  });
}

export function loginWithMtH5Params(params) {
  return request("/api/mt/auth/login", "POST", params, false);
}

export function clearAuthPayload() {
  uni.removeStorageSync(TOKEN_KEY);
  uni.removeStorageSync(SHOP_INFO_KEY);
  uni.removeStorageSync(VERSION_KEY);
  resetBehaviorSession();
}

export function getMtH5HomeInfo() {
  return request("/api/h5/shop/home", "GET");
}

export function getMtH5HomeDashboard(statsRange = "TODAY") {
  return request("/api/h5/shop/home/dashboard", "GET", { statsRange });
}

export function updateMtH5OperationSettings(settings) {
  return request("/api/h5/shop/operation-settings", "PUT", settings);
}

export function getMtH5ContactPhone() {
  return request("/api/h5/shop/contact-phone", "GET");
}

export function updateMtH5ContactPhone(customContactPhone) {
  return request("/api/h5/shop/contact-phone", "PUT", { customContactPhone });
}

export function getMtH5BroadcastSetting() {
  return request("/api/h5/shop/broadcast-setting", "GET");
}

export function saveMtH5BroadcastSetting(payload) {
  return request("/api/h5/shop/broadcast-setting", "PUT", payload);
}

export function deleteMtH5BroadcastSetting() {
  return request("/api/h5/shop/broadcast-setting", "DELETE");
}

export function getKnowledgeQaItems() {
  return request("/api/h5/knowledge/items", "GET");
}

export function createKnowledgeQaItem(payload) {
  return request("/api/h5/knowledge/items", "POST", payload);
}

export function updateKnowledgeQaItem(id, payload) {
  return request(`/api/h5/knowledge/items/${id}`, "PUT", payload);
}

export function deleteKnowledgeQaItem(id) {
  return request(`/api/h5/knowledge/items/${id}`, "DELETE");
}

export function clearKnowledge() {
  return request("/api/h5/knowledge/items/all", "DELETE");
}

export function getKnowledgeDocuments() {
  return request("/api/h5/knowledge/documents", "GET");
}

export function deleteKnowledgeDocument(id) {
  return request(`/api/h5/knowledge/documents/${id}`, "DELETE");
}

export function getKnowledgeDocumentChunks(id) {
  return request(`/api/h5/knowledge/documents/${id}/chunks`, "GET");
}

export function updateKnowledgeChunkEnabled(chunkId, enabled) {
  return request(
    `/api/h5/knowledge/documents/chunks/${chunkId}/enabled?enabled=${enabled ? "true" : "false"}`,
    "PUT",
  );
}

export function deleteKnowledgeChunk(chunkId) {
  return request(`/api/h5/knowledge/documents/chunks/${chunkId}`, "DELETE");
}

export function uploadKnowledgeDocument(filePath, name = "file") {
  return uploadKnowledgeFile(
    "/api/h5/knowledge/documents/upload",
    filePath,
    name,
    "上传失败",
  );
}

export function previewKnowledgeDocument(filePath, name = "file") {
  return uploadKnowledgeFile(
    "/api/h5/knowledge/documents/preview",
    filePath,
    name,
    "解析预览失败",
  );
}

function uploadKnowledgeFile(url, filePath, name, defaultErrorMessage) {
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url,
      filePath,
      name,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (response) => {
        let body = response.data;
        if (typeof body === "string") {
          try {
            body = JSON.parse(body);
          } catch (error) {
            reject(new Error("上传响应解析失败"));
            return;
          }
        }
        if (response.statusCode !== 200 || body?.code !== 200) {
          reject(new Error(body?.msg || defaultErrorMessage));
          return;
        }
        resolve(body.data);
      },
      fail: (error) => reject(new Error(error?.errMsg || defaultErrorMessage)),
    });
  });
}

export function reportMtH5BehaviorEvent(eventType, context = undefined) {
  const payload = {
    eventType,
    clientEventId: createBehaviorId("h5-event"),
    sessionId: behaviorSessionId,
  };
  if (context !== undefined) {
    payload.contextJson =
      typeof context === "string" ? context : JSON.stringify(context);
  }
  request("/api/h5/behavior-events", "POST", payload).catch(() => {
    // 埋点失败不能影响 H5 主流程。
  });
}

export function reportMtH5PageView(pageCode, entrySource = "DIRECT") {
  const normalizedEntrySource = String(entrySource || "DIRECT").toUpperCase();
  if (!appOpenReported) {
    appOpenReported = true;
    reportMtH5BehaviorEvent("H5_APP_OPEN", {
      entrySource: normalizedEntrySource,
    });
  }
  reportMtH5BehaviorEvent("H5_PAGE_VIEW", {
    pageCode: String(pageCode || "UNKNOWN").toUpperCase(),
    entrySource: normalizedEntrySource,
  });
}

export function getMtH5TabStatus() {
  return request("/api/h5/shop/tab-status", "GET");
}

export function getMtH5ImAutoReplyLogs(params = {}) {
  return request("/api/h5/im-auto-replies/messages", "GET", params);
}

export function getMtH5ImConversations(params = {}) {
  return request("/api/h5/im-auto-replies/conversations", "GET", params);
}

export function getMtH5ImConversationHistory(conversationId, params = {}) {
  return request(
    `/api/h5/im-auto-replies/conversations/${conversationId}/messages`,
    "GET",
    params,
  );
}

export function completeMtH5Guide(guideCode) {
  return request(`/api/h5/shop/guides/${guideCode}/complete`, "POST");
}

export function getMtH5PowerSummary(redemptionCode) {
  return request(
    "/api/h5/power/summary",
    "GET",
    redemptionCode ? { redemptionCode } : undefined,
  );
}

export function createMtH5PowerRecharge(powerAmount, redemptionCode) {
  return request("/api/h5/power/recharges", "POST", {
    powerAmount,
    redemptionCode,
  });
}

export function getMtH5PowerRechargeResult(rechargeNo) {
  return request(`/api/h5/power/recharges/${rechargeNo}`, "GET");
}

export function mockSuccessMtH5PowerRecharge(rechargeNo) {
  return request(`/api/h5/power/recharges/${rechargeNo}/mock-success`, "POST");
}

export function saveAuthPayload(payload) {
  uni.setStorageSync(TOKEN_KEY, payload?.token || "");
  uni.setStorageSync(SHOP_INFO_KEY, payload?.shopInfo || {});
  uni.setStorageSync(VERSION_KEY, String(payload?.version ?? 0));
}

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || "";
}

export function getShopInfo() {
  return uni.getStorageSync(SHOP_INFO_KEY) || {};
}

export function getVersion() {
  return uni.getStorageSync(VERSION_KEY) || "0";
}

function normalizeGuideKey(guideCode, shopKey) {
  const normalizedGuideCode = guideCode ? String(guideCode) : "unknown_guide";
  const normalizedShopKey = shopKey ? String(shopKey) : "unknown_shop";
  return `${normalizedGuideCode}:${normalizedShopKey}`;
}

export function getMtH5HomeGuideStep(guideCode, shopKey) {
  return (
    uni.getStorageSync(
      `${HOME_GUIDE_STEP_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`,
    ) || ""
  );
}

export function setMtH5HomeGuideStep(guideCode, shopKey, step) {
  uni.setStorageSync(
    `${HOME_GUIDE_STEP_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`,
    step || "",
  );
}

export function clearMtH5HomeGuideStep(guideCode, shopKey) {
  uni.removeStorageSync(
    `${HOME_GUIDE_STEP_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`,
  );
}

export function isMtH5HomeGuideDismissed(guideCode, shopKey) {
  return (
    uni.getStorageSync(
      `${HOME_GUIDE_DISMISSED_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`,
    ) === "1"
  );
}

export function dismissMtH5HomeGuide(guideCode, shopKey) {
  uni.setStorageSync(
    `${HOME_GUIDE_DISMISSED_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`,
    "1",
  );
}
