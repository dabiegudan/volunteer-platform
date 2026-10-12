import { API_BASE_URL, del, get } from '@/utils/request'

function authHeader(extra = {}) {
  const token = uni.getStorageSync('user_token')
  return {
    Authorization: token ? `Bearer ${token}` : '',
    ...extra
  }
}

export const fileApi = {
  uploadFile(filePath, formData = {}) {
    return new Promise((resolve, reject) => {
      uni.uploadFile({
        url: `${API_BASE_URL}/api/file/upload`,
        filePath,
        name: 'file',
        formData,
        header: authHeader(),
        success: (res) => {
          try {
            const payload = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
            if (payload.code === 200 || payload.code === 0) {
              resolve(payload.data)
              return
            }
            reject(payload)
          } catch (error) {
            reject(error)
          }
        },
        fail: reject
      })
    })
  },

  getFileUrl(params) {
    return get('/api/file/url', params, { requiresAuth: false })
  },

  downloadFile(params) {
    return get('/api/file/download', params, { requiresAuth: false })
  },

  deleteFile(params) {
    return del('/api/file/delete', params, { requiresAuth: false })
  }
}
