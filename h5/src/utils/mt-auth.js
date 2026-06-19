const TOKEN_KEY = 'token'
const SHOP_INFO_KEY = 'shopInfo'
const VERSION_KEY = 'version'
const HOME_GUIDE_STEP_KEY_PREFIX = 'mt_h5_home_guide_step:'
const HOME_GUIDE_DISMISSED_KEY_PREFIX = 'mt_h5_home_guide_dismissed:'

function request(url, method = 'GET', data, withAuth = true) {
  const header = {
    'Content-Type': 'application/json',
  }

  if (withAuth) {
    const token = getToken()
    if (token) {
      header.Authorization = `Bearer ${token}`
    }
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method,
      data,
      header,
      success: (response) => {
        const { statusCode, data: body } = response
        if (statusCode !== 200) {
          reject(new Error(body?.msg || '请求失败'))
          return
        }
        if (body?.code !== 200) {
          reject(new Error(body?.msg || '请求失败'))
          return
        }
        resolve(body.data)
      },
      fail: (error) => {
        reject(new Error(error?.errMsg || '网络异常，请稍后重试'))
      },
    })
  })
}

export function loginWithMtH5Params(params) {
  return request('/api/mt/auth/login', 'POST', params, false)
}

export function clearAuthPayload() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(SHOP_INFO_KEY)
  uni.removeStorageSync(VERSION_KEY)
}

export function getMtH5HomeInfo() {
  return request('/api/h5/shop/home', 'GET')
}

export function getMtH5HomeDashboard(statsRange = 'TODAY') {
  return request('/api/h5/shop/home/dashboard', 'GET', { statsRange })
}

export function reportMtH5BehaviorEvent(eventType, context = undefined) {
  const payload = {
    eventType,
  }
  if (context !== undefined) {
    payload.contextJson =
      typeof context === 'string' ? context : JSON.stringify(context)
  }
  request('/api/h5/behavior-events', 'POST', payload).catch(() => {
    // 埋点失败不能影响 H5 主流程。
  })
}

export function getMtH5TabStatus() {
  return request('/api/h5/shop/tab-status', 'GET')
}

export function completeMtH5Guide(guideCode) {
  return request(`/api/h5/shop/guides/${guideCode}/complete`, 'POST')
}

export function getMtH5PowerSummary(redemptionCode) {
  return request(
    '/api/h5/power/summary',
    'GET',
    redemptionCode ? { redemptionCode } : undefined,
  )
}

export function createMtH5PowerRecharge(powerAmount, redemptionCode) {
  return request('/api/h5/power/recharges', 'POST', { powerAmount, redemptionCode })
}

export function getMtH5PowerRechargeResult(rechargeNo) {
  return request(`/api/h5/power/recharges/${rechargeNo}`, 'GET')
}

export function mockSuccessMtH5PowerRecharge(rechargeNo) {
  return request(`/api/h5/power/recharges/${rechargeNo}/mock-success`, 'POST')
}

export function saveAuthPayload(payload) {
  uni.setStorageSync(TOKEN_KEY, payload?.token || '')
  uni.setStorageSync(SHOP_INFO_KEY, payload?.shopInfo || {})
  uni.setStorageSync(VERSION_KEY, String(payload?.version ?? 0))
}

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getShopInfo() {
  return uni.getStorageSync(SHOP_INFO_KEY) || {}
}

export function getVersion() {
  return uni.getStorageSync(VERSION_KEY) || '0'
}

function normalizeGuideKey(guideCode, shopKey) {
  const normalizedGuideCode = guideCode ? String(guideCode) : 'unknown_guide'
  const normalizedShopKey = shopKey ? String(shopKey) : 'unknown_shop'
  return `${normalizedGuideCode}:${normalizedShopKey}`
}

export function getMtH5HomeGuideStep(guideCode, shopKey) {
  return uni.getStorageSync(`${HOME_GUIDE_STEP_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`) || ''
}

export function setMtH5HomeGuideStep(guideCode, shopKey, step) {
  uni.setStorageSync(`${HOME_GUIDE_STEP_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`, step || '')
}

export function clearMtH5HomeGuideStep(guideCode, shopKey) {
  uni.removeStorageSync(`${HOME_GUIDE_STEP_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`)
}

export function isMtH5HomeGuideDismissed(guideCode, shopKey) {
  return uni.getStorageSync(`${HOME_GUIDE_DISMISSED_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`) === '1'
}

export function dismissMtH5HomeGuide(guideCode, shopKey) {
  uni.setStorageSync(`${HOME_GUIDE_DISMISSED_KEY_PREFIX}${normalizeGuideKey(guideCode, shopKey)}`, '1')
}
