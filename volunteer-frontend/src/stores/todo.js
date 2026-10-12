import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { todoApi } from '@/api/todo'

export const useTodoStore = defineStore('todo', () => {
  const todos = ref([])

  const allTodos = computed(() => todos.value)
  const pendingTodos = computed(() => todos.value.filter(t => !t.completed))
  const completedTodos = computed(() => todos.value.filter(t => t.completed))
  const pendingCount = computed(() => pendingTodos.value.length)
  const completedCount = computed(() => completedTodos.value.length)

  function normalizeTodo(todo) {
    if (!todo) return null

    return {
      ...todo,
      id: todo.id,
      title: todo.title || '',
      completed: todo.completed === true || todo.completed === 1 || todo.completed === '1' || todo.completed === 'true',
      createdAt: todo.createdAt || todo.createTime || '',
      updatedAt: todo.updatedAt || todo.updateTime || todo.createTime || ''
    }
  }

  function replaceTodo(updatedTodo) {
    const normalized = normalizeTodo(updatedTodo)
    if (!normalized || normalized.id == null) return null

    const index = todos.value.findIndex(t => String(t.id) === String(normalized.id))
    if (index >= 0) {
      todos.value.splice(index, 1, normalized)
    } else {
      todos.value.unshift(normalized)
    }
    saveTodos()
    return normalized
  }

  function setTodos(list) {
    todos.value = (Array.isArray(list) ? list : [])
      .map(normalizeTodo)
      .filter(Boolean)
    saveTodos()
  }

  async function addTodo(title) {
    const normalizedTitle = title.trim()
    const created = await todoApi.createTodo({ title: normalizedTitle })
    return replaceTodo(created)
  }

  async function toggleTodo(id) {
    const current = todos.value.find(t => String(t.id) === String(id))
    const completed = current ? !current.completed : undefined
    const updated = await todoApi.toggleTodo(id, completed)
    return replaceTodo(updated)
  }

  async function updateTodo(id, newTitle) {
    const updated = await todoApi.updateTodo(id, { title: newTitle.trim() })
    return replaceTodo(updated)
  }

  async function deleteTodo(id) {
    await todoApi.deleteTodo(id)
    todos.value = todos.value.filter(t => String(t.id) !== String(id))
    saveTodos()
  }

  async function clearCompleted() {
    await todoApi.clearCompleted()
    todos.value = todos.value.filter(t => !t.completed)
    saveTodos()
  }

  function saveTodos() {
    try {
      uni.setStorageSync('volunteer_todos', todos.value)
    } catch (e) {
      console.error('Failed to cache todos', e)
    }
  }

  async function loadTodos(status) {
    const list = await todoApi.getTodoList(status ? { status } : undefined)
    setTodos(list)
    return todos.value
  }

  function loadCachedTodos() {
    try {
      const saved = uni.getStorageSync('volunteer_todos')
      if (Array.isArray(saved)) {
        todos.value = saved.map(normalizeTodo).filter(Boolean)
      }
    } catch (e) {
      console.error('Failed to load cached todos', e)
    }
  }

  return {
    todos,
    allTodos,
    pendingTodos,
    completedTodos,
    pendingCount,
    completedCount,
    addTodo,
    toggleTodo,
    updateTodo,
    deleteTodo,
    clearCompleted,
    saveTodos,
    loadTodos,
    loadCachedTodos
  }
})
