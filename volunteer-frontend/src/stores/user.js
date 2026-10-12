import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { toNumber } from '@/utils/validate'

const ADMIN_ROLES = ['ADMIN', 'COLLEGE_ADMIN', 'ORGANIZER', 'AUDITOR']

function normalizeRole(role) {
  return String(role || 'VOLUNTEER').toUpperCase()
}

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  const userId = ref('')
  const username = ref('')
  const realName = ref('')
  const avatar = ref('')
  const phone = ref('')
  const college = ref('')
  const major = ref('')
  const className = ref('')
  const grade = ref('')
  const studentNo = ref('')
  const role = ref('VOLUNTEER')
  const accessLevel = ref('')
  const isLoggedIn = ref(false)
  const totalDuration = ref(0)
  const creditScore = ref(100)

  const isAdminRole = computed(() => ADMIN_ROLES.includes(normalizeRole(role.value)))

  const userInfo = computed(() => ({
    userId: userId.value,
    username: username.value,
    realName: realName.value,
    avatar: avatar.value,
    phone: phone.value,
    college: college.value,
    major: major.value,
    className: className.value,
    grade: grade.value,
    studentNo: studentNo.value,
    role: normalizeRole(role.value),
    accessLevel: accessLevel.value,
    totalDuration: totalDuration.value,
    creditScore: creditScore.value
  }))

  function login(userData) {
    if (!userData?.token) {
      throw new Error('Missing login token')
    }

    token.value = userData.token
    userId.value = userData.userId || userData.id || ''
    username.value = userData.username || ''
    realName.value = userData.realName || userData.name || userData.username || ''
    avatar.value = userData.avatar || ''
    phone.value = userData.phone || ''
    college.value = userData.college || userData.collegeName || ''
    major.value = userData.major || ''
    className.value = userData.className || ''
    grade.value = userData.grade || ''
    studentNo.value = userData.studentNo || userData.studentId || ''
    role.value = normalizeRole(userData.role || userData.roleCode)
    accessLevel.value = userData.accessLevel || ''
    totalDuration.value = toNumber(userData.totalDuration, 0)
    creditScore.value = toNumber(userData.creditScore, 100)
    isLoggedIn.value = true

    uni.setStorageSync('user_token', token.value)
    uni.setStorageSync('user_info', userInfo.value)
  }

  function logout() {
    token.value = ''
    userId.value = ''
    username.value = ''
    realName.value = ''
    avatar.value = ''
    phone.value = ''
    college.value = ''
    major.value = ''
    className.value = ''
    grade.value = ''
    studentNo.value = ''
    role.value = 'VOLUNTEER'
    accessLevel.value = ''
    totalDuration.value = 0
    creditScore.value = 100
    isLoggedIn.value = false

    uni.removeStorageSync('user_token')
    uni.removeStorageSync('user_info')
    uni.reLaunch({ url: '/pages/login/index' })
  }

  function updateUser(updates) {
    if (!updates) return

    const keyMap = {
      token,
      userId,
      username,
      realName,
      avatar,
      phone,
      college,
      major,
      className,
      grade,
      studentNo,
      role,
      accessLevel,
      totalDuration,
      creditScore
    }

    Object.keys(updates).forEach(key => {
      if (keyMap[key] !== undefined) {
        keyMap[key].value = key === 'role' ? normalizeRole(updates[key]) : updates[key]
      }
    })

    uni.setStorageSync('user_info', userInfo.value)
  }

  function loadFromStorage() {
    const savedToken = uni.getStorageSync('user_token')
    const savedInfo = uni.getStorageSync('user_info')

    if (savedToken && savedInfo) {
      token.value = savedToken || ''
      userId.value = savedInfo.userId || ''
      username.value = savedInfo.username || ''
      realName.value = savedInfo.realName || ''
      avatar.value = savedInfo.avatar || ''
      phone.value = savedInfo.phone || ''
      college.value = savedInfo.college || ''
      major.value = savedInfo.major || ''
      className.value = savedInfo.className || ''
      grade.value = savedInfo.grade || ''
      studentNo.value = savedInfo.studentNo || ''
      role.value = normalizeRole(savedInfo.role)
      accessLevel.value = savedInfo.accessLevel || ''
      totalDuration.value = toNumber(savedInfo.totalDuration, 0)
      creditScore.value = toNumber(savedInfo.creditScore, 100)
      isLoggedIn.value = true
    }
  }

  return {
    token,
    userId,
    username,
    realName,
    avatar,
    phone,
    college,
    major,
    className,
    grade,
    studentNo,
    role,
    accessLevel,
    isLoggedIn,
    totalDuration,
    creditScore,
    isAdminRole,
    userInfo,
    login,
    logout,
    updateUser,
    loadFromStorage
  }
})
