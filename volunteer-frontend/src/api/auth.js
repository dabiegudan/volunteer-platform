import { get, post, put, del } from '@/utils/request'

function cleanParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
  )
}

// 认证相关API
export const authApi = {
  // 用户登录
  login(data) {
    return post('/api/auth/login', data, { requiresAuth: false })
  },
  
  // 用户注册
  register(data) {
    return post('/api/auth/register', data, { requiresAuth: false })
  },

  refresh(token) {
    return post('/api/auth/refresh', token ? { token } : undefined)
  },
  
  // 获取当前用户信息
  getUserInfo(options = {}) {
    return get('/api/auth/user/info', undefined, options)
  },

  bindStudentId(data, options = {}) {
    return post('/api/auth/user/bind-student-id', data, options)
  },

  checkStudentId(params, options = {}) {
    return get('/api/auth/user/check-student-id', params, { requiresAuth: false, ...options })
  }
}

// 用户管理相关API
export const userApi = {
  // 分页查询用户列表
  getUserList(params, options = {}) {
    return get('/api/user/page', cleanParams(params), options)
  },
  
  // 获取用户详情
  getUserDetail(id, options = {}) {
    return get(`/api/user/${id}`, undefined, options)
  },
  
  // 创建用户
  createUser(data) {
    return post('/api/user', data)
  },
  
  // 更新用户
  updateUser(data) {
    return put('/api/user', data)
  },

  updateUserStatus(id, status) {
    return put(`/api/user/${id}/status`, { status })
  },

  resetPassword(id, password) {
    return put(`/api/user/${id}/password`, { password })
  },
  
  // 删除用户
  deleteUser(id) {
    return del(`/api/user/${id}`)
  },
  
  // 分配角色
  assignRole(userId, roleId) {
    return post(`/api/user/${userId}/role/${roleId}`)
  },
  
  // 获取用户角色
  getUserRoles(userId) {
    return get(`/api/user/${userId}/roles`)
  }
}

export const roleApi = {
  getRolePage(params) {
    return get('/api/role/page', params)
  },

  createRole(data) {
    return post('/api/role', data)
  },

  assignPermissions(id, permissionIds) {
    return put(`/api/role/${id}/permissions`, { permissionIds })
  }
}

export const captchaApi = {
  getCaptcha() {
    return get('/api/captcha', undefined, { requiresAuth: false })
  },

  verifyCaptcha(data) {
    return post('/api/captcha/verify', data, { requiresAuth: false })
  },

  getSlider() {
    return get('/api/captcha/slider', undefined, { requiresAuth: false })
  },

  verifySlider(data) {
    return post('/api/captcha/slider/verify', data, { requiresAuth: false })
  }
}
