<template>
  <view class="page client-page safe-bottom">
    <view class="client-shell">
      <view class="client-hero">
        <view class="client-hero__top">
          <view class="client-brand">
            <text class="brand-mark">重</text>
            <view>
              <text class="client-kicker">志愿服务平台</text>
              <text class="client-title">活动大厅</text>
            </view>
          </view>
          <button class="client-refresh" :disabled="loading" @click="loadActivities">刷新</button>
        </view>
        <text class="client-subtitle">查看可报名活动，处理签到、消息和个人事项。</text>
      </view>

      <view class="quick-grid">
        <button v-for="item in quickLinks" :key="item.url" class="quick-item" @click="go(item.url)">
          <view class="quick-icon">
            <image class="quick-icon__img" :src="item.icon" mode="aspectFit" />
          </view>
          <text class="quick-label">{{ item.label }}</text>
        </button>
      </view>

      <view class="shell-card content-panel">
        <view class="panel-head">
          <view>
            <text class="section-title__main">活动列表</text>
            <text class="section-title__sub">按名称、地点或组织方筛选</text>
          </view>
          <text class="panel-count">{{ loading ? '读取中' : activities.length + ' 项' }}</text>
        </view>

        <view class="search-box">
          <input
            class="input"
            v-model="activityQuery.keyword"
            placeholder="搜索活动名称、地点或组织方"
            confirm-type="search"
            @confirm="loadActivities"
          />
          <button class="search-button" :disabled="loading" @click="loadActivities">查询</button>
        </view>

        <view v-if="loading" class="empty-state">
          <text class="empty-state__title">加载中</text>
          <text>正在获取活动列表</text>
        </view>

        <view v-else-if="activities.length === 0" class="empty-state">
          <text class="empty-state__title">暂无活动</text>
          <text>当前没有可展示的活动</text>
        </view>

        <view v-else class="activity-list">
          <view v-for="activity in activities" :key="activity.id" class="activity-item" @click="goDetail(activity.id)">
            <view class="activity-item__main">
              <text class="activity-title">{{ activity.title || activity.name || '未命名活动' }}</text>
              <text class="activity-meta">{{ activity.location || '地点待定' }}</text>
              <text class="activity-meta">{{ formatTime(activity.startTime) }} - {{ formatTime(activity.endTime) }}</text>
            </view>
            <view class="activity-side">
              <text class="tag" :class="statusClass(activity.status)">{{ statusText(activity.status) }}</text>
              <text class="activity-count">{{ activity.currentParticipants || 0 }}/{{ activity.maxParticipants || '不限' }}</text>
            </view>
          </view>
        </view>

        <text v-if="error" class="error-line">{{ error }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { activityApi } from '@/api'

const loading = ref(false)
const error = ref('')
const activities = ref([])
const activityQuery = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 20 })

const clientIconColor = 'a51f2b'
const navIcon = name => `https://api.iconify.design/lucide:${name}.svg?color=%23${clientIconColor}`

const quickLinks = [
  { label: '我的活动', icon: navIcon('calendar-check'), url: '/pages/my-activities/index' },
  { label: '消息中心', icon: navIcon('message-square-text'), url: '/pages/message/index' },
  { label: '志愿工具', icon: navIcon('wrench'), url: '/pages/tools/index' },
  { label: '个人中心', icon: navIcon('user-round'), url: '/pages/mine/index' }
]

function listFrom(result) {
  if (Array.isArray(result)) return result
  return result?.records || result?.list || []
}

