import { get, post, del } from '@/utils/request'

// 通知相关API
export const notificationApi = {
  // 获取消息列表
  getMessageList(params) {
    return get('/api/notification/page', params)
  },
  
  // 获取消息列表（兼容页面调用）
  getNotificationList(params) {
    return get('/api/notification/page', params)
  },
  
  // 获取未读消息数量
  getUnreadCount() {
    return get('/api/notification/unread/count')
  },
  
  // 标记消息为已读
  markAsRead(messageId) {
    return post(`/api/notification/${messageId}/read`)
  },
  
  // 标记消息为已读（兼容页面调用）
  markRead(messageId) {
    return post(`/api/notification/${messageId}/read`)
  },
  
  // 标记所有消息为已读
  markAllAsRead() {
    return post('/api/notification/read/all')
  },
  
  // 标记所有消息为已读（兼容页面调用）
  markAllRead() {
    return post('/api/notification/read/all')
  },

  deleteMessage(messageId) {
    return del(`/api/notification/${messageId}`)
  }
}
