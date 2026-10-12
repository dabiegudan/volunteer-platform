<template>
  <view class="auth-page">
    <view class="auth-visual">
      <view class="auth-brand">
        <text class="auth-mark">重</text>
        <view>
          <text class="auth-name">志愿服务平台</text>
          <text class="auth-desc">高校志愿活动一体化管理</text>
        </view>
      </view>
      <view class="auth-copy">
        <text class="auth-copy__eyebrow">统一身份入口</text>
        <text class="auth-copy__title">志愿服务工作台统一入口。</text>
        <text class="auth-copy__text">活动发布、报名审核、签到考勤和数据报表在同一套流程中完成。</text>
      </view>
      <view class="auth-console">
        <view class="console-rail">
          <text>总览</text>
          <text>活动</text>
          <text>用户</text>
        </view>
        <view class="console-main">
          <view class="console-top">
            <text>管理端工作台</text>
            <text>运行中</text>
          </view>
          <view class="console-grid">
            <view class="console-card">
              <text>活动总数</text>
              <text>128</text>
            </view>
            <view class="console-card">
              <text>签到率</text>
              <text>92%</text>
            </view>
          </view>
          <view class="console-line"></view>
          <view class="console-line console-line--short"></view>
        </view>
      </view>
    </view>

    <view class="auth-panel shell-card">
      <view class="panel-head">
        <text class="panel-title">{{ mode === 'login' ? '登录平台' : '创建账号' }}</text>
        <text class="panel-subtitle">{{ mode === 'login' ? '使用统一账号进入系统' : '填写基础信息完成注册' }}</text>
      </view>

      <view class="auth-tabs">
        <button class="auth-tab" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button class="auth-tab" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </view>

      <view class="auth-form">
        <label class="field">
          <text class="field__label">账号</text>
          <input class="input" v-model="form.username" placeholder="用户名 / 手机号 / 学号" />
        </label>
        <label class="field" v-if="mode === 'register'">
          <text class="field__label">手机号</text>
          <input class="input" v-model="form.phone" placeholder="用于接收活动通知" />
        </label>
        <label class="field">
          <text class="field__label">密码</text>
          <input class="input" v-model="form.password" password placeholder="请输入密码" />
        </label>
        <label class="field" v-if="mode === 'register'">
          <text class="field__label">确认密码</text>
          <input class="input" v-model="form.confirmPassword" password placeholder="再次输入密码" />
        </label>

        <button class="btn btn--primary btn--block auth-submit" :disabled="submitting" @click="submit">
          {{ submitting ? '处理中...' : mode === 'login' ? '进入平台' : '创建账号' }}
        </button>

        <text v-if="error" class="error-line">{{ error }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { sanitizeInput } from '@/utils/validate'

const userStore = useUserStore()
const mode = ref('login')
const submitting = ref(false)
const error = ref('')

const form = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

function normalizeUser(loginData, profile = {}) {
  return {
    ...loginData,
    ...profile,
    token: loginData.token || loginData.accessToken,
    role: profile.role || loginData.role || loginData.roleCode || 'VOLUNTEER'
  }
}

function validateForm() {
  const username = sanitizeInput(form.username, 40)
  const password = sanitizeInput(form.password, 80)

  if (!username || !password) return '请填写账号和密码'
  if (mode.value === 'register') {
    if (!form.phone) return '请填写手机号'
    if (form.password !== form.confirmPassword) return '两次密码不一致'
  }
  return ''
}

async function submit() {
  error.value = validateForm()
  if (error.value) return

  submitting.value = true
  try {
    if (mode.value === 'register') {
      await authApi.register({
        username: sanitizeInput(form.username, 40),
        phone: sanitizeInput(form.phone, 20),
        password: form.password
      })
      uni.showToast({ title: '注册成功，请登录', icon: 'success' })
      mode.value = 'login'
      return
    }

    const loginData = await authApi.login({
      username: sanitizeInput(form.username, 40),
      password: form.password
    })
    let profile = {}
    try {
      uni.setStorageSync('user_token', loginData.token || loginData.accessToken)
      profile = await authApi.getUserInfo()
    } catch (profileError) {
      profile = {}
    }
    userStore.login(normalizeUser(loginData, profile))
    uni.reLaunch({ url: '/pages/index/index' })
  } catch (err) {
    error.value = err?.message || err?.msg || '操作失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
$auth-ink: #2a1214;
$auth-muted: #667772;
$auth-paper: #fffafa;
$auth-red: #a51f2b;
$auth-red-dark: #73151f;
$auth-red-soft: #fff0ee;
$auth-accent: #e08b73;

.auth-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(420px, 1fr) minmax(360px, 430px);
  gap: 28px;
  align-items: center;
  overflow-x: hidden;
  padding: 44px clamp(24px, 5vw, 72px);
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $auth-ink;
}

.auth-visual {
  position: relative;
  display: flex;
  min-height: 620px;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  padding: 24px;
  border: 1px solid rgba(255, 214, 204, 0.42);
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.18), transparent 190px),
    linear-gradient(135deg, #4f1118 0%, #851c28 55%, #2a0c11 100%);
  color: #fff1ef;
  box-shadow: 0 22px 44px rgba(91, 17, 25, 0.18);
}

.auth-brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.auth-mark {
  display: flex;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: $auth-ink;
  font-size: 22px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.2);
}

