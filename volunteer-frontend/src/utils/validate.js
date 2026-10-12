/**
 * 表单验证工具
 */

// 输入过滤，防止 XSS 和 SQL 注入
export function sanitizeInput(input, maxLength = 200) {
  if (!input) return ''
  return String(input)
    .replace(/[<>'"&]/g, '')
    .trim()
    .substring(0, maxLength)
}

// 验证用户名
export function validateUsername(username) {
  if (!username) return { valid: false, message: '请输入用户名' }
  if (username.length < 4) return { valid: false, message: '用户名至少4位' }
  if (username.length > 20) return { valid: false, message: '用户名最多20位' }
  if (!/^[a-zA-Z0-9_]+$/.test(username)) {
    return { valid: false, message: '用户名只能包含字母、数字和下划线' }
  }
  return { valid: true }
}

// 验证密码
export function validatePassword(password) {
  if (!password) return { valid: false, message: '请输入密码' }
  if (password.length < 6) return { valid: false, message: '密码至少6位' }
  if (password.length > 16) return { valid: false, message: '密码最多16位' }
  return { valid: true }
}

// 验证手机号
export function validatePhone(phone) {
  if (!phone) return { valid: false, message: '请输入手机号' }
  if (!/^1[3-9]\d{9}$/.test(phone)) {
    return { valid: false, message: '手机号格式不正确' }
  }
  return { valid: true }
}

// 验证邮箱
export function validateEmail(email) {
  if (!email) return { valid: false, message: '请输入邮箱' }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return { valid: false, message: '邮箱格式不正确' }
  }
  return { valid: true }
}

// 类型转换工具 - 增强版，支持更多格式
export function toBoolean(value) {
  if (value === null || value === undefined) return false
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') {
    const lower = value.toLowerCase()
    return lower === 'true' || lower === '1' || lower === 'yes' || lower === 'y'
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  return Boolean(value)
}

export function toNumber(value, defaultValue = 0) {
  if (value === null || value === undefined) return defaultValue
  const num = Number(value)
  return isNaN(num) ? defaultValue : num
}

// 验证必填项
export function required(value, fieldName = '该项') {
  if (value === null || value === undefined || value === '') {
    return { valid: false, message: `${fieldName}不能为空` }
  }
  return { valid: true }
}

// 验证长度范围
export function validateLength(value, min, max, fieldName = '内容') {
  const len = String(value || '').length
  if (min && len < min) {
    return { valid: false, message: `${fieldName}至少${min}个字符` }
  }
  if (max && len > max) {
    return { valid: false, message: `${fieldName}最多${max}个字符` }
  }
  return { valid: true }
}

// 验证数值范围
export function validateRange(value, min, max, fieldName = '数值') {
  const num = Number(value)
  if (isNaN(num)) return { valid: false, message: `${fieldName}必须是数字` }
  if (min !== undefined && num < min) {
    return { valid: false, message: `${fieldName}不能小于${min}` }
  }
  if (max !== undefined && num > max) {
    return { valid: false, message: `${fieldName}不能大于${max}` }
  }
  return { valid: true }
}

// 防抖处理工具
export function debounce(fn, delay = 1000) {
  let timer = null
  return function (...args) {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      fn.apply(this, args)
      timer = null
    }, delay)
  }
}
