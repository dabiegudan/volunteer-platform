/**
 * 通用分页 Composable
 * 提供分页数据加载、缓存、加载状态管理
 */
import { ref } from 'vue'

export function usePagination(options) {
  const {
    fetchFn,
    defaultPageSize = 10,
    cacheKey,
    cacheTime = 5 * 60 * 1000 // 5分钟
  } = options

  const data = ref([])
  const loading = ref(false)
  const error = ref(null)
  const pageNum = ref(1)
  const pageSize = ref(defaultPageSize)
  const total = ref(0)
  const hasMore = ref(true)
  const isRefreshing = ref(false)

  // 从缓存读取
  function getFromCache() {
    if (!cacheKey) return null
    try {
      const cached = uni.getStorageSync(cacheKey)
      if (cached && Date.now() - cached.timestamp < cacheTime) {
        return cached.data
      }
    } catch (e) {
      console.error('Cache read error:', e)
    }
    return null
  }

  // 写入缓存
  function setToCache(cacheData) {
    if (!cacheKey) return
    try {
      uni.setStorageSync(cacheKey, {
        data: cacheData,
        timestamp: Date.now()
      })
    } catch (e) {
      console.error('Cache write error:', e)
    }
  }

  // 清除缓存
  function clearCache() {
    if (cacheKey) {
      try {
        uni.removeStorageSync(cacheKey)
      } catch (e) {
        console.error('Cache clear error:', e)
      }
    }
  }

  // 加载数据
  async function loadData(refresh = false) {
    if (loading.value) return

    loading.value = true
    error.value = null

    try {
      if (refresh) {
        pageNum.value = 1
        hasMore.value = true
        clearCache()
      }

      // 刷新时尝试从缓存获取
      if (refresh && cacheKey) {
        const cached = getFromCache()
        if (cached) {
          data.value = cached.data
          total.value = cached.total
          hasMore.value = cached.hasMore
          loading.value = false
          return
        }
      }

      const result = await fetchFn({
        pageNum: pageNum.value,
        pageSize: pageSize.value
      })

      const newData = result.list || result.records || result || []

      if (refresh) {
        data.value = newData
      } else {
        data.value = [...data.value, ...newData]
      }

      total.value = result.total || 0
      hasMore.value = data.value.length < total.value

      if (hasMore.value) {
        pageNum.value++
      }

      // 缓存结果
      if (cacheKey && refresh) {
        setToCache({
          data: data.value,
          total: total.value,
          hasMore: hasMore.value
        })
      }
    } catch (err) {
      error.value = err
      console.error('loadData error:', err)
    } finally {
      loading.value = false
      isRefreshing.value = false
    }
  }

  // 下拉刷新
  async function refresh() {
    isRefreshing.value = true
    await loadData(true)
    isRefreshing.value = false
  }

  // 加载更多
  async function loadMore() {
    await loadData(false)
  }

  // 重置
  function reset() {
    data.value = []
    pageNum.value = 1
    hasMore.value = true
    error.value = null
    clearCache()
  }

  return {
    data,
    loading,
    error,
    pageNum,
    pageSize,
    total,
    hasMore,
    isRefreshing,
    loadData,
    refresh,
    loadMore,
    reset,
    clearCache
  }
}

/**
 * 防抖函数
 */
export function useDebounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
      timer = null
    }, delay)
  }
}
