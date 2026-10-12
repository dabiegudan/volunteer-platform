<template>
  <view class="page mine-page safe-bottom">
    <view class="page-shell">
      <view class="profile-card shell-card">
        <view class="avatar">{{ initials }}</view>
        <view class="profile-info">
          <text class="page-kicker">个人中心</text>
          <text class="profile-name">{{ user.realName || user.username || '志愿者' }}</text>
          <text class="profile-meta">{{ roleLabel }} · {{ user.college || '未绑定学院' }} · {{ studentStatusText }}</text>
        </view>
        <button class="btn profile-action" @click="logout">退出</button>
      </view>

      <view class="info-layout">
        <view class="info-card shell-card">
          <view class="info-card__head">
            <view>
              <text class="info-title">基础信息</text>
              <text class="info-subtitle">用于活动推荐和组织识别</text>
            </view>
            <button class="btn info-action" @click="go('/pages/profile/edit')">编辑</button>
          </view>
          <view class="info-list">
            <view v-for="item in basicInfoItems" :key="item.label" class="info-row">
              <text class="info-label">{{ item.label }}</text>
              <text class="info-value" :class="{ muted: !item.value }">{{ item.value || '未填写' }}</text>
            </view>
          </view>
        </view>

        <view class="info-card shell-card">
          <view class="info-card__head">
            <view>
              <text class="info-title">必要信息</text>
              <text class="info-subtitle">用于报名校验、通知和应急联系</text>
            </view>
            <text class="account-state" :class="{ complete: requiredComplete }">
              {{ requiredCompletedCount }}/{{ requiredInfoItems.length }}
            </text>
          </view>
          <view class="info-list">
            <view v-for="item in requiredInfoItems" :key="item.label" class="info-row">
              <text class="info-label">{{ item.label }}</text>
              <text class="info-value" :class="{ muted: !item.value }">{{ item.value || item.emptyText }}</text>
            </view>
          </view>
          <view class="info-actions">
            <button v-if="!user.studentNo" class="btn" @click="go('/pages/profile/bind-student')">绑定学号</button>
            <button class="btn btn--primary" @click="go('/pages/profile/edit')">
              {{ profileComplete ? '更新资料' : '完善资料' }}
            </button>
          </view>
        </view>
      </view>

      <view class="metric-grid">
        <view class="metric-card shell-card">
          <text class="metric-label">服务时长</text>
          <text class="metric-value">{{ duration.totalDuration || user.totalDuration || 0 }}</text>
        </view>
        <view class="metric-card shell-card">
          <text class="metric-label">信用积分</text>
          <text class="metric-value">{{ credit.creditScore || user.creditScore || 100 }}</text>
        </view>
      </view>

      <view class="menu-list shell-card">
        <button v-for="item in menuItems" :key="item.url" class="menu-item" @click="go(item.url)">
          <view class="menu-left">
            <view class="menu-icon">
              <image class="menu-icon__img" :src="item.icon" mode="aspectFit" />
            </view>
            <text>{{ item.label }}</text>
          </view>
          <text class="menu-arrow">›</text>
        </button>
      </view>

      <view class="shell-card panel">
        <view class="section-title">
          <view>
            <text class="section-title__main">报名限制</text>
            <text class="section-title__sub">来自考勤限制检查接口</text>
          </view>
          <button class="btn panel-action" @click="checkRestriction">检查</button>
        </view>
        <text class="restriction">{{ restrictionText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { attendanceApi, authApi, creditApi, profileApi, reportApi } from '@/api'
import { RoleTypeText } from '@/common/enums'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
userStore.loadFromStorage()

const user = computed(() => userStore.userInfo)
const duration = ref({})
const credit = ref({})
const restrictionText = ref('暂未检查')
const profileComplete = ref(false)
const profileLoaded = ref(false)
const profile = ref({})

const menuIconColor = 'a51f2b'
const navIcon = name => `https://api.iconify.design/lucide:${name}.svg?color=%23${menuIconColor}`

const menuItems = [
  { label: '我的活动', icon: navIcon('calendar-check'), url: '/pages/my-activities/index' },
  { label: '补签申请', icon: navIcon('file-pen-line'), url: '/pages/tools/supplementary' },
  { label: '个人报表', icon: navIcon('file-chart-column'), url: '/pages/report/index' },
  { label: '服务咨询', icon: navIcon('message-circle-question'), url: '/pages/ai/chat' }
]

const initials = computed(() => {
  const name = user.value.realName || user.value.username || '志'
  return name.slice(0, 1)
})

const roleLabel = computed(() => RoleTypeText[user.value.role] || user.value.role || '志愿者')
const studentStatusText = computed(() => user.value.studentNo ? `学号 ${user.value.studentNo}` : '未绑定学号')
const profileStatusText = computed(() => {
  if (!profileLoaded.value) return '待检查'
  return profileComplete.value ? '资料已完整' : '资料待完善'
})
const basicInfoItems = computed(() => [
  { label: '姓名', value: profile.value.realName || user.value.realName || user.value.username },
  { label: '性别', value: formatGender(profile.value.gender) },
  { label: '班级', value: profile.value.className || user.value.className },
  { label: '偏好', value: formatServiceTags(profile.value.serviceCategoryTags) }
])
const requiredInfoItems = computed(() => [
  { label: '学号', value: user.value.studentNo, emptyText: '未绑定' },
  { label: '联系方式', value: profile.value.phone || user.value.phone, emptyText: '未填写' },
  { label: '出生日期', value: normalizeDate(profile.value.birthDate), emptyText: '未填写' },
  { label: '紧急联系人', value: profile.value.emergencyContact, emptyText: '未填写' },
  { label: '紧急联系电话', value: profile.value.emergencyPhone, emptyText: '未填写' }
])
const requiredCompletedCount = computed(() => requiredInfoItems.value.filter(item => Boolean(item.value)).length)
const requiredComplete = computed(() => requiredCompletedCount.value === requiredInfoItems.value.length)

function go(url) {
  uni.navigateTo({ url })
}

function formatGender(value) {
  if (value === 'M') return '男'
  if (value === 'F') return '女'
  return value || ''
}

function normalizeDate(value) {
  return value ? String(value).slice(0, 10) : ''
}

function formatServiceTags(value) {
  if (!value) return ''
  if (Array.isArray(value)) return value.join('、')
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.join('、') : String(value)
  } catch (error) {
    return String(value)
  }
}

async function loadData() {
  const [durationResult, creditResult] = await Promise.allSettled([
    reportApi.getUserDurationReport(),
    creditApi.getOverview()
  ])
  duration.value = durationResult.status === 'fulfilled' ? durationResult.value || {} : {}
  credit.value = creditResult.status === 'fulfilled' ? creditResult.value || {} : {}
}

async function loadAccountStatus() {
  const [profileResult, userResult] = await Promise.allSettled([
    profileApi.getProfileStatus({ showLoading: false, showErrorToast: false }),
    authApi.getUserInfo({ showLoading: false, showErrorToast: false })
  ])

  if (profileResult.status === 'fulfilled') {
    profile.value = profileResult.value || {}
    profileComplete.value = Boolean(profile.value.profileComplete)
    profileLoaded.value = true
  }

  if (userResult.status === 'fulfilled') {
    const latestUser = userResult.value || {}
    userStore.updateUser({
      realName: latestUser.realName,
      phone: latestUser.phone,
      college: latestUser.college || latestUser.collegeName,
      major: latestUser.major,
      className: latestUser.className,
      grade: latestUser.grade,
      studentNo: latestUser.studentNo || latestUser.studentId || user.value.studentNo,
      accessLevel: latestUser.accessLevel || user.value.accessLevel
    })
  }
}

async function checkRestriction() {
  try {
    const result = await attendanceApi.checkRestriction()
    restrictionText.value = result?.restricted ? '当前存在报名限制' : '当前无报名限制'
  } catch (error) {
    restrictionText.value = '检查失败，请稍后再试'
  }
}

function logout() {
  userStore.logout()
}

onShow(() => {
  userStore.loadFromStorage()
  loadAccountStatus()
  loadData()
})
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.mine-page {
  min-height: 100vh;
  padding: 0 14px 92px;
  overflow-x: hidden;
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $client-ink;
}

.page-shell {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  padding-top: 14px;
}

.profile-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
  padding: 18px 16px;
  border: 1px solid rgba(255, 214, 204, 0.42);
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.18), transparent 190px),
    linear-gradient(135deg, #ad3440 0%, #cf5c68 58%, #982934 100%);
  color: #fff1ef;
  box-shadow: 0 16px 30px rgba(165, 31, 43, 0.14);
}

