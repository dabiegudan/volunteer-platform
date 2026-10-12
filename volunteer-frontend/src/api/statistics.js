import { get } from '@/utils/request'

export const statisticsApi = {
  // 获取活动趋势数据（折线图）
  getActivityTrend(params) {
    return get('/api/statistics/activity-trend', params)
  },

  // 获取服务时长统计（柱状图）
  getServiceDuration(params) {
    return get('/api/statistics/service-duration', params)
  },

  // 获取活动分类统计（饼图）
  getActivityCategory(params) {
    return get('/api/statistics/activity-category', params)
  },

  // 获取签到率分析（仪表盘）
  getCheckinRate(activityId) {
    return get('/api/statistics/checkin-rate', activityId ? { activityId } : undefined)
  },

  // 获取志愿者画像（雷达图）
  getVolunteerProfile() {
    return get('/api/statistics/volunteer-profile')
  }
}
