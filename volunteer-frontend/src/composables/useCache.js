/**
 * 前端缓存 Composable
 * 基于 UniApp Storage 的通用缓存方案
 */

const DEFAULT_TTL = 5 * 60 * 1000 // 5分钟

/**
 * 从缓存读取
 * @param {string} key 缓存键
 * @param {number} ttl 过期时间(毫秒)
 * @returns {*} 缓存数据，过期或不存在返回 null
 */
export function getCache(key, ttl = DEFAULT_TTL) {
  try {
    const cached = uni.getStorageSync(key)
    if (cached && cached.timestamp) {
      if (Date.now() - cached.timestamp < ttl) {
        return cached.value
      }
      // 过期了，清除
      uni.removeStorageSync(key)
      return null
    }
    return null
  } catch (e) {
    console.error('getCache error:', e)
    return null
  }
}

/**
 * 写入缓存
 * @param {string} key 缓存键
 * @param {*} value 缓存值
 */
export function setCache(key, value) {
  try {
    uni.setStorageSync(key, {
      value,
      timestamp: Date.now()
    })
  } catch (e) {
    console.error('setCache error:', e)
  }
}

/**
 * 清除缓存
 * @param {string} key 缓存键
 */
export function removeCache(key) {
  try {
    uni.removeStorageSync(key)
  } catch (e) {
    console.error('removeCache error:', e)
  }
}

/**
 * 清除匹配模式的所有缓存
 * @param {string} prefix 缓存键前缀
 */
export function clearCacheByPrefix(prefix) {
  try {
    const { keys } = uni.getStorageInfoSync()
    keys.forEach(key => {
      if (key.startsWith(prefix)) {
        uni.removeStorageSync(key)
      }
    })
  } catch (e) {
    console.error('clearCacheByPrefix error:', e)
  }
}

export { DEFAULT_TTL }
