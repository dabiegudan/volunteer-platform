<template>
  <view class="page statistics-page safe-bottom">
    <view class="page-shell">
    <view class="page-head">
      <view class="page-brand">
        <text class="page-mark">统</text>
        <view class="page-brand__text">
          <text class="page-kicker">志愿服务平台</text>
          <text class="page-title">数据统计</text>
          <text class="page-subtitle">活动趋势、服务时长、分类和签到表现</text>
        </view>
      </view>
      <button class="btn page-action" @click="loadData">刷新</button>
    </view>

    <view class="metric-grid">
      <view class="metric-card shell-card">
        <text class="metric-label">趋势点数</text>
        <text class="metric-value">{{ trend.length }}</text>
      </view>
      <view class="metric-card shell-card">
        <text class="metric-label">服务活动</text>
        <text class="metric-value">{{ duration.length }}</text>
      </view>
      <view class="metric-card shell-card">
        <text class="metric-label">签到人数</text>
        <text class="metric-value">{{ checkin.checkins || 0 }}</text>
      </view>
      <view class="metric-card shell-card">
        <text class="metric-label">画像评分</text>
        <text class="metric-value">{{ profile.creditScore || 0 }}</text>
      </view>
    </view>

    <view class="shell-card panel">
      <view class="section-title">
        <view>
          <text class="section-title__main">服务时长排行</text>
          <text class="section-title__sub">按活动聚合的有效服务时长</text>
        </view>
      </view>
      <view v-if="duration.length" class="bar-list">
        <view v-for="item in duration" :key="item.organizationName" class="bar-row">
          <view class="bar-info">
            <text class="bar-title">{{ item.organizationName }}</text>
            <text class="bar-value">{{ formatNumber(item.totalDuration) }} 小时</text>
          </view>
          <view class="bar-track">
            <view class="bar-fill" :style="{ width: barWidth(item.totalDuration, maxDuration) }"></view>
          </view>
        </view>
      </view>
      <view v-else class="empty-state">
        <text class="empty-state__title">暂无时长统计</text>
        <text>完成活动并记录时长后会生成排行。</text>
      </view>
    </view>

    <view class="shell-card panel">
      <view class="section-title">
        <view>
          <text class="section-title__main">活动分类</text>
          <text class="section-title__sub">当前统计口径下的活动类型分布</text>
        </view>
      </view>
      <view v-if="categoryRows.length" class="category-list">
        <view v-for="item in categoryRows" :key="item.name" class="category-row">
          <text>{{ item.name }}</text>
          <text>{{ item.value }}</text>
        </view>
      </view>
      <view v-else class="empty-state">
        <text class="empty-state__title">暂无分类数据</text>
        <text>活动指标入库后会显示分类分布。</text>
      </view>
    </view>

    <view class="shell-card panel">
      <view class="section-title">
        <view>
          <text class="section-title__main">志愿者画像</text>
          <text class="section-title__sub">{{ profile.userName || '当前用户' }}</text>
        </view>
      </view>
      <view class="score-list">
        <view v-for="item in scoreRows" :key="item.name" class="score-row">
          <text class="score-name">{{ item.name }}</text>
          <view class="bar-track">
            <view class="bar-fill" :style="{ width: barWidth(item.value, 100) }"></view>
          </view>
          <text class="score-value">{{ item.value }}</text>
        </view>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中...</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { statisticsApi } from '@/api'

const loading = ref(false)
const trend = ref([])
const duration = ref([])
const category = ref({})
const checkin = ref({})
const profile = ref({})

const maxDuration = computed(() => Math.max(1, ...duration.value.map(item => Number(item.totalDuration || 0))))
const categoryRows = computed(() => {
  const names = category.value.categories || []
  const values = category.value.values || []
  return names.map((name, index) => ({ name, value: values[index] || 0 }))
})
const scoreRows = computed(() => {
  const scores = profile.value.skillScores || {}
  return Object.keys(scores).map(name => ({ name, value: Number(scores[name] || 0) }))
})

