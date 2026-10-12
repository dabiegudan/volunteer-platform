import { get, post } from '@/utils/request'

export const timeBankApi = {
  getAccount() {
    return get('/api/time-bank/account')
  },

  transfer(data) {
    return post('/api/time-bank/transfer', data)
  },

  getServices(params) {
    return get('/api/time-bank/services', params)
  },

  publishService(data) {
    return post('/api/time-bank/services', data)
  },

  redeemService(serviceId) {
    return post(`/api/time-bank/services/${serviceId}/redeem`)
  },

  getTransactions(params) {
    return get('/api/time-bank/transactions', params)
  },

  deposit(data) {
    return post('/api/time-bank/deposit', data)
  },

  withdraw(data) {
    return post('/api/time-bank/withdraw', data)
  }
}
