<template>
  <view class="page todo-page safe-bottom">
    <view class="page-shell">
    <view class="page-head">
      <view class="page-brand">
        <text class="page-mark">办</text>
        <view class="page-brand__text">
          <text class="page-kicker">志愿服务平台</text>
          <text class="page-title">待办清单</text>
          <text class="page-subtitle">{{ pendingCount }} 项待处理 · {{ completedCount }} 项已完成</text>
        </view>
      </view>
      <button class="btn page-action" :disabled="!completedCount" @click="clearDone">清理</button>
    </view>

    <view class="composer shell-card">
      <input
        class="input"
        v-model="newTitle"
        confirm-type="done"
        maxlength="200"
        placeholder="添加一个志愿任务"
        @confirm="createTodo"
      />
      <button class="btn btn--primary" :disabled="submitting || !newTitle.trim()" @click="createTodo">添加</button>
    </view>

    <view class="tabs">
      <button
        v-for="item in tabs"
        :key="item.value"
        class="tab"
        :class="{ 'tab--active': filter === item.value }"
        @click="filter = item.value"
      >
        {{ item.label }}
      </button>
    </view>

    <view class="todo-list">
      <view v-for="item in visibleTodos" :key="item.id" class="todo-card shell-card">
        <button class="check" :class="{ 'check--done': item.completed }" @click="toggle(item)">
          {{ item.completed ? '✓' : '' }}
        </button>
        <view class="todo-main">
          <input
            v-if="editingId === item.id"
            class="input edit-input"
            v-model="editingTitle"
            maxlength="200"
            focus
            @confirm="saveEdit(item)"
          />
          <template v-else>
            <text class="todo-title" :class="{ 'todo-title--done': item.completed }">{{ item.title }}</text>
            <text class="todo-meta">{{ todoMeta(item) }}</text>
          </template>
        </view>
        <view class="actions">
          <button v-if="editingId === item.id" class="btn btn--primary" @click="saveEdit(item)">保存</button>
          <button v-else class="btn" @click="startEdit(item)">编辑</button>
          <button class="btn btn--danger" @click="remove(item)">删除</button>
        </view>
      </view>
    </view>

    <view v-if="!visibleTodos.length && !loading" class="empty-state">
      <text class="empty-state__title">暂无待办</text>
      <text>报名提醒、补签申请和临时任务会在这里集中处理。</text>
    </view>
    <view v-if="loading" class="loading">加载中...</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { useTodoStore } from '@/stores/todo'

const todoStore = useTodoStore()
const newTitle = ref('')
const filter = ref('pending')
const loading = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const editingTitle = ref('')

const tabs = [
  { label: '待处理', value: 'pending' },
  { label: '已完成', value: 'completed' },
  { label: '全部', value: 'all' }
]

const pendingCount = computed(() => todoStore.pendingCount)
const completedCount = computed(() => todoStore.completedCount)
const visibleTodos = computed(() => {
  if (filter.value === 'pending') return todoStore.pendingTodos
  if (filter.value === 'completed') return todoStore.completedTodos
  return todoStore.allTodos
})

async function loadTodos() {
  loading.value = true
  try {
    await todoStore.loadTodos()
  } catch (error) {
    todoStore.loadCachedTodos()
  } finally {
    loading.value = false
  }
}

async function createTodo() {
  const title = newTitle.value.trim()
  if (!title || submitting.value) return
  submitting.value = true
  try {
    await todoStore.addTodo(title)
    newTitle.value = ''
    filter.value = 'pending'
  } finally {
    submitting.value = false
  }
}

function startEdit(item) {
  editingId.value = item.id
  editingTitle.value = item.title
}

async function saveEdit(item) {
  const title = editingTitle.value.trim()
  if (!title) return
  await todoStore.updateTodo(item.id, title)
  editingId.value = null
  editingTitle.value = ''
}

async function toggle(item) {
  await todoStore.toggleTodo(item.id)
}

async function remove(item) {
  await todoStore.deleteTodo(item.id)
}

async function clearDone() {
  if (!completedCount.value) return
  await todoStore.clearCompleted()
}

function todoMeta(item) {
  const status = item.completed ? '已完成' : '待处理'
  const time = item.updatedAt || item.createdAt
  return time ? `${status} · ${String(time).replace('T', ' ').slice(0, 16)}` : status
}

onShow(loadTodos)

onPullDownRefresh(async () => {
  try {
    await loadTodos()
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

.todo-page {
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

.page-action[disabled] {
  border-color: rgba(255, 214, 204, 0.24);
  background: rgba(255, 246, 244, 0.08);
  color: rgba(255, 248, 246, 0.58);
}

.page-title,
.page-kicker,
.page-subtitle,
.todo-title,
.todo-meta {
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

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px;
  gap: 10px;
  margin-top: 16px;
  padding: 12px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.composer .input {
  border-color: rgba(16, 35, 31, 0.12);
  background: $client-paper;
}

.composer .btn--primary {
  border-color: $client-red;
  background: $client-red;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin: 14px 0;
}

.tab {
  min-height: 36px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-sm;
  background: rgba(255, 255, 255, 0.94);
  color: #7d4046;
  font-weight: 800;
  box-shadow: 0 6px 14px rgba(16, 35, 31, 0.04);
}

.tab--active {
  border-color: rgba(165, 31, 43, 0.24);
  background: $client-red-soft;
  color: $client-red;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-card {
  position: relative;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.07);
}

.check {
  display: flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(165, 31, 43, 0.22);
  border-radius: $radius-sm;
  color: #fff;
  font-weight: 800;
}

.check--done {
  border-color: $client-red;
  background: $client-red;
}

.todo-main {
  min-width: 0;
}

.todo-title {
  overflow: hidden;
  color: $client-ink;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-title--done {
  color: $client-muted;
  text-decoration: line-through;
}

.todo-meta {
  margin-top: 4px;
  color: $client-muted;
  font-size: 12px;
  font-weight: 700;
}

.edit-input {
  min-height: 34px;
}

.actions {
  display: flex;
  gap: 6px;
}

.loading {
  padding: 20px;
  color: $client-muted;
  text-align: center;
}

@media (max-width: 420px) {
  .todo-card {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .actions {
    grid-column: 2;
    justify-content: flex-end;
  }
}
</style>
