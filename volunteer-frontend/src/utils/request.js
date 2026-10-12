/**
 * 后端接口基址。
 *
 * 【修复的缺陷】原实现恒为 `import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081'`，
 * 而构建流水线（根目录 `Dockerfile`）只 COPY 后端模块、从不构建前端，也就从不注入
 * `VITE_API_BASE_URL`。结果是 `dist/build/h5` 产物里被硬编码成 `http://localhost:8081`：
 * 浏览器从服务器打开页面后，所有接口请求都打向**访问者自己的机器**，线上功能整体不可用。
 *
 * 现在的策略：
 * - 生产构建：返回空串，走**同源相对路径**（`/api/...`）。nginx 已把 `/api` 反代到后端，
 *   因此无需在构建期注入任何常量，任何域名/端口/子路径部署都能直接工作。
 * - 开发构建：仍可用 `VITE_API_BASE_URL` 覆盖；未设置时用 `http://localhost:8081`，
 *   同时 `vite.config.ts` 的 devServer 已配置 `/api` 代理，CORS 不再依赖后端白名单。
 */
const isProd = import.meta.env.PROD === true

export const API_BASE_URL = isProd
  ? ''
  : (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081')

const ERROR_CODES = {
  400: '请求参数错误',
  401: '未授权，请重新登录',
  403: '无权限访问',
  404: '请求资源不存在',
  408: '请求超时',
  429: '请求过于频繁，请稍后重试',
  460: '请先完善个人档案资料后再进行操作',
  500: '服务器内部错误',
  502: '网关错误',
  503: '服务暂不可用',
  504: '网关超时'
}

const BUSINESS_ERROR_CODES = {
  1001: '活动名额已满',
  1002: '您已报名该活动',
  1003: '活动已结束',
  1004: '不在报名时间内',
  1005: '信用分不足',
  1006: '您已被限制报名'
}

const pendingRequests = new Map()
let loadingCount = 0
let accessRedirecting = false

function redirectToAccessPage(type) {
  const routeMap = {
    STUDENT_ID_REQUIRED: '/pages/profile/bind-student',
    PROFILE_INCOMPLETE: '/pages/profile/edit'
  }
  const url = routeMap[type]
  if (!url || accessRedirecting) return

  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const current = pages[pages.length - 1]?.route
  if (current && url === `/${current}`) return

  const adminDrawerEvents = {
    STUDENT_ID_REQUIRED: 'admin:open-bind-student-drawer',
    PROFILE_INCOMPLETE: 'admin:open-profile-drawer'
  }
  const adminDrawerEvent = adminDrawerEvents[type]
  if (adminDrawerEvent && current === 'pages/index/index') {
    accessRedirecting = true
    uni.$emit(adminDrawerEvent)
    setTimeout(() => {
      accessRedirecting = false
    }, 350)
    return
  }

  accessRedirecting = true
  setTimeout(() => {
    uni.navigateTo({
      url,
      complete: () => {
        accessRedirecting = false
      }
    })
  }, 350)
}

function getAccessErrorType(code, statusCode, message) {
  if (String(message || '').includes('绑定学号')) return 'STUDENT_ID_REQUIRED'
  if (code === 460 || statusCode === 460) return 'PROFILE_INCOMPLETE'
  return ''
}

function getResponseMessage(responseData, fallback = '请求失败') {
  if (responseData && typeof responseData === 'object') {
    const businessMessage = BUSINESS_ERROR_CODES[responseData.code]
    return businessMessage || responseData.message || responseData.msg || fallback
  }
  return fallback
}

function showErrorToast(message, options) {
  if (options.showErrorToast === false) return
  uni.showToast({
    title: message,
    icon: 'none',
    duration: 2500
  })
}

function showLoading(title = '加载中...') {
  loadingCount++
  if (loadingCount === 1) {
    uni.showLoading({ title, mask: true })
  }
}

function hideLoading() {
  loadingCount--
  if (loadingCount <= 0) {
    loadingCount = 0
    uni.hideLoading()
  }
}

function getRequestKey(config) {
  const { url, method, data } = config
  return `${method}:${url}:${JSON.stringify(data || {})}`
}

function removePending(config) {
  const key = getRequestKey(config)
  if (pendingRequests.has(key)) {
    const abort = pendingRequests.get(key)
    abort()
    pendingRequests.delete(key)
  }
}

export function request(options) {
  if (options.deduplicate !== false) {
    removePending(options)
  }
  
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('user_token')

    if (options.requiresAuth !== false && !token) {
      uni.reLaunch({ url: '/pages/login/index' })
      reject(new Error('未登录'))
      return
    }

    if (options.showLoading !== false) {
      showLoading()
    }

    const requestTask = uni.request({
      url: API_BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      timeout: options.timeout || 30000,
      responseType: options.responseType,
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        ...options.header
      },
      success: (res) => {
        const key = getRequestKey(options)
        pendingRequests.delete(key)

        if (options.showLoading !== false) {
          hideLoading()
        }

        if (res.statusCode === 200) {
          const responseData = res.data

          if (responseData && typeof responseData === 'object' && 'code' in responseData) {
            if (responseData.code === 200 || responseData.code === 0) {
              resolve(responseData.data)
              return
            }

            const errorMessage = getResponseMessage(responseData)
            const accessType = getAccessErrorType(responseData.code, res.statusCode, errorMessage)
            if (!options.skipAccessRedirect) redirectToAccessPage(accessType)
            showErrorToast(errorMessage, options)
            reject({
              type: accessType || 'BUSINESS_ERROR',
              code: responseData.code,
              message: errorMessage
            })
            return
          }

          resolve(responseData)
          return
        }

        if (res.statusCode === 401) {
          uni.removeStorageSync('user_token')
          uni.removeStorageSync('user_info')
          uni.showModal({
            title: '登录已过期',
            content: '请重新登录',
            showCancel: false,
            success: () => {
              uni.reLaunch({ url: '/pages/login/index' })
            }
          })
          reject(new Error('登录已过期'))
          return
        }

        if (res.statusCode === 429) {
          showErrorToast('请求过于频繁，请稍后重试', options)
          reject({ type: 'RATE_LIMIT', code: 429, message: '请求过于频繁' })
          return
        }

        const responseData = res.data || {}
        const fallback = ERROR_CODES[res.statusCode] || `网络错误 ${res.statusCode}`
        const message = getResponseMessage(responseData, fallback)
        const code = responseData.code || res.statusCode
        const accessType = getAccessErrorType(code, res.statusCode, message)
        if (!options.skipAccessRedirect) redirectToAccessPage(accessType)
        showErrorToast(message, options)
        reject({
          type: accessType || 'HTTP_ERROR',
          code,
          httpStatus: res.statusCode,
          message,
          data: responseData.data
        })
      },
      fail: (err) => {
        const key = getRequestKey(options)
        pendingRequests.delete(key)

        if (options.showLoading !== false) {
          hideLoading()
        }

        let message = '网络连接失败'

        if (err.errMsg) {
          if (err.errMsg.includes('timeout')) {
            message = '请求超时，请稍后重试'
          } else if (err.errMsg.includes('network')) {
            message = '网络不可用，请检查网络'
          }
        }

        showErrorToast(message, options)

        reject({
          type: 'NETWORK_ERROR',
          message,
          timestamp: Date.now()
        })
      }
    })

    if (options.deduplicate !== false) {
      const key = getRequestKey(options)
      pendingRequests.set(key, () => {
        requestTask.abort()
      })
    }
  })
}

export function get(url, data, options = {}) {
  return request({ ...options, url, method: 'GET', data })
}

export function post(url, data, options = {}) {
  return request({ ...options, url, method: 'POST', data })
}

export function put(url, data, options = {}) {
  return request({ ...options, url, method: 'PUT', data })
}

export function del(url, data, options = {}) {
  return request({ ...options, url, method: 'DELETE', data })
}

export function showGlobalLoading(title = '加载中...') {
  showLoading(title)
}

export function hideGlobalLoading() {
  hideLoading()
}

export function cancelAllRequests() {
  pendingRequests.forEach((abort) => {
    try { abort() } catch (e) {}
  })
  pendingRequests.clear()
}

export async function requestWithRetry(options, maxRetries = 3) {
  let lastError

  for (let i = 0; i < maxRetries; i++) {
    try {
      return await request(options)
    } catch (error) {
      lastError = error

      if ((error.type === 'NETWORK_ERROR' || error.type === 'RATE_LIMIT') && i < maxRetries - 1) {
        await new Promise(r => setTimeout(r, 1000 * (i + 1)))
        continue
      }

      throw error
    }
  }

  throw lastError
}
