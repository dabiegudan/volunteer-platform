<template>
  <view class="page tools-page safe-bottom">
    <view class="page-shell">
      <view class="page-head">
        <view class="page-brand">
          <text class="page-mark">工</text>
          <view class="page-brand__text">
            <text class="page-kicker">志愿服务平台</text>
            <text class="page-title">志愿工具</text>
            <text class="page-subtitle">签到、补签、报表和咨询入口集中处理</text>
          </view>
        </view>
      </view>

      <view class="tool-grid">
        <button v-for="item in tools" :key="item.title" class="tool-card shell-card" @click="openTool(item)">
          <view class="tool-icon">
            <image class="tool-icon__img" :src="item.icon" mode="aspectFit" />
          </view>
          <view class="tool-main">
            <text class="tool-title">{{ item.title }}</text>
            <text class="tool-desc">{{ item.desc }}</text>
          </view>
        </button>
      </view>

      <view class="shell-card panel">
        <view class="section-title">
          <view>
            <text class="section-title__main">报名限制检查</text>
            <text class="section-title__sub">参加活动前可快速确认当前状态</text>
          </view>
          <button class="btn panel-action" @click="checkRestriction">检查</button>
        </view>
        <text class="status-text">{{ restrictionText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { attendanceApi } from '@/api'

const restrictionText = ref('暂未检查')

const toolIconColor = 'a51f2b'
const navIcon = name => `https://api.iconify.design/lucide:${name}.svg?color=%23${toolIconColor}`

const tools = [
  { title: '补签申请', desc: '提交原因和凭证，跟进审核状态', icon: navIcon('file-pen-line'), url: '/pages/tools/supplementary' },
  { title: '个人报表', desc: '查看时长、积分、摘要并导出', icon: navIcon('file-chart-column'), url: '/pages/report/index' },
  { title: '数据统计', desc: '查看趋势、分类和签到率', icon: navIcon('chart-column'), url: '/pages/statistics/index' },
  { title: '服务咨询', desc: '咨询报名、补签和平台规则', icon: navIcon('message-circle-question'), url: '/pages/ai/chat' },
  { title: '我的活动', desc: '查看报名和参与记录', icon: navIcon('calendar-check'), url: '/pages/my-activities/index' },
  { title: '待办清单', desc: '处理志愿任务和提醒', icon: navIcon('list-checks'), url: '/pages/todo/index' }
]

function openTool(item) {
  uni.navigateTo({ url: item.url })
}

async function checkRestriction() {
  try {
    const result = await attendanceApi.checkRestriction()
    restrictionText.value = result?.restricted ? '当前存在报名限制' : '当前无报名限制'
  } catch (error) {
    restrictionText.value = '检查失败，请稍后再试'
  }
}
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-paper: #fffafa;
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.tools-page {
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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
  display: flex;
  min-width: 0;
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
.tool-title,
.tool-desc,
.status-text {
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

.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(156px, 1fr));
  gap: 10px 12px;
  margin-top: 16px;
}

.tool-card {
  position: relative;
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 76px;
  align-items: center;
  gap: 12px;
  overflow: hidden;
  padding: 12px;
  border-color: rgba(165, 31, 43, 0.1);
  background: rgba(255, 255, 255, 0.96);
  text-align: left;
  box-shadow: 0 8px 18px rgba(16, 35, 31, 0.06);
}

.tool-card::after {
  content: '';
  width: 18px;
  height: 2px;
  flex: 0 0 auto;
  margin-left: auto;
  border-radius: 2px;
  background: rgba(224, 139, 115, 0.72);
}

.tool-card:active {
  border-color: rgba(165, 31, 43, 0.32);
  background: #fff7f5;
}

.tool-icon {
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

.tool-icon__img {
  display: block;
  width: 18px;
  height: 18px;
}

.tool-main {
  min-width: 0;
  flex: 1;
}

.tool-title {
  overflow: hidden;
  color: $client-ink;
  font-size: 15px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-desc {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 5px;
  color: #72827d;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.panel {
  min-width: 0;
  margin-top: 14px;
  padding: 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.92);
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

.panel-action {
  border-color: $client-red;
  background: $client-red;
  color: #fff;
}

.status-text {
  color: $client-muted;
  font-weight: 700;
}

@media (max-width: 430px) {
  .tools-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .page-head {
    padding: 16px 14px;
  }

  .page-title {
    font-size: 24px;
  }

  .tool-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
