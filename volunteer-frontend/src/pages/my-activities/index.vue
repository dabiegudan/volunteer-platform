<template>
  <view class="page list-page safe-bottom">
    <view class="page-shell">
      <view class="page-head">
        <view class="page-brand">
          <text class="page-mark">活</text>
          <view class="page-brand__text">
            <text class="page-kicker">志愿服务平台</text>
            <text class="page-title">我的活动</text>
            <text class="page-subtitle">查看报名、审核、签到和完成状态</text>
          </view>
        </view>
        <button class="btn page-action" @click="loadData">刷新</button>
      </view>

      <scroll-view scroll-x class="tabs">
        <view class="tab-row">
          <button
            v-for="item in tabs"
            :key="item.value"
            class="chip"
            :class="{ active: status === item.value }"
            @click="switchStatus(item.value)"
          >
            {{ item.label }}
          </button>
        </view>
      </scroll-view>

      <view class="activity-list">
        <view v-for="item in registrations" :key="item.id || item.activityId" class="registration-card shell-card">
          <view class="registration-top">
            <text class="tag" :class="statusClass(item.status)">{{ statusText(item.status) }}</text>
            <text class="hours">{{ item.durationHours || item.duration || 0 }} 小时</text>
          </view>
          <text class="activity-title">{{ item.activityTitle || item.title || item.activityName || '活动报名' }}</text>
          <text class="activity-meta">{{ item.location || item.activityLocation || '地点待定' }} · {{ formatDate(item.startTime) }}</text>
          <view class="card-actions">
            <button class="btn btn--ghost" @click="goDetail(item.activityId || item.id)">详情</button>
            <button class="btn btn--danger" @click="cancel(item.activityId || item.id)">取消报名</button>
          </view>
        </view>
      </view>

      <view v-if="!registrations.length && !loading" class="empty-state">
        <text class="empty-state__title">暂无报名记录</text>
        <text>去活动大厅找一个合适的志愿服务吧。</text>
      </view>

      <view v-if="loading" class="loading">加载中...</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { activityApi } from '@/api'
import { RegistrationStatusText } from '@/common/enums'

const tabs = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已签到', value: 'CHECKED_IN' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' }
]

const status = ref('')
const registrations = ref([])
const loading = ref(false)

function extractList(result) {
  if (Array.isArray(result)) return result
  return result?.list || result?.records || []
}

function statusText(value) {
  return RegistrationStatusText[value] || value || '未知'
}

function statusClass(value) {
  if (['APPROVED', 'COMPLETED', 'CHECKED_IN'].includes(value)) return 'tag--blue'
  if (['REJECTED', 'CANCELLED'].includes(value)) return 'tag--red'
  if (value === 'PENDING') return 'tag--amber'
  return ''
}

function formatDate(value) {
  if (!value) return '暂无时间'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadData() {
  loading.value = true
  try {
    const result = await activityApi.getUserRegistrations({
      status: status.value,
      pageNum: 1,
      pageSize: 50
    }, { showLoading: false })
    registrations.value = extractList(result)
  } finally {
    loading.value = false
  }
}

function switchStatus(value) {
  status.value = value
  loadData()
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

async function cancel(id) {
  await activityApi.cancelRegistration(id)
  uni.showToast({ title: '已取消报名', icon: 'success' })
  await loadData()
}

loadData()

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

.list-page {
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

.page-head,
.page-brand,
.registration-top,
.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-head {
  position: relative;
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

.page-brand {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  justify-content: flex-start;
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

.page-title,
.page-kicker,
.page-subtitle,
.activity-title,
.activity-meta {
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

.page-action {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  min-width: 64px;
  border-color: rgba(255, 214, 204, 0.5);
  background: rgba(255, 246, 244, 0.2);
  color: #fff;
}

.tabs {
  width: 100%;
  max-width: 100%;
  margin: 16px 0;
  overflow: hidden;
  white-space: nowrap;
}

.tab-row {
  display: inline-flex;
  gap: 8px;
  min-width: max-content;
}

.chip {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  color: #7d4046;
  font-weight: 800;
  box-shadow: 0 6px 14px rgba(16, 35, 31, 0.04);
}

.chip.active {
  border-color: rgba(165, 31, 43, 0.24);
  background: $client-red-soft;
  color: $client-red;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.registration-card {
  position: relative;
  min-width: 0;
  overflow: hidden;
  padding: 14px 14px 14px 17px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.registration-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  width: 3px;
  height: 28px;
  border-radius: 0 3px 3px 0;
  background: $client-accent;
}

.hours {
  color: $client-muted;
  font-size: 12px;
  font-weight: 900;
}

.activity-title {
  margin-top: 12px;
  overflow: hidden;
  color: $client-ink;
  font-size: 16px;
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

.card-actions {
  justify-content: flex-end;
  margin-top: 14px;
}

.loading {
  padding: 20px;
  color: $client-muted;
  text-align: center;
}

.list-page .tag {
  border: 1px solid rgba(165, 31, 43, 0.14);
  background: $client-red-soft;
  color: $client-red;
  font-weight: 900;
}

.list-page .tag--blue {
  border-color: rgba(37, 99, 235, 0.16);
  background: #edf3ff;
  color: #2563eb;
}

.list-page .tag--amber {
  border-color: rgba(183, 121, 31, 0.18);
  background: #fff5dd;
  color: #9b6419;
}

.list-page .tag--red {
  border-color: rgba(194, 65, 58, 0.18);
  background: #fff1ef;
  color: #c2413a;
}

.list-page .btn--ghost {
  color: $client-red;
}

.list-page .btn--danger {
  border-color: rgba(194, 65, 58, 0.18);
  background: #fff1ef;
  color: #c2413a;
}

.list-page .empty-state {
  border-color: rgba(165, 31, 43, 0.16);
  background: #fff7f5;
  color: $client-muted;
}

.list-page .empty-state__title {
  color: $client-ink;
}

@media (max-width: 430px) {
  .list-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .page-head {
    align-items: flex-start;
    padding: 16px 14px;
  }

  .page-title {
    font-size: 24px;
  }
}
</style>
