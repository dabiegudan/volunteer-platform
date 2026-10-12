import { get, post } from '@/utils/request'

export const creditApi = {
  getOverview() {
    return get('/api/credit/overview')
  },

  getTransactions(params) {
    return get('/api/credit/transactions', params)
  },

  getMedals() {
    return get('/api/credit/medals')
  },

  getTasks() {
    return get('/api/credit/tasks')
  },

  dailyCheckIn() {
    return post('/api/credit/checkin')
  },

  updateTaskProgress(data) {
    return post('/api/credit/task/progress', data)
  },

  claimTask(data) {
    return post('/api/credit/task/claim', data)
  },

  getProducts(params) {
    return get('/api/credit/products', params)
  },

  exchangeProduct(data) {
    return post('/api/credit/exchange', data)
  },

  getOrders(params) {
    return get('/api/credit/orders', params)
  },

  addCredit(data) {
    return post('/api/credit/add', data)
  },

  consumeCredit(data) {
    return post('/api/credit/consume', data)
  }
}