function formatTime(value) {
  if (!value) return '时间待定'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusText(status) {
  const map = {
    DRAFT: '草稿',
    AUDITING: '审核中',
    RECRUITING: '招募中',
    PUBLISHED: '已发布',
    APPROVED: '已通过',
    ONGOING: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REJECTED: '已拒绝'
  }
  return map[status] || status || '未知'
}

function statusClass(status) {
  if (['CANCELLED', 'REJECTED'].includes(status)) return 'tag--red'
  if (['COMPLETED', 'APPROVED', 'PUBLISHED'].includes(status)) return 'tag--blue'
  if (['AUDITING', 'DRAFT'].includes(status)) return 'tag--amber'
  return ''
}

async function loadActivities() {
  loading.value = true
  error.value = ''
  try {
    const res = await activityApi.getActivityList({
      pageNum: activityQuery.pageNum,
      pageSize: activityQuery.pageSize,
      keyword: activityQuery.keyword.trim(),
      status: activityQuery.status.trim()
    }, { showLoading: false })
    activities.value = listFrom(res)
  } catch (err) {
    activities.value = []
    error.value = err?.message || '活动列表加载失败'
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function go(url) {
  uni.navigateTo({ url })
}

function goDetail(id) {
  if (!id) return
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

onMounted(loadActivities)

onPullDownRefresh(loadActivities)
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-paper: #fffafa;
$client-red: #a51f2b;
$client-red-dark: #73151f;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;
$client-rose: #c84b5b;

.client-page {
  min-height: 100vh;
  padding: 0 14px 96px;
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $client-ink;
}

.client-shell {
  max-width: 760px;
  margin: 0 auto;
  padding-top: 14px;
}

.client-hero {
  position: relative;
  overflow: hidden;
  padding: 18px 16px 16px;
  border: 1px solid rgba(255, 214, 204, 0.42);
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.18), transparent 190px),
    linear-gradient(135deg, #ad3440 0%, #cf5c68 58%, #982934 100%);
  color: #fff1ef;
  box-shadow: 0 16px 30px rgba(165, 31, 43, 0.14);
}

.client-hero::after {
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

.client-hero__top,
.client-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.client-hero__top {
  justify-content: space-between;
  gap: 14px;
}

.brand-mark,
.client-title,
.client-kicker,
.client-subtitle,
.quick-label,
.activity-title,
.activity-meta,
.activity-count {
  display: block;
}

.brand-mark {
  display: flex;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: $client-ink;
  font-size: 18px;
  font-weight: 900;
  box-shadow: 0 8px 18px rgba(91, 17, 25, 0.16);
}

.client-kicker {
  color: rgba(255, 248, 246, 0.78);
  font-size: 12px;
  font-weight: 800;
}

.client-title {
  margin-top: 2px;
  color: #fff;
  font-size: 26px;
  font-weight: 900;
  line-height: 1.15;
}

.client-subtitle {
  max-width: 520px;
  margin-top: 14px;
  color: rgba(255, 248, 246, 0.9);
  font-size: 13px;
  font-weight: 700;
}

.client-refresh,
.search-button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.5);
  border-radius: $radius-sm;
  font-size: 13px;
  font-weight: 800;
}

.client-refresh {
  min-width: 64px;
  padding: 0 14px;
  background: rgba(255, 246, 244, 0.2);
  color: #fff;
}

.client-refresh[disabled],
.search-button[disabled] {
  opacity: 0.58;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
  margin: 12px 0 16px;
}

.quick-item {
  position: relative;
  display: flex;
  min-height: 58px;
  align-items: center;
  gap: 12px;
  overflow: hidden;
  padding: 9px 12px;
  border: 1px solid rgba(165, 31, 43, 0.1);
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.96);
  color: $client-ink;
  text-align: left;
  box-shadow: 0 8px 18px rgba(16, 35, 31, 0.06);
}

.quick-item::after {
  content: '';
  width: 18px;
  height: 2px;
  flex: 0 0 auto;
  margin-left: auto;
  border-radius: 2px;
  background: rgba(224, 139, 115, 0.72);
}

.quick-item:active {
  border-color: rgba(165, 31, 43, 0.32);
  background: #fff7f5;
}

.quick-icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(165, 31, 43, 0.14);
  border-radius: $radius-sm;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.82), transparent),
    $client-red-soft;
}

.quick-icon__img {
  display: block;
  width: 18px;
  height: 18px;
}

.quick-label {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-panel {
  padding: 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-title__main {
  color: $client-ink;
  font-size: 18px;
  font-weight: 900;
}

.section-title__sub {
  margin-top: 4px;
  color: #6d7d78;
  font-weight: 700;
}

.panel-count {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  padding: 0 9px;
  border: 1px solid rgba(165, 31, 43, 0.14);
  border-radius: 999px;
  background: $client-red-soft;
  color: $client-red;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.search-box {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 16px;
  padding: 10px;
  border: 1px solid rgba(16, 35, 31, 0.08);
  border-radius: $radius-md;
  background: $client-paper;
}

.client-page .input {
  min-height: 38px;
  border-color: rgba(16, 35, 31, 0.12);
  background: #fff;
}

.client-page .input:focus {
  border-color: rgba(165, 31, 43, 0.42);
}

.search-button {
  min-width: 64px;
  padding: 0 14px;
  border-color: $client-red;
  background: $client-red;
  color: #fff;
  box-shadow: 0 8px 18px rgba(165, 31, 43, 0.2);
}

.content-panel .empty-state {
  min-height: 184px;
  border-color: rgba(165, 31, 43, 0.16);
  background: #fff7f5;
  color: $client-muted;
}

.content-panel .empty-state__title {
  color: $client-ink;
}

.activity-list {
  overflow: hidden;
  border: 1px solid rgba(16, 35, 31, 0.1);
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.95);
}

.activity-item {
  position: relative;
  display: grid;
  min-height: 78px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px 14px 14px 17px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  background: #fff;
}

.activity-item:last-child {
  border-bottom: 0;
}

.activity-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  width: 3px;
  height: 28px;
  border-radius: 0 3px 3px 0;
  background: $client-accent;
}

.activity-item:active {
  background: #fff9f7;
}

.activity-item__main {
  min-width: 0;
}

.activity-title {
  overflow: hidden;
  color: $client-ink;
  font-size: 15px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  margin-top: 6px;
  overflow: hidden;
  color: #72827d;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

.activity-count {
  color: $client-muted;
  font-size: 12px;
  font-weight: 800;
}

.client-page .tag {
  border: 1px solid rgba(165, 31, 43, 0.14);
  background: $client-red-soft;
  color: $client-red;
  font-weight: 900;
}

.client-page .tag--blue {
  border-color: rgba(37, 99, 235, 0.16);
  background: #edf3ff;
  color: #2563eb;
}

.client-page .tag--amber {
  border-color: rgba(183, 121, 31, 0.18);
  background: #fff5dd;
  color: #9b6419;
}

.client-page .tag--red {
  border-color: rgba(194, 65, 58, 0.18);
  background: #fff1ef;
  color: #c2413a;
}

.error-line {
  display: block;
  margin-top: 12px;
  color: #c2413a;
  font-weight: 700;
}

@media (max-width: 430px) {
  .client-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .client-hero {
    padding: 16px 14px;
  }

  .client-hero__top {
    align-items: flex-start;
  }

  .client-title {
    font-size: 24px;
  }

  .client-refresh,
  .search-button {
    min-height: 36px;
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-box {
    grid-template-columns: minmax(0, 1fr);
  }

  .search-button {
    width: 100%;
  }

  .activity-item {
    grid-template-columns: minmax(0, 1fr);
  }

  .activity-side {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}
</style>
