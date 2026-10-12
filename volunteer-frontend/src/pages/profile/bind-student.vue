<template>
  <view class="page bind-page safe-bottom">
    <view class="page-shell">
      <view class="identity-head shell-card">
        <view class="identity-mark">ID</view>
        <view class="identity-copy">
          <text class="head-kicker">账号身份</text>
          <text class="head-title">绑定学号</text>
          <text class="head-sub">{{ currentStudentNo ? `当前学号 ${currentStudentNo}` : '当前账号暂未绑定学号' }}</text>
        </view>
      </view>

      <view class="shell-card form-card">
        <view class="section-title">
          <view>
            <text class="section-title__main">学号信息</text>
            <text class="section-title__sub">6-20 位字母或数字</text>
          </view>
          <text class="status-pill" :class="{ complete: currentStudentNo }">{{ currentStudentNo ? '已绑定' : '未绑定' }}</text>
        </view>

        <label class="field">
          <text class="field__label">学号</text>
          <input
            class="input"
            v-model.trim="form.studentId"
            :disabled="Boolean(currentStudentNo)"
            maxlength="20"
            placeholder="请输入学号"
            @input="clearAvailability"
          />
        </label>

        <label class="field">
          <text class="field__label">登录密码</text>
          <input
            class="input"
            v-model="form.password"
            :disabled="Boolean(currentStudentNo)"
            password
            maxlength="64"
            placeholder="请输入当前账号密码"
            @input="error = ''"
          />
        </label>

        <view v-if="availabilityText" class="result-line" :class="availabilityClass">{{ availabilityText }}</view>
        <text v-if="error" class="error-line">{{ error }}</text>

        <view class="action-row">
          <button class="btn" :disabled="checking || Boolean(currentStudentNo)" @click="checkStudentId">
            {{ checking ? '检查中...' : '检查占用' }}
          </button>
          <button class="btn btn--primary" :disabled="submitDisabled" @click="submit">
            {{ submitting ? '绑定中...' : '确认绑定' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const form = reactive({
  studentId: '',
  password: ''
})
const checking = ref(false)
const submitting = ref(false)
const availability = ref('')
const error = ref('')

const studentIdPattern = /^[a-zA-Z0-9]{6,20}$/
const currentStudentNo = computed(() => userStore.userInfo.studentNo || '')
const normalizedStudentId = computed(() => form.studentId.trim())
const normalizedPassword = computed(() => form.password.trim())
const availabilityText = computed(() => {
  if (currentStudentNo.value) return ''
  if (availability.value === 'available') return '该学号可绑定'
  if (availability.value === 'bound') return '该学号已被其他账号绑定'
  return ''
})
const availabilityClass = computed(() => availability.value === 'available' ? 'success' : 'danger')
const submitDisabled = computed(() => (
  submitting.value ||
  Boolean(currentStudentNo.value) ||
  !studentIdPattern.test(normalizedStudentId.value) ||
  !normalizedPassword.value ||
  availability.value === 'bound'
))

function validate() {
  if (!normalizedStudentId.value) return '请输入学号'
  if (!studentIdPattern.test(normalizedStudentId.value)) return '学号应为 6-20 位字母或数字'
  if (!normalizedPassword.value) return '请输入当前账号密码'
  return ''
}

function clearAvailability() {
  availability.value = ''
  error.value = ''
}

async function checkStudentId() {
  error.value = validate()
  if (error.value) return

  checking.value = true
  try {
    const result = await authApi.checkStudentId({ studentId: normalizedStudentId.value })
    availability.value = result?.isBound ? 'bound' : 'available'
  } catch (err) {
    error.value = err?.message || '学号检查失败，请稍后再试'
  } finally {
    checking.value = false
  }
}

async function submit() {
  error.value = validate()
  if (error.value || availability.value === 'bound') return

  submitting.value = true
  try {
    const result = await authApi.bindStudentId({
      studentId: normalizedStudentId.value,
      password: normalizedPassword.value
    })
    const studentId = result?.studentId || normalizedStudentId.value
    userStore.updateUser({
      studentNo: studentId,
      accessLevel: result?.accessLevel || 'full'
    })
    form.studentId = studentId
    form.password = ''
    uni.showToast({ title: '绑定成功', icon: 'success' })
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.redirectTo({ url: '/pages/mine/index' })
      }
    }, 600)
  } catch (err) {
    error.value = err?.message || '绑定失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

onShow(() => {
  userStore.loadFromStorage()
  if (currentStudentNo.value) {
    form.studentId = currentStudentNo.value
    availability.value = 'available'
  }
})
</script>

<style lang="scss" scoped>
$profile-ink: #2a1214;
$profile-muted: #667772;
$profile-red: #a51f2b;
$profile-red-soft: #fff0ee;
$profile-accent: #e08b73;

.bind-page {
  min-height: 100vh;
  padding: 14px 14px 92px;
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $profile-ink;
}

.page-shell {
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
}

.identity-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  overflow: hidden;
  padding: 18px 16px;
  border-color: rgba(255, 214, 204, 0.42);
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.18), transparent 190px),
    linear-gradient(135deg, #ad3440 0%, #cf5c68 58%, #982934 100%);
  color: #fff1ef;
  box-shadow: 0 16px 30px rgba(165, 31, 43, 0.14);
}

.identity-head::after {
  content: '';
  position: absolute;
  right: 16px;
  bottom: 12px;
  width: 46px;
  height: 4px;
  border-radius: 4px;
  background: $profile-accent;
  opacity: 0.78;
}

.identity-mark {
  position: relative;
  z-index: 1;
  display: flex;
  width: 52px;
  height: 52px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: $profile-ink;
  font-size: 19px;
  font-weight: 900;
}

.identity-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.head-kicker,
.head-title,
.head-sub {
  display: block;
}

.head-kicker {
  color: rgba(255, 248, 246, 0.78);
  font-size: 12px;
  font-weight: 800;
}

.head-title {
  margin-top: 2px;
  color: #fff;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.2;
}

.head-sub {
  margin-top: 8px;
  color: rgba(255, 248, 246, 0.9);
  font-size: 13px;
  font-weight: 700;
}

.form-card {
  margin-top: 14px;
  padding: 18px 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.status-pill {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef1f0;
  color: $profile-muted;
  font-size: 12px;
  font-weight: 900;
}

.status-pill.complete {
  background: $profile-red-soft;
  color: $profile-red;
}

.bind-page .input {
  border-color: rgba(16, 35, 31, 0.12);
  background: #fffafa;
}

.result-line {
  margin-top: 12px;
  font-size: 13px;
  font-weight: 800;
}

.result-line.success {
  color: #1f7a4d;
}

.result-line.danger {
  color: $profile-red;
}

.action-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 10px;
  margin-top: 18px;
}

.action-row .btn {
  width: 100%;
}

.bind-page .btn--primary {
  border-color: $profile-red;
  background: $profile-red;
  color: #fff;
  box-shadow: 0 8px 18px rgba(165, 31, 43, 0.18);
}

@media (max-width: 430px) {
  .bind-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .action-row {
    grid-template-columns: 1fr;
  }
}
</style>
