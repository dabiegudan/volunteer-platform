<template>
  <view class="page detail-page safe-bottom">
    <view class="detail-shell">
      <view v-if="loading" class="detail-state empty-state">
        <text class="empty-state__title">加载中</text>
        <text>正在获取活动详情</text>
      </view>

      <view v-else-if="!activity" class="detail-state empty-state">
        <text class="empty-state__title">活动不存在</text>
        <text>{{ error || '未找到活动详情' }}</text>
      </view>

      <view v-else class="detail">
        <view class="detail-hero">
          <view class="detail-hero__top">
            <view class="activity-mark">
              <text>志</text>
            </view>
            <view class="hero-title">
              <text class="hero-kicker">活动详情</text>
              <text class="detail-title">{{ activity.title || activity.name || '未命名活动' }}</text>
            </view>
            <text class="tag hero-tag" :class="statusClass(activity.status)">{{ statusText(activity.status) }}</text>
          </view>

          <view class="hero-meta-grid">
            <view class="hero-meta-item">
              <text class="hero-meta-icon">地</text>
              <view>
                <text class="hero-meta-label">活动地点</text>
                <text class="hero-meta-value">{{ activity.location || '地点待定' }}</text>
              </view>
            </view>
            <view class="hero-meta-item">
              <text class="hero-meta-icon">时</text>
              <view>
                <text class="hero-meta-label">活动时间</text>
                <text class="hero-meta-value">{{ formatTime(activity.startTime) }} - {{ formatTime(activity.endTime) }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="detail-layout">
          <view class="detail-main">
            <view class="shell-card section">
              <view class="section-title">
                <view>
                  <text class="section-title__main">活动说明</text>
                  <text class="section-title__sub">面向志愿者的现场说明</text>
                </view>
              </view>
              <text class="paragraph">{{ activity.description || activity.content || '暂无活动说明' }}</text>
            </view>

            <view class="shell-card section">
              <view class="section-title">
                <view>
                  <text class="section-title__main">活动安排</text>
                  <text class="section-title__sub">报名前请确认时间与组织信息</text>
                </view>
              </view>
              <view class="info-list">
                <view class="info-row">
                  <text class="info-label">开始时间</text>
                  <text class="info-value">{{ formatTime(activity.startTime) }}</text>
                </view>
                <view class="info-row">
                  <text class="info-label">结束时间</text>
                  <text class="info-value">{{ formatTime(activity.endTime) }}</text>
                </view>
                <view class="info-row">
                  <text class="info-label">服务时长</text>
                  <text class="info-value">{{ durationText(activity) }}</text>
                </view>
                <view class="info-row">
                  <text class="info-label">组织方</text>
                  <text class="info-value">{{ activity.organizerName || '未设置' }}</text>
                </view>
              </view>
            </view>
          </view>

          <view class="detail-side">
            <view class="shell-card action-panel">
              <view class="section-title">
                <view>
                  <text class="section-title__main">报名信息</text>
                  <text class="section-title__sub">当前活动容量</text>
                </view>
              </view>

              <view class="metric-grid">
                <view class="metric-box">
                  <text class="metric">{{ activity.currentParticipants || 0 }}</text>
                  <text class="metric-label">已报名</text>
                </view>
                <view class="metric-box">
                  <text class="metric">{{ activity.maxParticipants || '不限' }}</text>
                  <text class="metric-label">名额上限</text>
                </view>
              </view>

              <view class="progress-block">
                <view class="progress-head">
                  <text>报名进度</text>
                  <text>{{ registrationRateText(activity) }}</text>
                </view>
                <view class="progress-track">
                  <view class="progress-fill" :class="{ 'progress-fill--open': !hasParticipantLimit(activity) }" :style="{ width: `${registrationRate(activity)}%` }"></view>
                </view>
              </view>

              <button v-if="registered" class="btn btn--danger btn--block action-button" :disabled="submitting" @click="cancelRegistration">
                取消报名
              </button>
              <button v-else class="btn btn--primary btn--block action-button" :disabled="submitting" @click="register">
                立即报名
              </button>
              <text class="action-hint">{{ registered ? '你已报名该活动，可在我的活动中查看记录。' : '提交后将同步到个人报名记录。' }}</text>
            </view>

            <view class="shell-card organizer-panel">
              <text class="organizer-label">组织方</text>
              <text class="organizer-name">{{ activity.organizerName || '未设置' }}</text>
              <text class="organizer-desc">活动状态：{{ statusText(activity.status) }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { activityApi } from '@/api/activity'

const activityId = ref(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const activity = ref(null)
const registered = ref(false)

async function loadDetail() {
  if (!activityId.value) return
  loading.value = true
  error.value = ''
  try {
    activity.value = await activityApi.getActivityDetail(activityId.value)
    try {
      const reg = await activityApi.getUserRegistration(activityId.value, { showErrorToast: false })
      registered.value = Boolean(reg)
    } catch (regError) {
      registered.value = false
    }
  } catch (err) {
    activity.value = null
    error.value = err?.message || '活动详情加载失败'
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function register() {
  submitting.value = true
  try {
    await activityApi.registerActivity(activityId.value)
    registered.value = true
    uni.showToast({ title: '报名成功', icon: 'success' })
    await loadDetail()
  } catch (err) {
    uni.showToast({ title: err?.message || '报名失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

async function cancelRegistration() {
  submitting.value = true
  try {
    await activityApi.cancelRegistration(activityId.value)
    registered.value = false
    uni.showToast({ title: '已取消报名', icon: 'success' })
    await loadDetail()
  } catch (err) {
    uni.showToast({ title: err?.message || '取消失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function formatTime(value) {
  if (!value) return '时间待定'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusText(status) {
  const map = {
    RECRUITING: '招募中',
    PUBLISHED: '已发布',
    APPROVED: '已通过',
    ONGOING: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  }
  return map[status] || status || '未知'
}

function statusClass(status) {
  if (status === 'CANCELLED') return 'tag--red'
  if (status === 'COMPLETED') return 'tag--muted'
  if (status === 'ONGOING') return 'tag--blue'
  return ''
}

function durationText(item) {
  const value = item?.durationHours || item?.duration || 0
  return value ? `${value} 小时` : '待确认'
}

function registrationRate(item) {
  const max = Number(item?.maxParticipants || 0)
  if (!max) return 100
  const current = Number(item?.currentParticipants || 0)
  return Math.min(100, Math.round((current / max) * 100))
}

function hasParticipantLimit(item) {
  return Number(item?.maxParticipants || 0) > 0
}

function registrationRateText(item) {
  return hasParticipantLimit(item) ? `${registrationRate(item)}%` : '不限'
}

onLoad((query) => {
  activityId.value = query?.id
  loadDetail()
})
onPullDownRefresh(loadDetail)
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.detail-page {
  min-height: 100vh;
  padding: 24px;
  padding-bottom: calc(24px + env(safe-area-inset-bottom));
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
}

.detail-shell {
  width: min(1120px, 100%);
  margin: 0 auto;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-state {
  min-height: 260px;
  border-style: solid;
  background: rgba(255, 255, 255, 0.8);
}

.section {
  padding: 20px;
}

.detail-title,
.hero-kicker,
.hero-meta-label,
.hero-meta-value,
.paragraph,
.metric,
.metric-label,
.info-label,
.info-value,
.action-hint,
.organizer-label,
.organizer-name,
.organizer-desc {
  display: block;
}

.detail-hero {
  position: relative;
  overflow: hidden;
  padding: 22px;
  border: 1px solid rgba(255, 214, 204, 0.42);
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.16), transparent 180px),
    linear-gradient(135deg, #ad3440 0%, #cf5c68 58%, #982934 100%);
  color: #fff1ef;
  box-shadow: 0 18px 36px rgba(91, 17, 25, 0.16);
}

.detail-hero::after {
  content: '';
  position: absolute;
  right: -48px;
  bottom: -86px;
  width: 260px;
  height: 260px;
  border: 1px solid rgba(255, 244, 241, 0.22);
  border-radius: 50%;
}

.detail-hero__top {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.activity-mark {
  display: flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: #2a1214;
  font-size: 22px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(91, 17, 25, 0.2);
}

.hero-title {
  min-width: 0;
}

.hero-kicker {
  color: rgba(255, 241, 239, 0.7);
  font-size: 12px;
  font-weight: 900;
}

.detail-title {
  margin-top: 8px;
  color: #fff;
  font-size: 28px;
  font-weight: 900;
  line-height: 1.25;
}

.hero-tag {
  border: 1px solid rgba(255, 214, 204, 0.4);
  background: rgba(255, 244, 241, 0.16);
  color: #fff;
}

.hero-meta-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 24px;
}

.hero-meta-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 76px;
  padding: 12px;
  border: 1px solid rgba(255, 214, 204, 0.2);
  border-radius: $radius-md;
  background: rgba(255, 246, 244, 0.1);
}

.hero-meta-icon {
  display: flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: $radius-sm;
  background: rgba(255, 244, 241, 0.16);
  color: #ffd4ca;
  font-weight: 900;
}

.hero-meta-label {
  color: rgba(255, 241, 239, 0.62);
  font-size: 12px;
  font-weight: 800;
}

.hero-meta-value {
  overflow: hidden;
  margin-top: 5px;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: 16px;
  align-items: start;
}

.detail-main,
.detail-side {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
}

.paragraph {
  color: $color-muted;
  line-height: 1.7;
  white-space: pre-line;
}

.info-list {
  display: grid;
  border: 1px solid rgba(16, 35, 31, 0.08);
  border-radius: $radius-md;
  overflow: hidden;
}

.info-row {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 46px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  background: #fffafa;
}

.info-row:last-child {
  border-bottom: 0;
}

.info-label {
  color: $color-muted;
  font-size: 12px;
  font-weight: 800;
}

.info-value {
  min-width: 0;
  color: $color-text;
  font-weight: 800;
}

.action-panel,
.organizer-panel {
  padding: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.metric-box {
  min-height: 86px;
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background: #fff8f6;
}

.metric {
  color: #2a1214;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.15;
}

.metric-label {
  margin-top: 6px;
  color: $color-muted;
  font-size: 12px;
  font-weight: 800;
}

.progress-block {
  margin-top: 18px;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: $color-muted;
  font-size: 12px;
  font-weight: 800;
}

.progress-track {
  height: 8px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #f1e7e5;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #a51f2b, #cf5c68);
}

.progress-fill--open {
  opacity: 0.44;
}

.action-button {
  min-height: 42px;
  margin-top: 18px;
  border-radius: $radius-sm;
  font-size: 14px;
  font-weight: 900;
}

.action-hint {
  margin-top: 10px;
  color: $color-muted;
  font-size: 12px;
  line-height: 1.6;
}

.organizer-panel {
  position: relative;
  overflow: hidden;
  background: #fffafa;
}

.organizer-panel::before {
  content: '';
  position: absolute;
  left: 0;
  top: 18px;
  width: 3px;
  height: 32px;
  border-radius: 0 3px 3px 0;
  background: #e08b73;
}

.organizer-label {
  color: $color-muted;
  font-size: 12px;
  font-weight: 800;
}

.organizer-name {
  margin-top: 8px;
  color: $color-text;
  font-size: 18px;
  font-weight: 900;
  line-height: 1.3;
}

.organizer-desc {
  margin-top: 8px;
  color: $color-muted;
  font-size: 13px;
}

@media (max-width: 900px) {
  .detail-page {
    padding: 14px 12px 28px;
    padding-bottom: calc(28px + env(safe-area-inset-bottom));
  }

  .detail-hero {
    padding: 18px 16px;
  }

  .detail-hero__top {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .hero-tag {
    justify-self: start;
    grid-column: 2;
  }

  .detail-title {
    font-size: 23px;
  }

  .hero-meta-grid,
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .section {
    padding: 18px;
  }
}

@media (max-width: 430px) {
  .detail-hero__top {
    gap: 10px;
  }

  .activity-mark {
    width: 42px;
    height: 42px;
    font-size: 20px;
  }

  .hero-meta-value {
    white-space: normal;
  }

  .info-row {
    grid-template-columns: 88px minmax(0, 1fr);
    padding: 0 12px;
  }
}
</style>
