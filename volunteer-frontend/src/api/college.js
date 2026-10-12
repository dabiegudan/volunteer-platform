import { get, post } from '@/utils/request'

export const collegeApi = {
  getCollegeList(options = {}) {
    return get('/api/college/list', undefined, { requiresAuth: false, ...options })
  },

  assignCollege(userId, collegeId) {
    return post(`/api/college/assign/${userId}/${collegeId}`)
  },

  checkBind() {
    return get('/api/college/check-bind')
  }
}
