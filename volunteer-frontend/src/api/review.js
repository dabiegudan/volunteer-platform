import { get, post, put, del } from '@/utils/request'

export const reviewApi = {
  createActivityReview(activityId, data) {
    return post(`/api/review/activity/${activityId}`, data)
  },

  updateActivityReview(reviewId, data) {
    return put(`/api/review/activity/${reviewId}`, data)
  },

  deleteActivityReview(reviewId) {
    return del(`/api/review/activity/${reviewId}`)
  },

  getActivityReviews(params) {
    return get('/api/review/activity', params)
  },

  getActivityRatingStats(activityId) {
    return get(`/api/review/activity/${activityId}/stats`)
  },

  createVolunteerReview(reviewedUserId, data) {
    return post(`/api/review/volunteer/${reviewedUserId}`, data)
  },

  getVolunteerReviews(params) {
    return get('/api/review/volunteer', params)
  },

  getVolunteerRatingStats(userId) {
    return get(`/api/review/volunteer/${userId}/stats`)
  },

  createFeedback(data) {
    return post('/api/review/feedback', data)
  },

  getMyFeedback(params) {
    return get('/api/review/feedback', params)
  },

  getFeedbackAdmin(params) {
    return get('/api/review/feedback/admin', params)
  },

  getFeedback(feedbackId) {
    return get(`/api/review/feedback/${feedbackId}`)
  },

  replyFeedback(feedbackId, reply) {
    return put(`/api/review/feedback/${feedbackId}/reply`, { reply })
  },

  closeFeedback(feedbackId) {
    return put(`/api/review/feedback/${feedbackId}/close`)
  }
}
