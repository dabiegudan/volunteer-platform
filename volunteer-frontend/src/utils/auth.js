/**
 * 权限验证工具
 */

// 检查是否已登录
export function checkAuth() {
  const token = uni.getStorageSync('user_token')
  if (!token) {
    uni.showModal({
      title: '提示',
      content: '请先登录后再进行操作',
      success: (res) => {
        if (res.confirm) {
          uni.navigateTo({ url: '/pages/login/index' })
        }
      }
    })
    return false
  }
  return true
}

// 检查是否有管理员权限
export function checkAdmin() {
  const userInfo = uni.getStorageSync('user_info')
  const role = String(userInfo?.role || '').toUpperCase()
  if (!userInfo || !['ADMIN', 'COLLEGE_ADMIN', 'ORGANIZER', 'AUDITOR'].includes(role)) {
    uni.showToast({ title: '无权限操作', icon: 'none' })
    return false
  }
  return true
}

// 清除登录状态
export function clearAuth() {
  uni.removeStorageSync('user_token')
  uni.removeStorageSync('user_info')
  uni.reLaunch({ url: '/pages/login/index' })
}

// 获取当前用户角色
export function getUserRole() {
  const userInfo = uni.getStorageSync('user_info')
  return String(userInfo?.role || 'GUEST').toUpperCase()
}

// 检查是否有特定权限
export function hasPermission(permission) {
  const role = getUserRole()
  
  const permissionMap = {
    ADMIN: ['all'],
    COLLEGE_ADMIN: ['all'],
    ORGANIZER: ['all'],
    AUDITOR: ['all'],
    VOLUNTEER: ['view_activities', 'join_activity', 'check_in', 'view_profile', 'edit_profile'],
    GUEST: ['view_public_activities']
  }
  
  const rolePermissions = permissionMap[role] || []
  return rolePermissions.includes('all') || rolePermissions.includes(permission)
}
