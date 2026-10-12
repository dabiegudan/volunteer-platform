/**
 * useCache 缓存工具模块测试
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { getCache, setCache, removeCache, clearCacheByPrefix, DEFAULT_TTL } from '@/composables/useCache'

// 模拟 uni.storage API（在 tests/setup.js 中已初始化）
beforeEach(() => {
  // 清理存储
  global.__storage__ = {}
})

describe('useCache', () => {
  it('setCache 存储数据包含时间戳', () => {
    setCache('test-key', { name: '张三' })

    const raw = uni.getStorageSync('test-key')
    expect(raw).toBeTruthy()
    expect(raw.timestamp).toBeDefined()
    expect(raw.value).toEqual({ name: '张三' })
  })

  it('getCache 读取未过期的缓存', () => {
    setCache('test-key', { name: '李四' })
    const result = getCache('test-key')

    expect(result).toEqual({ name: '李四' })
  })

  it('getCache 过期缓存返回null', () => {
    // 手动写入一个过期的缓存
    uni.setStorageSync('test-key', {
      value: { name: '过期' },
      timestamp: Date.now() - DEFAULT_TTL - 1000 // 已过期
    })

    const result = getCache('test-key')
    expect(result).toBeNull()
  })

  it('getCache 缓存过期后自动清除', () => {
    uni.setStorageSync('test-key', {
      value: { name: '过期' },
      timestamp: Date.now() - DEFAULT_TTL - 1000
    })

    getCache('test-key')
    const raw = uni.getStorageSync('test-key')
    expect(raw).toBeNull()
  })

  it('getCache 不存在的key返回null', () => {
    const result = getCache('non-existent')
    expect(result).toBeNull()
  })

  it('removeCache 删除缓存', () => {
    setCache('test-key', 'value')
    removeCache('test-key')

    expect(uni.getStorageSync('test-key')).toBeNull()
  })

  it('clearCacheByPrefix 按前缀清除缓存', () => {
    setCache('page:1', 'data1')
    setCache('page:2', 'data2')
    setCache('other', 'other-data')

    clearCacheByPrefix('page:')

    expect(uni.getStorageSync('page:1')).toBeNull()
    expect(uni.getStorageSync('page:2')).toBeNull()
    expect(uni.getStorageSync('other')).toBeTruthy()
  })

  it('支持自定义TTL', () => {
    setCache('short-cache', 'data')
    const result = getCache('short-cache', 1000) // 1秒TTL
    expect(result).toBe('data')
  })
})
