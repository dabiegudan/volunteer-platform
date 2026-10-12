import { get, post, put, del } from '@/utils/request'

function cleanParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

// 档案管理相关API (#2)
export const profileApi = {
  getProfileStatus(options = {}) {
    return get('/api/auth/user/profile/status', undefined, options)
  },
  updateProfile(data, options = {}) {
    return put('/api/auth/user/profile', data, options)
  }
}

// 时长异议相关API (#4)
export const disputeApi = {
  submitDispute(data) {
    return post('/api/dispute', data)
  },
  getDisputePage(params) {
    return get('/api/dispute/page', params)
  },
  getDisputeDetail(id) {
    return get(`/api/dispute/${id}`)
  },
  reviewDispute(id, data) {
    return put(`/api/dispute/${id}/review`, data)
  }
}

// 表彰奖励相关API (#5)
export const commendationApi = {
  getCommendationPage(params) {
    return get('/api/commendation/page', params)
  },
  getCommendationPageAdmin(params) {
    return get('/api/commendation/admin/page', params)
  },
  createCommendation(data) {
    return post('/api/commendation', data)
  },
  approveCommendation(id) {
    return put(`/api/commendation/${id}/approve`)
  },
  rejectCommendation(id, remark) {
    return put(`/api/commendation/${id}/reject`, remark)
  },
  deleteCommendation(id) {
    return del(`/api/commendation/${id}`)
  },
  getCommendationDetail(id) {
    return get(`/api/commendation/${id}`)
  }
}

// 志愿地图相关API (#7)
export const mapApi = {
  getNearbyStations(params) {
    return get('/api/map/stations', params)
  },
  getFifteenMinCircle(params) {
    return get('/api/map/15min-circle', params)
  },
  getAllMapPoints(params) {
    return get('/api/map/points', params)
  }
}

// 系统公告相关API (#12)
export const announcementApi = {
  getAnnouncementPage(params) {
    return get('/api/announcement/page', params)
  },
  getActiveAnnouncements() {
    return get('/api/announcement/active')
  },
  getAnnouncementDetail(id) {
    return get(`/api/announcement/${id}`)
  },
  createAnnouncement(data) {
    return post('/api/announcement', data)
  },
  updateAnnouncement(id, data) {
    return put(`/api/announcement/${id}`, data)
  },
  deleteAnnouncement(id) {
    return del(`/api/announcement/${id}`)
  }
}

// 运营大屏相关API (#14)
export const dashboardApi = {
  getDashboardData() {
    return get('/api/dashboard')
  }
}

// 操作审计日志相关API (#15)
export const auditApi = {
  getAuditLogPage(params, options = {}) {
    return get('/api/audit/page', cleanParams(params), options)
  }
}
