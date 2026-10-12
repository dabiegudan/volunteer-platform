<template>
  <view class="page chat-page safe-bottom">
    <view class="page-shell">
      <view class="chat-head">
        <view class="page-brand">
          <text class="page-mark">询</text>
          <view class="page-brand__text">
            <text class="page-kicker">志愿服务平台</text>
            <text class="page-title">服务咨询</text>
            <text class="page-subtitle">活动政策、报名、补签和排班问题集中咨询</text>
          </view>
        </view>
      </view>

      <view class="consult-layout">
        <scroll-view class="faq-scroll shell-card" scroll-x>
          <view class="faq-row">
            <button v-for="item in faqs" :key="item.question || item" class="chip" @click="askFaq(item)">
              {{ item.question || item }}
            </button>
          </view>
        </scroll-view>

        <scroll-view class="chat-list shell-card" scroll-y :scroll-into-view="lastMessageId">
          <view
            v-for="(item, index) in messages"
            :id="`msg-${index}`"
            :key="index"
            class="chat-bubble"
            :class="item.role"
          >
            <text>{{ item.content }}</text>
          </view>
        </scroll-view>

        <view class="chat-input shell-card">
          <input class="input" v-model="question" placeholder="输入你的问题" @confirm="send" />
          <button class="btn btn--primary" @click="send">发送</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { chatApi } from '@/api/ai'
import { sanitizeInput } from '@/utils/validate'

const faqs = ref([])
const messages = ref([
  { role: 'assistant', content: '你好，可以在这里查询志愿服务流程、报名要求、补签材料和活动建议。' }
])
const question = ref('')

const lastMessageId = computed(() => `msg-${messages.value.length - 1}`)

async function loadFaqs() {
  try {
    const result = await chatApi.getFAQs()
    faqs.value = Array.isArray(result) ? result : (result?.list || [])
  } catch (error) {
    faqs.value = ['如何报名活动？', '补签需要什么材料？', '怎么查看服务时长？']
  }
}

function askFaq(item) {
  question.value = item.question || item
  send()
}

async function send() {
  const content = sanitizeInput(question.value, 500)
  if (!content) return
  messages.value.push({ role: 'user', content })
  question.value = ''
  try {
    const result = await chatApi.chatWithHistory({
      question: content,
      history: messages.value.map(item => ({ role: item.role, content: item.content }))
    })
    messages.value.push({
      role: 'assistant',
      content: result?.answer || result?.content || String(result || '暂时没有生成回答')
    })
  } catch (error) {
    messages.value.push({ role: 'assistant', content: '服务暂时不可用，请稍后再试。' })
  }
}

loadFaqs()
</script>

<style lang="scss" scoped>
$client-ink: #2a1214;
$client-muted: #667772;
$client-paper: #fffafa;
$client-red: #a51f2b;
$client-red-soft: #fff0ee;
$client-accent: #e08b73;

.chat-page {
  min-height: 100vh;
  padding: 0 14px 14px;
  overflow-x: hidden;
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $client-ink;
}

.page-shell {
  display: flex;
  width: 100%;
  max-width: 760px;
  height: calc(100vh - 58px);
  min-height: 0;
  flex-direction: column;
  margin: 0 auto;
  padding-top: 14px;
}

.chat-head {
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

.chat-head::after {
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

.page-title,
.page-kicker,
.page-subtitle {
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

.consult-layout {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
}

.faq-scroll {
  min-height: 52px;
  padding: 9px 10px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 24px rgba(16, 35, 31, 0.06);
  white-space: nowrap;
}

.faq-row {
  display: flex;
  gap: 8px;
}

.chip {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(165, 31, 43, 0.13);
  border-radius: $radius-sm;
  background: $client-red-soft;
  color: $client-red;
  font-size: 13px;
  font-weight: 800;
}

.chat-list {
  min-height: 0;
  flex: 1;
  padding: 14px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.chat-bubble {
  width: fit-content;
  max-width: 82%;
  margin: 10px 0;
  padding: 11px 12px;
  border: 1px solid rgba(16, 35, 31, 0.09);
  border-radius: $radius-md;
  font-weight: 700;
  line-height: 1.7;
  box-shadow: 0 8px 18px rgba(16, 35, 31, 0.05);
}

.chat-bubble.assistant {
  background: $client-paper;
  color: $client-ink;
}

.chat-bubble.user {
  margin-left: auto;
  border-color: rgba(165, 31, 43, 0.26);
  background: $client-red;
  color: #fff;
}

.chat-input {
  position: sticky;
  bottom: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 72px;
  gap: 10px;
  padding: 10px 10px calc(10px + env(safe-area-inset-bottom));
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -8px 24px rgba(16, 35, 31, 0.06);
}

.chat-input .input {
  border-color: rgba(16, 35, 31, 0.12);
  background: $client-paper;
}

.chat-input .btn--primary {
  border-color: $client-red;
  background: $client-red;
}

@media (max-width: 430px) {
  .chat-page {
    padding-right: 12px;
    padding-left: 12px;
  }

  .chat-head {
    padding: 16px 14px;
  }

  .page-title {
    font-size: 24px;
  }

  .chat-input {
    grid-template-columns: minmax(0, 1fr) 64px;
  }
}
</style>
