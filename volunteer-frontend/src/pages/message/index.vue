<template>
  <view class="page message-page safe-bottom">
    <view class="page-shell">
      <view class="page-head">
        <view class="page-brand">
          <text class="page-mark">信</text>
          <view class="page-brand__text">
            <text class="page-kicker">志愿服务平台</text>
            <text class="page-title">消息中心</text>
            <text class="page-subtitle">{{ unreadCount }} 条未读消息</text>
          </view>
        </view>
        <button class="btn page-action" @click="markAllRead">全部已读</button>
      </view>

      <view class="message-list">
        <view
          v-for="item in messages"
          :key="getMessageId(item)"
          class="swipe-row"
          @touchstart="startSwipe($event, item)"
          @touchmove="moveSwipe($event)"
          @touchend="endSwipe(item)"
        >
          <button class="delete-action" @click.stop="deleteMessage(item)">删除</button>
          <view
            class="message-card shell-card"
            :class="{ 'message-card--swiped': swipedId === getMessageId(item) }"
            @click="markRead(item)"
          >
            <view class="message-top">
              <text class="tag" :class="isRead(item) ? 'tag--muted' : ''">{{ typeText(item.type || item.messageType) }}</text>
              <text class="message-time">{{ formatDate(item.createTime || item.createdAt) }}</text>
            </view>
            <text class="message-title">{{ item.title || '系统消息' }}</text>
            <text class="message-content">{{ item.content || item.message || '暂无消息内容' }}</text>
          </view>
        </view>
      </view>

      <view v-if="!messages.length && !loading" class="empty-state">
        <text class="empty-state__title">暂无消息</text>
        <text>活动通知、审核结果和系统公告会在这里出现。</text>
      </view>
      <view v-if="loading" class="loading">加载中...</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { notificationApi } from '@/api'
import { MessageTypeText } from '@/common/enums'

const messages = ref([])
const unreadCount = ref(0)
const loading = ref(false)
const swipedId = ref(null)

let swipeStartX = 0
let swipeStartY = 0
let swipeDeltaX = 0

function extractList(result) {
  if (Array.isArray(result)) return result
  return result?.list || result?.records || []
}

function typeText(type) {
  return MessageTypeText[type] || type || '通知'
}

function getMessageId(item) {
  return item.id || item.messageId
}

function isRead(item) {
  return item.isRead === 1 || item.readFlag === 1 || item.read === true
}

function formatDate(value) {
  if (!value) return '刚刚'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadData() {
  loading.value = true
  try {
    const [listResult, countResult] = await Promise.allSettled([
      notificationApi.getMessageList({ pageNum: 1, pageSize: 50 }),
      notificationApi.getUnreadCount()
    ])
    messages.value = listResult.status === 'fulfilled' ? extractList(listResult.value) : []
    unreadCount.value = countResult.status === 'fulfilled' ? Number(countResult.value || 0) : 0
  } finally {
    loading.value = false
  }
}

async function markAllRead() {
  await notificationApi.markAllAsRead()
  swipedId.value = null
  await loadData()
}

async function markRead(item) {
  if (isRead(item)) return
  await notificationApi.markAsRead(getMessageId(item))
  await loadData()
}

function startSwipe(event, item) {
  const touch = event.touches?.[0]
  if (!touch) return
  swipeStartX = touch.clientX
  swipeStartY = touch.clientY
  swipeDeltaX = 0
  if (swipedId.value && swipedId.value !== getMessageId(item)) {
    swipedId.value = null
  }
}

function moveSwipe(event) {
  const touch = event.touches?.[0]
  if (!touch) return
  const deltaX = touch.clientX - swipeStartX
  const deltaY = touch.clientY - swipeStartY
  if (Math.abs(deltaX) > Math.abs(deltaY)) {
    swipeDeltaX = deltaX
  }
}

function endSwipe(item) {
  if (swipeDeltaX < -40) {
    swipedId.value = getMessageId(item)
  } else if (swipeDeltaX > 20) {
    swipedId.value = null
  }
  swipeDeltaX = 0
}

async function deleteMessage(item) {
  await notificationApi.deleteMessage(getMessageId(item))
  swipedId.value = null
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
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.message-page {
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
.message-top {
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
.message-title,
.message-content {
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
  min-width: 84px;
  border-color: rgba(255, 214, 204, 0.5);
  background: rgba(255, 246, 244, 0.2);
  color: #fff;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.message-card {
  position: relative;
  z-index: 1;
  min-width: 0;
  overflow: hidden;
  padding: 14px 14px 14px 17px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
  transition: transform 0.2s ease;
}

.message-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  width: 3px;
  height: 28px;
  border-radius: 0 3px 3px 0;
  background: $client-accent;
}

.message-card--swiped {
  transform: translateX(-82px);
}

.swipe-row {
  position: relative;
  overflow: hidden;
}

.delete-action {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 74px;
  margin: 0;
  border: 0;
  border-radius: 8px;
  background: #c2413a;
  color: #fff;
  font-size: 14px;
  font-weight: 800;
}

.delete-action::after {
  border: 0;
}

.message-time {
  flex: 0 0 auto;
  max-width: 50%;
  overflow: hidden;
  color: $client-muted;
  font-size: 12px;
  font-weight: 800;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-title {
  margin-top: 12px;
  overflow: hidden;
  color: $client-ink;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-content {
  margin-top: 8px;
  color: #72827d;
  font-size: 13px;
  line-height: 1.6;
}

.loading {
  padding: 20px;
  color: $client-muted;
  text-align: center;
}

.message-page .tag {
  border: 1px solid rgba(165, 31, 43, 0.14);
  background: $client-red-soft;
  color: $client-red;
  font-weight: 900;
}

.message-page .tag--muted {
  border-color: rgba(16, 35, 31, 0.1);
  background: #f1f3f2;
  color: $client-muted;
}

.message-page .empty-state {
  border-color: rgba(165, 31, 43, 0.16);
  background: #fff7f5;
  color: $client-muted;
}

.message-page .empty-state__title {
  color: $client-ink;
}

@media (max-width: 430px) {
  .message-page {
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
