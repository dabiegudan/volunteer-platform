<template>
  <view class="page profile-page safe-bottom">
    <view class="page-shell">
      <view class="profile-head shell-card">
        <view class="profile-head-main">
          <view class="profile-mark">档</view>
          <view class="profile-head-copy">
            <text class="head-kicker">个人档案</text>
            <text class="head-title">完善资料</text>
            <text class="head-sub">{{ requiredComplete ? '必要信息已完整' : '请补齐必要信息' }}</text>
          </view>
        </view>
        <text class="status-pill" :class="{ complete: requiredComplete }">{{ requiredComplete ? '完整' : '待完善' }}</text>
      </view>

      <view class="form-layout">
        <view class="shell-card form-card">
          <view class="section-title">
            <view>
              <text class="section-title__main">基础信息</text>
              <text class="section-title__sub">姓名、班级和服务偏好</text>
            </view>
          </view>

          <view class="field-stack">
            <label class="field">
              <text class="field__label">姓名</text>
              <input class="input" v-model.trim="form.realName" maxlength="30" placeholder="请输入姓名" />
            </label>

            <label class="field">
              <text class="field__label">性别</text>
              <picker mode="selector" :range="genderOptions" range-key="label" :value="genderIndex" @change="selectGender">
                <view class="select-like">{{ genderLabel || '请选择性别' }}</view>
              </picker>
            </label>

            <label class="field">
              <text class="field__label">学院</text>
              <view class="select-like readonly">{{ form.collegeName || '未绑定学院' }}</view>
            </label>

            <view class="two-col">
              <label class="field">
                <text class="field__label">专业</text>
                <input class="input" v-model.trim="form.major" maxlength="40" placeholder="如计算机科学" />
              </label>

              <label class="field">
                <text class="field__label">年级</text>
                <input class="input" v-model.trim="form.grade" maxlength="20" placeholder="如2024级" />
              </label>
            </view>

            <label class="field">
              <text class="field__label">班级</text>
              <input class="input" v-model.trim="form.className" maxlength="40" placeholder="如计科 2401 班" />
            </label>

            <label class="field">
              <text class="field__label">政治面貌</text>
              <picker mode="selector" :range="politicalOptions" :value="politicalIndex" @change="selectPoliticalStatus">
                <view class="select-like">{{ form.politicalStatus || '请选择' }}</view>
              </picker>
            </label>

            <label class="field">
              <text class="field__label">宿舍片区</text>
              <input class="input" v-model.trim="form.dormArea" maxlength="60" placeholder="如兰园 3 栋" />
            </label>
          </view>

          <view class="tag-field">
            <text class="field__label">服务偏好</text>
            <view class="tag-list">
              <button
                v-for="tag in serviceTags"
                :key="tag"
                class="tag-option"
                :class="{ active: selectedTags.includes(tag) }"
                @click="toggleTag(tag)"
              >
                {{ tag }}
              </button>
            </view>
          </view>
        </view>

        <view class="shell-card form-card required-card">
          <view class="section-title">
            <view>
              <text class="section-title__main">必要信息</text>
              <text class="section-title__sub">报名、通知和应急联系使用</text>
            </view>
          </view>

          <view class="field-stack">
            <label class="field">
              <text class="field__label">学号</text>
              <view class="student-row">
                <view class="select-like readonly">{{ currentStudentNo || '未绑定学号' }}</view>
                <button v-if="!currentStudentNo" class="btn bind-btn" @click="goBindStudent">绑定</button>
              </view>
            </label>

            <label class="field">
              <text class="field__label">联系方式</text>
              <input class="input" v-model.trim="form.phone" maxlength="20" placeholder="请输入手机号" />
            </label>

            <label class="field">
              <text class="field__label">出生日期</text>
              <picker mode="date" :value="form.birthDate" :end="today" @change="selectBirthDate">
                <view class="select-like">{{ form.birthDate || '请选择日期' }}</view>
              </picker>
            </label>

            <label class="field">
              <text class="field__label">紧急联系人</text>
              <input class="input" v-model.trim="form.emergencyContact" maxlength="30" placeholder="请输入姓名" />
            </label>

            <label class="field">
              <text class="field__label">紧急联系电话</text>
              <input class="input" v-model.trim="form.emergencyPhone" maxlength="20" placeholder="请输入联系电话" />
            </label>
          </view>

          <view class="required-status">
            <text class="required-status__count">{{ requiredCompletedCount }}/{{ requiredInfoItems.length }}</text>
            <text class="required-status__text">{{ requiredComplete ? '必要信息已完成' : '仍有必要信息未填写' }}</text>
          </view>
        </view>
      </view>

      <text v-if="error" class="error-line">{{ error }}</text>

      <button class="btn btn--primary btn--block submit-btn" :disabled="submitting || loading" @click="submit">
        {{ submitting ? '保存中...' : '保存资料' }}
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { profileApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { sanitizeInput } from '@/utils/validate'

const userStore = useUserStore()
const genderOptions = [
  { label: '男', value: 'M' },
  { label: '女', value: 'F' }
]
const politicalOptions = ['群众', '共青团员', '中共党员', '中共预备党员', '其他']
const serviceTags = ['校园服务', '社区服务', '应急保障', '文化活动', '环保公益', '助老助残']

const form = reactive({
  realName: '',
  gender: '',
  collegeName: '',
  major: '',
  grade: '',
  className: '',
  politicalStatus: '',
  dormArea: '',
  phone: '',
  birthDate: '',
  emergencyContact: '',
  emergencyPhone: ''
})
const selectedTags = ref([])
const profileComplete = ref(false)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const currentStudentNo = ref('')
const today = new Date().toISOString().slice(0, 10)

const genderIndex = computed(() => {
  const index = genderOptions.findIndex(item => item.value === form.gender)
  return index >= 0 ? index : 0
})
const genderLabel = computed(() => genderOptions.find(item => item.value === form.gender)?.label || '')
const politicalIndex = computed(() => {
  const index = politicalOptions.findIndex(item => item === form.politicalStatus)
  return index >= 0 ? index : 0
})
const requiredInfoItems = computed(() => [
  currentStudentNo.value,
  form.phone,
  form.birthDate,
  form.emergencyContact,
  form.emergencyPhone
])
const requiredCompletedCount = computed(() => requiredInfoItems.value.filter(Boolean).length)
const requiredComplete = computed(() => requiredCompletedCount.value === requiredInfoItems.value.length)

function selectGender(event) {
  const index = Number(event.detail.value)
  form.gender = genderOptions[index]?.value || ''
}

function selectBirthDate(event) {
  form.birthDate = event.detail.value
}

function selectPoliticalStatus(event) {
  form.politicalStatus = politicalOptions[Number(event.detail.value)] || ''
}

function parseTags(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return String(value)
      .split(',')
      .map(item => item.trim())
      .filter(Boolean)
  }
}

