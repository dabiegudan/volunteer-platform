import { get } from '@/utils/request'

// 报表相关API
export const reportApi = {
  // 获取个人时长统计
  getUserDurationReport() {
    return get('/api/report/duration')
  },

  getUserDurationDetail(params) {
    return get('/api/report/duration/detail', params)
  },
  
  // AI生成报表摘要
  getReportSummary() {
    return get('/api/report/summary')
  },
  
  // 导出考勤记录Excel
  exportAttendanceExcel() {
    return get('/api/report/export/attendance')
  },
  
  // 导出时长明细Excel
  exportDurationExcel() {
    return get('/api/report/export/duration')
  },
  
  // 生成时长证明PDF
  generateCertificate() {
    return get('/api/report/certificate')
  },
  
  // 验证证书真伪
  verifyCertificate(params) {
    return get('/api/report/certificate/verify', params)
  },
  
  // 获取所有组织统计
  getOrganizationStatistics() {
    return get('/api/report/organization')
  },
  
  // 获取单个组织统计
  getOrganizationStatistic(id) {
    return get(`/api/report/organization/${id}`)
  },
  
  // 获取所有学院统计
  getAllCollegeStatistics() {
    return get('/api/report/college')
  },
  
  // 获取单个学院统计
  getCollegeStatistics(collegeName) {
    return get('/api/report/college/detail', { collegeName })
  },

  getCollegeStatisticsByParams(params) {
    return get('/api/report/college/detail', params)
  }
}
