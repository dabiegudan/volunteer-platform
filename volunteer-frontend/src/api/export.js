import { get, post } from '@/utils/request'

/**
 * 数据导出 API
 */
export const exportApi = {
  // 导出活动统计数据
  exportActivityStats(params) {
    return get('/api/export/activity-stats', params)
  },

  // 导出用户时长统计
  exportUserDuration(params) {
    return get('/api/export/user-duration', params)
  },

  // 导出 CSV 格式
  exportCsv(params) {
    return get('/api/export/csv', params)
  },

  createExportTask(data) {
    return post('/api/export/task', data)
  },

  getExportTaskProgress(taskId) {
    return get(`/api/export/task/${taskId}/progress`)
  },

  getExportTasks(params) {
    return get('/api/export/tasks', params)
  },

  cancelExportTask(taskId) {
    return post(`/api/export/task/${taskId}/cancel`)
  }
}