function normalizeDate(value) {
  return value ? String(value).slice(0, 10) : ''
}

function toggleTag(tag) {
  selectedTags.value = selectedTags.value.includes(tag)
    ? selectedTags.value.filter(item => item !== tag)
    : [...selectedTags.value, tag]
}

function goBindStudent() {
  uni.navigateTo({ url: '/pages/profile/bind-student' })
}

function validate() {
  if (!sanitizeInput(form.realName, 30)) return '请填写姓名'
  if (!sanitizeInput(form.phone, 20)) return '请填写联系方式'
  if (!/^[0-9+\-\s]{6,20}$/.test(form.phone)) return '联系方式格式不正确'
  if (!form.birthDate) return '请选择出生日期'
  if (!sanitizeInput(form.emergencyContact, 30)) return '请填写紧急联系人'
  if (!sanitizeInput(form.emergencyPhone, 20)) return '请填写紧急联系电话'
  if (!/^[0-9+\-\s]{6,20}$/.test(form.emergencyPhone)) return '紧急联系电话格式不正确'
  return ''
}

async function loadProfile() {
  userStore.loadFromStorage()
  loading.value = true
  error.value = ''
  try {
    const profile = await profileApi.getProfileStatus()
    profileComplete.value = Boolean(profile?.profileComplete)
    currentStudentNo.value = profile?.studentNo || profile?.studentId || userStore.userInfo.studentNo || ''
    form.realName = profile?.realName || userStore.userInfo.realName || ''
    form.gender = profile?.gender || ''
    form.collegeName = profile?.collegeName || userStore.userInfo.college || ''
    form.major = profile?.major || userStore.userInfo.major || ''
    form.grade = profile?.grade || userStore.userInfo.grade || ''
    form.className = profile?.className || userStore.userInfo.className || ''
    form.politicalStatus = profile?.politicalStatus || ''
    form.dormArea = profile?.dormArea || ''
    form.phone = profile?.phone || userStore.userInfo.phone || ''
    form.birthDate = normalizeDate(profile?.birthDate)
    form.emergencyContact = profile?.emergencyContact || ''
    form.emergencyPhone = profile?.emergencyPhone || ''
    selectedTags.value = parseTags(profile?.serviceCategoryTags)
  } catch (err) {
    error.value = err?.message || '资料加载失败'
  } finally {
    loading.value = false
  }
}

