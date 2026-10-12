import { get, post } from '@/utils/request'

// 考勤相关API
export const attendanceApi = {
  // 验证位置
  validateLocation(data) {
    return post('/api/attendance/location/validate', data)
  },
  
  // AI分析补签理由
  analyzeSupplementary(data) {
    return post('/api/attendance/supplementary/analyze', data)
  },
  
  // 定位签到
  checkIn(data, options = {}) {
    return post('/api/attendance/checkin', data, options)
  },
  
  // 定位签退
  checkOut(data, options = {}) {
    return post('/api/attendance/checkout', data, options)
  },
  
  // 生成签到二维码
  generateQRCode(activityId) {
    return post(`/api/attendance/qrcode/${activityId}`)
  },
  
  // 二维码签到
  checkInByQRCode(data) {
    return post('/api/attendance/qrcode/checkin', data)
  },

  // 人脸签到
  checkInByFace(data) {
    return post('/api/attendance/face/checkin', data)
  },

  // NFC签到
  checkInByNfc(data) {
    return post('/api/attendance/nfc/checkin', data)
  },

  // 蓝牙签到
  checkInByBluetooth(data) {
    return post('/api/attendance/bluetooth/checkin', data)
  },
  
  // 申请补签
  applySupplementary(data) {
    return post('/api/attendance/supplementary', data)
  },

  getActivityAttendances(activityId, params) {
    return get(`/api/attendance/activity/${activityId}`, params)
  },

  approveSupplementary(id) {
    return post(`/api/attendance/supplementary/${id}/approve`)
  },

  rejectSupplementary(id, data) {
    return post(`/api/attendance/supplementary/${id}/reject`, data)
  },
  
  // 创建失信记录
  createViolation(data) {
    return post('/api/attendance/violation', data)
  },
  
  // 解除限制
  releaseRestriction(violationId, data) {
    return post(`/api/attendance/violation/${violationId}/release`, data)
  },
  
  // 获取用户失信记录
  getUserViolations(userId) {
    return get(`/api/attendance/violation/user/${userId}`)
  },

  getMyViolations() {
    return get('/api/attendance/violation/my')
  },

  getViolationPage(params) {
    return get('/api/attendance/violation/page', params)
  },
  
  // 检查用户是否受限
  checkRestriction() {
    return get('/api/attendance/restriction/check')
  }
}

export const durationApi = {
  approve(id, data) {
    return post(`/api/duration/${id}/approve`, data)
  }
}
