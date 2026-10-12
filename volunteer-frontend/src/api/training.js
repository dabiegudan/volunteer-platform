import { get, post, put, del } from '@/utils/request'

export const trainingApi = {
  getCourses(params) {
    return get('/api/training/courses', params)
  },

  getCourse(id) {
    return get(`/api/training/courses/${id}`)
  },

  createCourse(data) {
    return post('/api/training/courses', data)
  },

  updateCourse(id, data) {
    return put(`/api/training/courses/${id}`, data)
  },

  deleteCourse(id) {
    return del(`/api/training/courses/${id}`)
  },

  startCourse(courseId) {
    return post(`/api/training/courses/${courseId}/start`)
  },

  updateProgress(courseId, progress) {
    return put(`/api/training/courses/${courseId}/progress`, { progress })
  },

  completeCourse(courseId) {
    return post(`/api/training/courses/${courseId}/complete`)
  },

  getRecords(params) {
    return get('/api/training/records', params)
  },

  getCertifications(params) {
    return get('/api/training/certifications', params)
  },

  getCategories() {
    return get('/api/training/categories')
  }
}
