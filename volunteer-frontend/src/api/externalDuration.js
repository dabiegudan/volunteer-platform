import { get, post, put } from '@/utils/request'

export const externalDurationApi = {
  getSupportedPlatforms() {
    return get('/api/external-duration/platforms')
  },

  importDuration(data) {
    return post('/api/external-duration/import', data)
  },

  batchImportDuration(data) {
    return post('/api/external-duration/import/batch', data)
  },

  getRecords(params) {
    return get('/api/external-duration/records', params)
  },

  getRecordsAdmin(params) {
    return get('/api/external-duration/admin/records', params)
  },

  auditDuration(durationId, data) {
    return put(`/api/external-duration/${durationId}/audit`, data)
  }
}
