<template>
  <view class="page form-page safe-bottom">
    <view class="page-shell">
      <view class="page-head">
        <view class="page-brand">
          <text class="page-mark">补</text>
          <view class="page-brand__text">
            <text class="page-kicker">志愿服务平台</text>
            <text class="page-title">补签申请</text>
            <text class="page-subtitle">选择活动，说明原因，上传必要证明</text>
          </view>
        </view>
      </view>

      <view class="shell-card form-card">
        <view class="section-title">
          <view>
            <text class="section-title__main">补签信息</text>
            <text class="section-title__sub">按活动记录提交，便于后台审核</text>
          </view>
        </view>

        <label class="field">
          <text class="field__label">活动 ID</text>
          <input class="input" v-model="form.activityId" placeholder="输入需要补签的活动 ID" />
        </label>

        <view class="activity-options">
          <button
            v-for="item in activities"
            :key="item.id"
            class="activity-option"
            :class="{ active: form.activityId === String(item.id) }"
            @click="form.activityId = String(item.id)"
          >
            <view>
              <text class="activity-title">{{ item.title || item.name || '活动' }}</text>
              <text class="activity-meta">{{ item.location || '地点待定' }}</text>
            </view>
            <text class="activity-code">#{{ item.id }}</text>
          </button>
        </view>

        <label class="field">
          <text class="field__label">补签原因</text>
          <textarea class="textarea" v-model="form.reason" placeholder="说明未能正常签到/签退的原因" />
        </label>

        <view class="upload-box">
          <button class="btn" @click="chooseProof">选择证明图片</button>
          <text class="proof-count">{{ proofs.length }} 张</text>
        </view>

        <view v-if="analysis" class="analysis-box">
          <text class="analysis-label">规则预审结果</text>
          <text class="analysis-text" selectable>{{ analysis }}</text>
        </view>

        <view class="action-row">
          <button class="btn" @click="analyze">规则预审</button>
          <button class="btn btn--primary" @click="submit">提交申请</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { activityApi, attendanceApi } from '@/api'

const activities = ref([])
const proofs = ref([])
const analysis = ref('')

const form = reactive({
  activityId: '',
  reason: ''
})

function extractList(result) {
  if (Array.isArray(result)) return result
  return result?.list || result?.records || []
}

async function loadActivities() {
  const result = await activityApi.getUserRegistrations({ pageNum: 1, pageSize: 20 }, { showLoading: false })
  activities.value = extractList(result)
}

function chooseProof() {
  uni.chooseImage({
    count: 5,
    success: (res) => {
      proofs.value = res.tempFilePaths || []
    }
  })
}

async function analyze() {
  if (!form.reason) {
    uni.showToast({ title: '请先填写原因', icon: 'none' })
    return
  }
  const result = await attendanceApi.analyzeSupplementary({
    activityId: form.activityId,
    reason: form.reason
  })
  analysis.value = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
}

async function submit() {
  if (!form.activityId || !form.reason) {
    uni.showToast({ title: '请填写活动和原因', icon: 'none' })
    return
  }
  await attendanceApi.applySupplementary({
    activityId: form.activityId,
    reason: form.reason,
    proof: proofs.value.join(',')
  })
  uni.showToast({ title: '申请已提交', icon: 'success' })
  form.activityId = ''
  form.reason = ''
  proofs.value = []
  analysis.value = ''
}

loadActivities()
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-paper: #fffafa;
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.form-page {
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

.page-title,
.page-kicker,
.page-subtitle,
.section-title__main,
.section-title__sub,
.activity-title,
.activity-meta,
.activity-code,
.analysis-label,
.analysis-text,
.analysis-box {
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

.form-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 14px;
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

.form-card .input,
.form-card .textarea {
  border-color: rgba(16, 35, 31, 0.12);
  background: $client-paper;
}

.activity-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.activity-option {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  min-height: 58px;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border: 1px solid rgba(16, 35, 31, 0.1);
  border-radius: $radius-md;
  background: rgba(255, 250, 250, 0.86);
  text-align: left;
}

.activity-option.active {
  border-color: rgba(165, 31, 43, 0.32);
  background: $client-red-soft;
}

.activity-title {
  overflow: hidden;
  color: $client-ink;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-meta {
  margin-top: 4px;
  color: $client-muted;
  font-size: 12px;
  font-weight: 700;
}

.activity-code {
  flex: 0 0 auto;
  color: $client-red;
  font-size: 12px;
  font-weight: 900;
}

.upload-box,
.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.upload-box .btn {
  margin-right: auto;
}

.proof-count {
  color: $client-muted;
  font-weight: 800;
}

.analysis-box {
  max-height: 180px;
  overflow: auto;
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background: rgba(255, 240, 238, 0.62);
  color: $client-muted;
  white-space: pre-wrap;
}

.analysis-label {
  color: $client-ink;
  font-size: 13px;
  font-weight: 900;
}

.analysis-text {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.7;
}

.action-row .btn {
  min-width: 112px;
}

.action-row .btn--primary {
  border-color: $client-red;
  background: $client-red;
}

@media (max-width: 430px) {
  .form-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .page-head {
    padding: 16px 14px;
  }

  .page-title {
    font-size: 24px;
  }

  .activity-option,
  .action-row {
    grid-template-columns: 1fr;
  }

  .action-row {
    display: grid;
  }

  .action-row .btn {
    width: 100%;
  }
}
</style>
