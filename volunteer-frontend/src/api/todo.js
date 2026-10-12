import { get, post, put, del } from '@/utils/request'

// 待办事项相关API
export const todoApi = {
  // 获取待办列表
  getTodoList(params) {
    return get('/api/todos', params)
  },
  
  // 创建待办
  createTodo(data) {
    return post('/api/todos', data)
  },
  
  // 更新待办
  updateTodo(id, data) {
    return put(`/api/todos/${id}`, data)
  },
  
  // 删除待办
  deleteTodo(id) {
    return del(`/api/todos/${id}`)
  },

  clearCompleted() {
    return del('/api/todos/completed')
  },
  
  // 切换待办完成状态
  toggleTodo(id, completed) {
    return put(`/api/todos/${id}/toggle`, { completed })
  }
}
