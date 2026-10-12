/**
 * usePagination composable 单元测试
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { usePagination } from '@/composables/usePagination'

describe('usePagination', () => {
  let mockFetchFn

  beforeEach(() => {
    mockFetchFn = vi.fn()
  })

  it('初始状态正确', () => {
    const {
      data, loading, error, pageNum, pageSize,
      total, hasMore, isRefreshing
    } = usePagination({ fetchFn: mockFetchFn })

    expect(data.value).toEqual([])
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
    expect(pageNum.value).toBe(1)
    expect(pageSize.value).toBe(10)
    expect(total.value).toBe(0)
    expect(hasMore.value).toBe(true)
    expect(isRefreshing.value).toBe(false)
  })

  it('自定义每页大小', () => {
    const { pageSize } = usePagination({
      fetchFn: mockFetchFn,
      defaultPageSize: 20
    })

    expect(pageSize.value).toBe(20)
  })

  it('loadData(refresh=true) 加载数据并刷新', async () => {
    const mockData = {
      list: [{ id: 1, title: '活动1' }, { id: 2, title: '活动2' }],
      total: 20
    }

    mockFetchFn.mockResolvedValue(mockData)

    const { data, total, loadData, loading, hasMore } = usePagination({
      fetchFn: mockFetchFn
    })

    await loadData(true)

    expect(data.value).toEqual(mockData.list)
    expect(total.value).toBe(20)
    expect(loading.value).toBe(false)
    expect(hasMore.value).toBe(true)
  })

  it('loadData(refresh=false) 追加数据', async () => {
    mockFetchFn
      .mockResolvedValueOnce({
        list: [{ id: 1 }, { id: 2 }],
        total: 5
      })
      .mockResolvedValueOnce({
        list: [{ id: 3 }, { id: 4 }],
        total: 5
      })

    const { data, loadData } = usePagination({
      fetchFn: mockFetchFn,
      defaultPageSize: 2
    })

    await loadData(true)
    await loadData(false)

    expect(data.value).toHaveLength(4)
  })

  it('loadData 加载失败时设置error', async () => {
    const testError = new Error('网络错误')
    mockFetchFn.mockRejectedValue(testError)

    const { error, loadData, loading } = usePagination({
      fetchFn: mockFetchFn
    })

    await loadData(true)

    expect(error.value).toBe(testError)
    expect(loading.value).toBe(false)
  })

  it('loadData 加载中不重复请求', async () => {
    let resolvePromise
    mockFetchFn.mockImplementation(() => new Promise(resolve => {
      resolvePromise = resolve
    }))

    const { loadData, loading } = usePagination({
      fetchFn: mockFetchFn
    })

    loadData(true)
    loadData(true) // 第二次应被忽略

    expect(mockFetchFn).toHaveBeenCalledTimes(1)

    resolvePromise({ list: [], total: 0 })
  })

  it('hasMore 判断：数据量小于pageSize时无更多', async () => {
    mockFetchFn.mockResolvedValue({
      list: [{ id: 1 }, { id: 2 }],
      total: 2
    })

    const { hasMore, loadData } = usePagination({
      fetchFn: mockFetchFn,
      defaultPageSize: 10
    })

    await loadData(true)

    expect(hasMore.value).toBe(false)
  })

  it('reset 重置所有状态', async () => {
    mockFetchFn.mockResolvedValue({
      list: [{ id: 1 }, { id: 2 }],
      total: 2
    })

    const { data, pageNum, hasMore, loadData, reset } = usePagination({
      fetchFn: mockFetchFn
    })

    await loadData(true)
    reset()

    expect(data.value).toEqual([])
    expect(pageNum.value).toBe(1)
    expect(hasMore.value).toBe(true)
  })

  it('refresh 调用 loadData(true) 并管理 isRefreshing', async () => {
    mockFetchFn.mockResolvedValue({
      list: [{ id: 1 }],
      total: 1
    })

    const { refresh, isRefreshing } = usePagination({
      fetchFn: mockFetchFn
    })

    await refresh()

    expect(isRefreshing.value).toBe(false)
  })

  it('loadMore 调用 loadData(false)', async () => {
    mockFetchFn
      .mockResolvedValueOnce({ list: [{ id: 1 }], total: 3 })
      .mockResolvedValueOnce({ list: [{ id: 2 }], total: 3 })

    const { data, loadData, loadMore } = usePagination({
      fetchFn: mockFetchFn,
      defaultPageSize: 2
    })

    await loadData(true)
    await loadMore()

    expect(data.value).toHaveLength(2)
  })
})
