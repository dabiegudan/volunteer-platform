/**
 * 测试环境初始化
 * 模拟 UniApp 全局 API
 */

// 模拟 uni 对象
global.uni = {
  // Storage
  getStorageSync: (key) => {
    const data = global.__storage__ || {}
    return data[key] || null
  },
  setStorageSync: (key, value) => {
    if (!global.__storage__) global.__storage__ = {}
    global.__storage__[key] = value
  },
  removeStorageSync: (key) => {
    if (global.__storage__) {
      delete global.__storage__[key]
    }
  },
  getStorageInfoSync: () => ({
    keys: Object.keys(global.__storage__ || {}),
    currentSize: 0,
    limitSize: 0
  }),

  // UI
  showToast: () => {},
  showLoading: () => {},
  hideLoading: () => {},
  showModal: () => {},
  navigateTo: () => {},
  navigateBack: () => {},
  switchTab: () => {},
  reLaunch: () => {},

  // System
  getSystemInfoSync: () => ({
    windowWidth: 375,
    windowHeight: 667,
    platform: 'ios'
  }),

  // Network
  request: (options) => {
    // 默认模拟成功响应
    if (options.success) {
      options.success({
        statusCode: 200,
        data: {
          code: 200,
          message: '操作成功',
          data: options.data || {}
        }
      })
    }
  }
}