async function submit() {
  error.value = validate()
  if (error.value) return

  submitting.value = true
  try {
    const result = await profileApi.updateProfile({
      realName: sanitizeInput(form.realName, 30),
      gender: form.gender,
      major: sanitizeInput(form.major, 40),
      grade: sanitizeInput(form.grade, 20),
      className: sanitizeInput(form.className, 40),
      politicalStatus: sanitizeInput(form.politicalStatus, 20),
      dormArea: sanitizeInput(form.dormArea, 60),
      phone: sanitizeInput(form.phone, 20),
      birthDate: form.birthDate,
      emergencyContact: sanitizeInput(form.emergencyContact, 30),
      emergencyPhone: sanitizeInput(form.emergencyPhone, 20),
      serviceCategoryTags: JSON.stringify(selectedTags.value)
    })
    profileComplete.value = Boolean(result?.profileComplete)
    userStore.updateUser({
      realName: form.realName,
      phone: form.phone,
      major: form.major,
      className: form.className,
      grade: form.grade
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.redirectTo({ url: '/pages/mine/index' })
      }
    }, 600)
  } catch (err) {
    error.value = err?.message || '保存失败，请稍后再试'
  } finally {
    submitting.value = false
  }
}

onShow(loadProfile)
</script>

<style lang="scss" scoped>
$profile-ink: #2a1214;
$profile-muted: #667772;
$profile-red: #a51f2b;
$profile-red-soft: #fff0ee;
$profile-accent: #e08b73;

.profile-page {
  min-height: 100vh;
  padding: 14px 14px 92px;
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $profile-ink;
}

.page-shell {
  width: 100%;
  max-width: 900px;
  margin: 0 auto;
}

.profile-head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
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

.profile-head::after {
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

.profile-head-main {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

.profile-mark {
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
  font-size: 22px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(91, 17, 25, 0.18);
}

.profile-head-copy {
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

.status-pill {
  position: relative;
  z-index: 1;
  display: inline-flex;
  min-height: 28px;
  flex: 0 0 auto;
  align-items: center;
  padding: 0 10px;
  border: 1px solid rgba(255, 214, 204, 0.44);
  border-radius: 999px;
  background: rgba(255, 246, 244, 0.16);
  color: #fff1ef;
  font-size: 12px;
  font-weight: 900;
}

.status-pill.complete {
  background: #fff4f1;
  color: $profile-red;
}

.form-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: 12px;
  margin-top: 14px;
}

.form-card {
  min-width: 0;
  padding: 18px 16px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.required-card {
  align-self: start;
}

.field-stack {
  display: grid;
  gap: 14px;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.profile-page .input,
.profile-page .select-like {
  display: flex;
  min-height: 40px;
  align-items: center;
  border-color: rgba(16, 35, 31, 0.12);
  background: #fffafa;
}

.select-like.readonly {
  color: $profile-muted;
}

.student-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 70px;
  gap: 8px;
}

.bind-btn {
  min-height: 40px;
  border-color: rgba(165, 31, 43, 0.18);
  background: $profile-red-soft;
  color: $profile-red;
}

.tag-field {
  margin-top: 16px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.tag-option {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(165, 31, 43, 0.14);
  border-radius: $radius-sm;
  background: $profile-red-soft;
  color: #7d4046;
  font-size: 13px;
  font-weight: 800;
}

.tag-option.active {
  border-color: $profile-red;
  background: $profile-red;
  color: #fff;
}

.required-status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-sm;
  background: $profile-red-soft;
}

.required-status__count {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  padding: 0 9px;
  border-radius: 999px;
  background: #fff;
  color: $profile-red;
  font-size: 12px;
  font-weight: 900;
}

.required-status__text {
  color: $profile-muted;
  font-size: 13px;
  font-weight: 800;
}

.submit-btn {
  margin-top: 14px;
  border-color: $profile-red;
  background: $profile-red;
  color: #fff;
  box-shadow: 0 8px 18px rgba(165, 31, 43, 0.18);
}

@media (max-width: 720px) {
  .profile-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .form-layout,
  .two-col {
    grid-template-columns: 1fr;
  }

  .profile-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .status-pill {
    align-self: flex-start;
  }
}
</style>
