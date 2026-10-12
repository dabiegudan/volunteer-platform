import { get, put } from '@/utils/request'

export const configApi = {
  getConfigPage(params) {
    return get('/api/config/page', params)
  },

  updateConfig(key, data) {
    return put(`/api/config/${key}`, data)
  }
}

export const statsApi = {
  getGlobalStats() {
    return get('/api/stats/global')
  }
}