function formatDate(date) {
  return date.toISOString().slice(0, 10)
}

function formatNumber(value) {
  return Number(value || 0).toFixed(1)
}

function barWidth(value, max) {
  const width = Math.max(4, Math.min(100, (Number(value || 0) / Number(max || 1)) * 100))
  return `${width}%`
}

async function loadData() {
  loading.value = true
  const endDate = new Date()
  const startDate = new Date()
  startDate.setDate(endDate.getDate() - 30)
  try {
    const [trendResult, durationResult, categoryResult, checkinResult, profileResult] = await Promise.allSettled([
      statisticsApi.getActivityTrend({ startDate: formatDate(startDate), endDate: formatDate(endDate), period: 'DAY' }),
      statisticsApi.getServiceDuration({ startDate: formatDate(startDate), endDate: formatDate(endDate) }),
      statisticsApi.getActivityCategory(),
      statisticsApi.getCheckinRate(),
      statisticsApi.getVolunteerProfile()
    ])
    trend.value = trendResult.status === 'fulfilled' && Array.isArray(trendResult.value) ? trendResult.value : []
    duration.value = durationResult.status === 'fulfilled' && Array.isArray(durationResult.value) ? durationResult.value : []
    category.value = categoryResult.status === 'fulfilled' ? categoryResult.value || {} : {}
    checkin.value = checkinResult.status === 'fulfilled' ? checkinResult.value || {} : {}
    profile.value = profileResult.status === 'fulfilled' ? profileResult.value || {} : {}
  } finally {
    loading.value = false
  }
}

onShow(loadData)

onPullDownRefresh(async () => {
  try {
    await loadData()
  } finally {
    uni.stopPullDownRefresh()
  }
})
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-paper: #fffafa;
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.statistics-page {
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

.page-head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.page-head::after {
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

.page-brand {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  gap: 12px;
}

.page-brand__text {
  min-width: 0;
  flex: 1;
}

.page-mark {
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

.page-action {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  min-width: 64px;
  border-color: rgba(255, 214, 204, 0.5);
  background: rgba(255, 246, 244, 0.2);
  color: #fff;
}

.page-title,
.page-kicker,
.page-subtitle,
.metric-label,
.metric-value,
.section-title__main,
.section-title__sub,
.bar-title,
.bar-value {
  display: block;
}

.page-kicker {
  color: rgba(255, 248, 246, 0.78);
  font-size: 12px;
  font-weight: 800;
}

.page-title {
  margin-top: 2px;
  color: #fff;
  font-size: 26px;
  font-weight: 900;
  line-height: 1.15;
}

.page-subtitle {
  margin-top: 10px;
  color: rgba(255, 248, 246, 0.9);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-top: 16px;
}

.metric-card {
  position: relative;
  overflow: hidden;
  min-height: 92px;
  padding: 14px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.metric-card::after {
  content: '';
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 34px;
  height: 3px;
  border-radius: 3px;
  background: rgba(224, 139, 115, 0.72);
}

.metric-label {
  color: $client-muted;
  font-size: 12px;
  font-weight: 800;
}

.metric-value {
  margin-top: 8px;
  color: $client-ink;
  font-size: 24px;
  font-weight: 900;
}

.panel {
  margin-top: 12px;
  padding: 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
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

.bar-list,
.category-list,
.score-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-info,
.category-row,
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.bar-title {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: $client-ink;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-value,
.score-value {
  color: $client-muted;
  font-size: 12px;
  font-weight: 800;
}

.bar-track {
  overflow: hidden;
  width: 100%;
  height: 8px;
  border-radius: 999px;
  background: rgba(165, 31, 43, 0.12);
}

.bar-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, $client-red, $client-accent);
}

.category-row {
  min-height: 34px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  color: $client-ink;
  font-weight: 800;
}

.category-row:last-child {
  border-bottom: 0;
}

.score-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 42px;
}

.score-name {
  overflow: hidden;
  color: $client-ink;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading {
  padding: 20px;
  color: $client-muted;
  text-align: center;
}
</style>