.auth-name,
.auth-desc,
.auth-copy__eyebrow,
.auth-copy__title,
.auth-copy__text,
.panel-title,
.panel-subtitle {
  display: block;
}

.auth-name {
  color: #fff;
  font-size: 22px;
  font-weight: 900;
}

.auth-desc {
  margin-top: 4px;
  color: rgba(255, 241, 239, 0.66);
  font-weight: 700;
}

.auth-copy {
  position: relative;
  z-index: 1;
  max-width: 720px;
}

.auth-copy__eyebrow {
  margin-bottom: 12px;
  color: rgba(255, 241, 239, 0.68);
  font-size: 13px;
  font-weight: 900;
}

.auth-copy__title {
  color: #fff;
  font-size: clamp(34px, 4vw, 52px);
  font-weight: 900;
  line-height: 1.25;
  overflow-wrap: break-word;
  white-space: normal;
}

.auth-copy__text {
  margin-top: 18px;
  max-width: 560px;
  color: rgba(255, 248, 246, 0.78);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.7;
  overflow-wrap: break-word;
  white-space: normal;
}

.auth-console {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  min-height: 214px;
  overflow: hidden;
  border: 1px solid rgba(255, 214, 204, 0.2);
  border-radius: $radius-md;
  background: rgba(255, 246, 244, 0.08);
}

.console-rail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-right: 1px solid rgba(255, 214, 204, 0.16);
  background: rgba(255, 246, 244, 0.06);
}

.console-rail text {
  display: flex;
  min-height: 34px;
  align-items: center;
  padding: 0 10px;
  border-radius: $radius-sm;
  background: rgba(255, 246, 244, 0.08);
  color: rgba(255, 241, 239, 0.76);
  font-size: 12px;
  font-weight: 900;
}

.console-main {
  padding: 16px;
}

.console-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  font-weight: 900;
}

.console-top text:last-child {
  min-height: 24px;
  padding: 4px 8px;
  border: 1px solid rgba(255, 214, 204, 0.28);
  border-radius: 999px;
  color: #ffd4ca;
  font-size: 12px;
}

.console-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}

.console-card {
  min-height: 74px;
  padding: 12px;
  border: 1px solid rgba(255, 214, 204, 0.16);
  border-radius: $radius-md;
  background: rgba(255, 246, 244, 0.1);
}

.console-card text {
  display: block;
  color: rgba(255, 241, 239, 0.68);
  font-size: 12px;
  font-weight: 800;
}

.console-card text:last-child {
  margin-top: 8px;
  color: #fff;
  font-size: 24px;
  font-weight: 900;
}

.console-line {
  height: 8px;
  margin-top: 16px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(255, 212, 202, 0.8), rgba(255, 212, 202, 0.12));
}

.console-line--short {
  width: 62%;
  margin-top: 10px;
  opacity: 0.72;
}

.auth-panel {
  position: relative;
  overflow: hidden;
  padding: 24px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 36px rgba(16, 35, 31, 0.1);
}

.auth-panel::before {
  content: '';
  position: absolute;
  left: 0;
  top: 22px;
  width: 3px;
  height: 32px;
  border-radius: 0 3px 3px 0;
  background: $auth-accent;
}

.panel-head {
  margin-bottom: 18px;
}

.panel-title {
  color: $auth-ink;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.2;
}

.panel-subtitle {
  margin-top: 6px;
  color: $auth-muted;
  font-size: 13px;
  font-weight: 700;
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  margin-bottom: 24px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background: $auth-red-soft;
  padding: 4px;
}

.auth-tab {
  height: 38px;
  border-radius: $radius-sm;
  color: #7d4046;
  font-weight: 900;
}

.auth-tab.active {
  background: #fff;
  color: $auth-red;
  box-shadow: 0 6px 14px rgba(16, 35, 31, 0.06);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.auth-submit {
  margin-top: 4px;
  border-color: $auth-red;
  background: $auth-red;
  color: #fff;
  box-shadow: 0 8px 18px rgba(165, 31, 43, 0.2);
}

.auth-page .input {
  border-color: rgba(16, 35, 31, 0.12);
  background: $auth-paper;
}

.auth-page .input:focus {
  border-color: rgba(165, 31, 43, 0.42);
  background: #fff;
}

@media (max-width: 900px) {
  .auth-page {
    display: flex;
    min-height: 100vh;
    width: 100%;
    max-width: 100vw;
    flex-direction: column;
    justify-content: flex-start;
    gap: 14px;
    box-sizing: border-box;
    padding: 14px 12px 28px;
  }

  .auth-visual {
    min-height: auto;
    width: calc(100vw - 24px);
    max-width: 100%;
    align-self: stretch;
    padding: 16px 14px;
  }

  .auth-copy {
    width: 100%;
    margin-top: 28px;
  }

  .auth-copy__title {
    font-size: 24px;
  }

  .auth-copy__text {
    max-width: 100%;
    font-size: 14px;
    word-break: break-all;
  }

  .auth-panel {
    width: calc(100vw - 24px);
    max-width: 100%;
    align-self: stretch;
    padding: 20px;
  }

  .auth-console {
    display: none;
  }
}
</style>