.profile-card::after {
  content: '';
  position: absolute;
  right: 16px;
  bottom: 14px;
  width: 64px;
  height: 4px;
  border-radius: 4px;
  background: $client-accent;
  opacity: 0.8;
}

.info-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin-top: 14px;
}

.info-card {
  min-width: 0;
  padding: 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  color: $client-ink;
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.info-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.info-title,
.info-subtitle,
.info-label,
.info-value,
.account-state {
  display: block;
}

.info-title {
  color: $client-ink;
  font-size: 17px;
  font-weight: 900;
  line-height: 1.2;
}

.info-subtitle {
  margin-top: 4px;
  color: $client-muted;
  font-size: 12px;
  font-weight: 700;
}

.info-action {
  min-height: 30px;
  padding: 0 10px;
  border-color: rgba(165, 31, 43, 0.18);
  background: $client-red-soft;
  color: $client-red;
}

.info-list {
  display: grid;
  gap: 9px;
}

.info-row {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  min-height: 24px;
}

.info-label {
  color: $client-muted;
  font-size: 12px;
  font-weight: 800;
}

.info-value {
  overflow: hidden;
  color: $client-ink;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-value.muted {
  color: #98a49f;
  font-weight: 700;
}

.account-state {
  display: inline-flex;
  min-height: 26px;
  flex: 0 0 auto;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  background: $client-red-soft;
  color: $client-red;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.account-state.complete {
  background: #eef5f2;
  color: #1f7a4d;
}

.info-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}

.info-actions .btn--primary {
  border-color: $client-red;
  background: $client-red;
  color: #fff;
}

.avatar {
  display: flex;
  width: 52px;
  height: 52px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: $radius-md;
  border: 1px solid rgba(255, 214, 204, 0.65);
  background: #fff4f1;
  color: $client-ink;
  font-size: 22px;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(91, 17, 25, 0.16);
}

.profile-info {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1;
}

.profile-action {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  min-width: 58px;
  border-color: rgba(255, 214, 204, 0.5);
  background: rgba(255, 246, 244, 0.2);
  color: #fff;
}

.page-kicker,
.profile-name,
.profile-meta,
.metric-label,
.metric-value,
.restriction {
  display: block;
}

.page-kicker {
  color: rgba(255, 248, 246, 0.78);
  font-size: 12px;
  font-weight: 800;
}

.profile-name {
  margin-top: 2px;
  color: #fff;
  font-size: 22px;
  font-weight: 900;
  line-height: 1.2;
}

.profile-meta {
  margin-top: 8px;
  color: rgba(255, 248, 246, 0.9);
  font-size: 13px;
  font-weight: 700;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin: 14px 0;
}

.metric-card,
.panel {
  min-width: 0;
  padding: 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.metric-label {
  color: $client-muted;
  font-size: 12px;
  font-weight: 800;
}

.metric-value {
  margin-top: 8px;
  color: $client-ink;
  font-size: 26px;
  font-weight: 900;
}

.menu-list {
  overflow: hidden;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.menu-item {
  display: flex;
  width: 100%;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  background: transparent;
  color: $client-ink;
  font-weight: 900;
  text-align: left;
}

.menu-item:last-child {
  border-bottom: 0;
}

.menu-left {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.menu-icon {
  display: flex;
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(165, 31, 43, 0.14);
  border-radius: $radius-sm;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.82), transparent),
    $client-red-soft;
}

.menu-icon__img {
  display: block;
  width: 17px;
  height: 17px;
}

.menu-arrow {
  color: $client-accent;
  font-size: 22px;
  font-weight: 700;
}

.panel {
  margin-top: 12px;
}

.section-title__main {
  color: $client-ink;
  font-size: 18px;
  font-weight: 900;
}

.section-title__sub {
  color: #6d7d78;
  font-weight: 700;
}

.panel-action {
  border-color: $client-red;
  background: $client-red;
  color: #fff;
}

.restriction {
  color: $client-muted;
  font-weight: 700;
}

@media (max-width: 430px) {
  .mine-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .profile-card {
    align-items: flex-start;
    padding: 16px 14px;
  }

  .profile-name {
    font-size: 21px;
  }

  .info-layout {
    grid-template-columns: 1fr;
  }
}
</style>
