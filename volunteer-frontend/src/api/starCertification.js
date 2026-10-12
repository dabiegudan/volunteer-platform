import { get, post } from '@/utils/request'

export const starCertificationApi = {
  getLevel() {
    return get('/api/star-certification/level')
  },

  getInfo() {
    return get('/api/star-certification/info')
  },

  apply() {
    return post('/api/star-certification/apply')
  },

  refresh() {
    return post('/api/star-certification/refresh')
  },

  getRequirements(targetLevel) {
    return get(`/api/star-certification/requirements/${targetLevel}`)
  }
}
