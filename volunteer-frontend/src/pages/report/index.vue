<template>
  <view class="page report-page safe-bottom">
    <view class="page-shell">
    <view class="page-head">
      <view class="page-brand">
        <text class="page-mark">报</text>
        <view class="page-brand__text">
          <text class="page-kicker">志愿服务平台</text>
          <text class="page-title">个人报表</text>
          <text class="page-subtitle">服务时长、摘要、证书与导出</text>
        </view>
      </view>
      <button class="btn page-action" @click="loadData">刷新</button>
    </view>

    <view class="metric-grid">
      <view class="metric-card shell-card">
        <text class="metric-label">累计时长</text>
        <text class="metric-value">{{ duration.totalDuration || duration.duration || 0 }}</text>
        <text class="metric-note">小时</text>
      </view>
      <view class="metric-card shell-card">
        <text class="metric-label">活动次数</text>
        <text class="metric-value">{{ duration.activityCount || 0 }}</text>
        <text class="metric-note">次</text>
      </view>
      <view class="metric-card shell-card">
        <text class="metric-label">积分</text>
        <text class="metric-value">{{ credit.creditScore || credit.totalCredit || 0 }}</text>
        <text class="metric-note">成长信用</text>
      </view>
    </view>

    <view class="shell-card panel">
      <view class="section-title">
        <view>
          <text class="section-title__main">报表摘要</text>
          <text class="section-title__sub">服务记录汇总</text>
        </view>
      </view>
      <text class="summary">{{ summary || '暂无摘要，刷新后会尝试生成。' }}</text>
    </view>

    <view class="shell-card panel">
      <view class="section-title">
        <view>
          <text class="section-title__main">数据导出</text>
          <text class="section-title__sub">下载考勤、时长和证明文件</text>
        </view>
      </view>
      <view class="export-grid">
        <button class="export-card" @click="downloadReport('/api/report/export/attendance', '考勤记录.xlsx')">
          <text class="export-title">考勤记录</text>
          <text class="export-sub">下载 Excel</text>
        </button>
        <button class="export-card" @click="downloadReport('/api/report/export/duration', '时长明细.xlsx')">
          <text class="export-title">时长明细</text>
          <text class="export-sub">下载 Excel</text>
        </button>
        <button class="export-card" @click="downloadReport('/api/report/certificate', '时长证明.pdf')">
          <text class="export-title">时长证明</text>
          <text class="export-sub">生成 PDF</text>
        </button>
      </view>
    </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { creditApi, reportApi } from '@/api'
import { downloadAuthenticated } from '@/utils/fileDownload'

const duration = ref({})
const credit = ref({})
const summary = ref('')

async function loadData() {
  const [durationResult, summaryResult, creditResult] = await Promise.allSettled([
    reportApi.getUserDurationReport(),
    reportApi.getReportSummary(),
    creditApi.getOverview()
  ])
  duration.value = durationResult.status === 'fulfilled' ? durationResult.value || {} : {}
  summary.value = summaryResult.status === 'fulfilled'
    ? (summaryResult.value?.summary || summaryResult.value?.content || String(summaryResult.value || ''))
    : ''
  credit.value = creditResult.status === 'fulfilled' ? creditResult.value || {} : {}
}

async function downloadReport(path, filename) {
  try {
    await downloadAuthenticated(path, {}, filename)
  } catch (error) {
    uni.showToast({ title: '下载失败', icon: 'none' })
  }
}

loadData()
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-paper: #fffafa;
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.report-page {
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
.metric-note,
.section-title__main,
.section-title__sub,
.summary,
.export-title,
.export-sub {
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
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin: 16px 0 14px;
}

.metric-card,
.panel {
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.metric-card {
  position: relative;
  overflow: hidden;
  min-height: 96px;
  padding: 14px;
}

.metric-card::after {
  content: '';
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 36px;
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
  font-size: 26px;
  font-weight: 900;
}

.metric-note {
  margin-top: 4px;
  color: $client-muted;
  font-size: 12px;
  font-weight: 700;
}

.panel {
  margin-top: 12px;
  padding: 16px;
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

.summary {
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.1);
  border-radius: $radius-md;
  background: rgba(255, 240, 238, 0.52);
  color: $client-muted;
  font-weight: 700;
  line-height: 1.8;
}

.export-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.export-card {
  display: flex;
  min-height: 82px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background: $client-paper;
  text-align: left;
}

.export-card:active {
  border-color: rgba(165, 31, 43, 0.32);
  background: $client-red-soft;
}

.export-title {
  color: $client-ink;
  font-weight: 900;
}

.export-sub {
  margin-top: 5px;
  color: $client-red;
  font-size: 12px;
  font-weight: 800;
}

@media (max-width: 390px) {
  .metric-grid,
  .export-grid {
    grid-template-columns: 1fr;
  }
}
</style>
