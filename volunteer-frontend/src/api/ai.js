import { get, post } from '@/utils/request'

// AI相关API
export const aiApi = {
  // 获取活动推荐
  getRecommendations(params) {
    return get('/api/ai/recommend', params)
  },
  
  // 获取AI智能推荐（带推荐理由）
  getAIRecommendations(params) {
    return get('/api/ai/recommend/ai', params)
  },
  
  // 获取推荐详情（带匹配度分析）
  getRecommendationsWithExplanation(params) {
    return get('/api/ai/recommend/detail', params)
  },

  // 根据描述生成活动草稿
  generateActivityDraft(data, options = {}) {
    return post('/api/ai/activity/draft', data, {
      timeout: 60000,
      ...options
    })
  },
  
  // 情感分析
  analyzeSentiment(data) {
    return post('/api/ai/sentiment', data)
  },
  
  // 申请审核分析
  analyzeApplication(data) {
    return post('/api/ai/application/analyze', data)
  },
  
  // 批量申请审核分析
  batchAnalyzeApplications(data) {
    return post('/api/ai/application/batch-analyze', data)
  },
  
  // 生成活动总结
  generateSummary(data) {
    return post('/api/ai/summary/generate', data)
  },
  
  // 生成反思总结
  generateReflection(data) {
    return post('/api/ai/summary/reflection', data)
  },
  
  // 生成证书内容
  generateCertificateContent(data) {
    return post('/api/ai/summary/certificate', data)
  },
  
  // 生成排班方案
  generateSchedule(data) {
    return post('/api/ai/schedule/generate', data)
  },
  
  // 志愿者匹配
  matchVolunteers(data) {
    return post('/api/ai/schedule/match', data)
  },
  
  // 生成多日排班方案
  generateMultiDaySchedule(data) {
    return post('/api/ai/schedule/multi-day', data)
  },
  
  // 分析排班冲突
  analyzeConflicts(data) {
    return post('/api/ai/schedule/conflicts', data)
  },
  
  // 内容润色
  polishContent(data) {
    return post('/api/ai/polish', data)
  },
  
  // 敏感词检测
  detectSensitiveWords(data) {
    return post('/api/ai/sensitive', data)
  },
  
  // 内容审核处理
  processContent(data) {
    return post('/api/ai/process', data)
  },
  
  // 添加知识到知识库
  addKnowledge(data) {
    return post('/api/ai/knowledge/add', data)
  },
  
  // 搜索知识库
  searchKnowledge(params) {
    return get('/api/ai/knowledge/search', params)
  },
  
  // 按分类搜索知识库
  searchKnowledgeByCategory(params) {
    return get('/api/ai/knowledge/search/category', params)
  },

  chat(data) {
    const message = typeof data === 'string' ? data : (data?.message || data?.question || '')
    return post('/api/ai/chat', message).then(answer => ({ answer }))
  },

  chatWithHistory(data) {
    return post('/api/ai/chat/history', data)
  }
}

// AI聊天相关API
export const chatApi = {
  // 智能对话
  chat(message) {
    const content = typeof message === 'string' ? message : (message?.message || message?.question || '')
    return post('/api/ai/chat', content).then(answer => ({ answer }))
  },
  
  // 带历史对话的智能问答
  chatWithHistory(data) {
    return post('/api/ai/chat/history', {
      ...data,
      message: data?.message || data?.question || ''
    })
  },
  
  // 获取常见问题列表
  getFAQs() {
    return get('/api/ai/chat/faqs').then(result => result?.faqs || result?.list || result || [])
  },
  
  // 健康检查
  healthCheck() {
    return get('/api/ai/chat/health')
  }
}

// 兼容页面调用的AI API
export const legacyAiApi = {
  // 获取活动推荐
  getRecommendations(params) {
    return get('/api/ai/recommend', params)
  },
  
  // 获取AI智能推荐（带推荐理由）
  getAIRecommendations(params) {
    return get('/api/ai/recommend/ai', params)
  },
  
  // 获取推荐详情（带匹配度分析）
  getRecommendationsWithExplanation(params) {
    return get('/api/ai/recommend/detail', params)
  },
  
  // 智能对话（兼容页面调用）
  chat(data) {
    return post('/api/ai/chat', data)
  },
  
  // 带历史对话的智能问答（兼容页面调用）
  chatWithHistory(data) {
    return post('/api/ai/chat/history', data)
  }
}
