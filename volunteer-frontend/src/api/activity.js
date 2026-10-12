import { get, post, put, del } from '@/utils/request'

function cleanParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

function normalizeTags(tags) {
  if (Array.isArray(tags)) return tags.filter(Boolean)
  if (typeof tags !== 'string') return []
  return tags
    .split(/[,，、;；|]/)
    .map(tag => tag.trim())
    .filter(Boolean)
}

function normalizeActivity(activity) {
  if (!activity) return activity

  const participantCount = activity.participantCount ?? activity.currentParticipants ?? 0
  const duration = activity.duration ?? activity.durationHours ?? 0

  return {
    ...activity,
    participantCount,
    currentParticipants: activity.currentParticipants ?? participantCount,
    duration,
    durationHours: activity.durationHours ?? duration,
    maxParticipants: activity.maxParticipants ?? 0,
    tags: normalizeTags(activity.tags),
    organizerName: activity.organizerName || activity.leaderName || '活动负责人',
    organizerPhone: activity.organizerPhone || activity.leaderPhone || '',
    organizerRole: activity.organizerRole || 'ORGANIZER'
  }
}

function normalizePageResult(result) {
  if (!result) return { list: [], records: [], total: 0 }
  const sourceList = result.list || result.records || []
  const list = sourceList.map(normalizeActivity)
  return {
    ...result,
    list,
    records: list,
    total: result.total ?? list.length
  }
}

export const activityApi = {
  async getActivityList(params, options = {}) {
    const result = await get('/api/activity/page', cleanParams(params), options)
    return normalizePageResult(result)
  },

  async getActivityFeed(params, options = {}) {
    const result = await get('/api/activity/list', cleanParams(params), options)
    return Array.isArray(result) ? result.map(normalizeActivity) : normalizePageResult(result).list
  },

  async getActivityDetail(id, options = {}) {
    const result = await get(`/api/activity/${id}`, undefined, { deduplicate: false, ...options })
    return normalizeActivity(result)
  },

  createActivity(data) {
    return post('/api/activity', data)
  },

  updateActivity(id, data) {
    return put(`/api/activity/${id}`, data)
  },

  publishActivity(id) {
    return post(`/api/activity/publish/${id}`)
  },

  approveActivity(id, data) {
    return post(`/api/activity/${id}/approve`, data)
  },

  rejectActivity(id, data) {
    return post(`/api/activity/${id}/reject`, data)
  },

  auditActivity(id, data) {
    return post(`/api/activity/${id}/audit`, data)
  },

  getAuditActivities(params, options = {}) {
    return get('/api/activity/audit/page', cleanParams(params), options)
  },

  deleteActivity(id) {
    return del(`/api/activity/${id}`)
  },

  registerActivity(id, options = {}) {
    return post(`/api/activity/${id}/register`, undefined, options)
  },

  cancelRegistration(id) {
    return del(`/api/activity/${id}/register`)
  },

  getUserRegistration(id, options = {}) {
    return get(`/api/activity/${id}/registration`, undefined, {
      showLoading: false,
      deduplicate: false,
      ...options
    })
  },

  getUserRegistrations(params, options = {}) {
    return get('/api/activity/registrations', cleanParams(params), options)
  },

  getActivityRegistrations(activityId, params, options = {}) {
    return get(`/api/activity/${activityId}/registrations`, cleanParams(params), options)
  },

  getImportLogs(activityId, params, options = {}) {
    return get(`/api/activity/${activityId}/import/logs`, cleanParams(params), options)
  },

  confirmImport(activityId, data) {
    return post(`/api/activity/${activityId}/import/confirm`, data)
  }
}

export const registrationApi = {
  getMyRegistrations(params, options = {}) {
    return get('/api/registration/my', cleanParams(params), options)
  },

  approve(id) {
    return post(`/api/registration/${id}/approve`)
  },

  reject(id, data) {
    return post(`/api/registration/${id}/reject`, data)
  }
}

export const teamApi = {
  createTeam(data) {
    return post('/api/team', data)
  },

  joinTeam(teamId) {
    return post(`/api/team/${teamId}/join`)
  },

  leaveTeam(teamId) {
    return post(`/api/team/${teamId}/leave`)
  },

  disbandTeam(teamId) {
    return post(`/api/team/${teamId}/disband`)
  },

  getActivityTeams(activityId) {
    return get(`/api/team/activity/${activityId}`)
  },

  getTeamMembers(teamId) {
    return get(`/api/team/${teamId}/members`)
  }
}
