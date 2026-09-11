<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { conversationApi, messageApi } from '@/api/message'
import { useNotificationStore } from '@/stores/notification'
import type { Conversation, Message } from '@/api/types'
import { chatTime, conversationTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()

const conversations = ref<Conversation[]>([])
const activeId = ref<string>('')
const messages = ref<Message[]>([])
const draft = ref('')
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const hasOlder = ref(false)

const scrollRef = ref<HTMLElement | null>(null)

const activeConversation = computed(() =>
  conversations.value.find((item) => item.id === activeId.value),
)

/** 聊天页轮询要更快：用户正盯着这个页面等回复，3 秒是体感与请求量的折中。 */
const CHAT_POLL_INTERVAL = 3000
const PAGE_SIZE = 30

let chatTimer: number | undefined

async function loadConversations() {
  loadingConversations.value = true
  try {
    const result = await conversationApi.list(1, 50)
    conversations.value = result.list
  } finally {
    loadingConversations.value = false
  }
}

async function selectConversation(id: string) {
  if (activeId.value === id) return
  activeId.value = id
  messages.value = []
  hasOlder.value = false
  await loadLatestMessages()
  await markRead()
  // 把当前会话同步到地址栏，刷新后仍停留在这里
  router.replace({ name: 'messages', query: { conversation: id } })
}

async function loadLatestMessages() {
  if (!activeId.value) return
  loadingMessages.value = true
  try {
    const list = await conversationApi.messages(activeId.value, undefined, PAGE_SIZE)
    messages.value = list
    hasOlder.value = list.length >= PAGE_SIZE
    await scrollToBottom()
  } finally {
    loadingMessages.value = false
  }
}

/** 往前翻历史：用最早一条的 id 作为游标。 */
async function loadOlder() {
  if (!activeId.value || !messages.value.length) return
  const earliest = messages.value[0].id
  const older = await conversationApi.messages(activeId.value, earliest, PAGE_SIZE)
  if (older.length) {
    messages.value = [...older, ...messages.value]
  }
  hasOlder.value = older.length >= PAGE_SIZE
}

async function markRead() {
  if (!activeId.value) return
  await conversationApi.markRead(activeId.value)
  const target = conversations.value.find((item) => item.id === activeId.value)
  if (target) target.unreadCount = 0
  notificationStore.refresh()
}

async function send() {
  const content = draft.value.trim()
  const conversation = activeConversation.value
  if (!content || !conversation) return
  if (sending.value) return

  sending.value = true
  try {
    const created = await messageApi.send(conversation.partner.id, content)
    draft.value = ''
    messages.value = [...messages.value, created]
    // 会话列表里的预览与排序也要跟着更新
    conversation.lastMessagePreview = content
    conversation.lastMessageTime = created.createTime
    await scrollToBottom()
  } finally {
    sending.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  const element = scrollRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

/** 轮询：拉最新一页，按 id 合并出新增的消息。 */
async function pollNewMessages() {
  if (!activeId.value || document.hidden) return
  const list = await conversationApi.messages(activeId.value, undefined, PAGE_SIZE)
  const knownIds = new Set(messages.value.map((item) => item.id))
  const fresh = list.filter((item) => !knownIds.has(item.id))
  if (fresh.length) {
    messages.value = [...messages.value, ...fresh]
    await scrollToBottom()
    await markRead()
  }
}

function startChatPolling() {
  stopChatPolling()
  chatTimer = window.setInterval(() => {
    pollNewMessages().catch(() => undefined)
    loadConversations().catch(() => undefined)
  }, CHAT_POLL_INTERVAL)
}

function stopChatPolling() {
  if (chatTimer !== undefined) {
    window.clearInterval(chatTimer)
    chatTimer = undefined
  }
}

function onVisibilityChange() {
  // 页面切回前台时立即补一次，不必等下一个轮询周期
  if (!document.hidden) {
    pollNewMessages().catch(() => undefined)
  }
}

watch(
  () => route.query.conversation,
  (value) => {
    const id = value as string | undefined
    if (id && id !== activeId.value && conversations.value.some((item) => item.id === id)) {
      selectConversation(id)
    }
  },
)

onMounted(async () => {
  await loadConversations()

  const requested = route.query.conversation as string | undefined
  if (requested && conversations.value.some((item) => item.id === requested)) {
    await selectConversation(requested)
  } else if (conversations.value.length) {
    // 默认打开最近的一个会话，避免右侧一直空着
    await selectConversation(conversations.value[0].id)
  }

  startChatPolling()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  stopChatPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})

function onComposerKeydown(event: KeyboardEvent) {
  // Enter 发送，Shift + Enter 换行 —— 聊天窗口的通用约定
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}
</script>

<template>
  <div class="chat">
    <aside class="chat__aside miqu-card">
      <div class="chat__aside-head">
        <h2>消息</h2>
        <el-button
          text
          size="small"
          :loading="loadingConversations"
          @click="loadConversations"
        >
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <div v-if="loadingConversations && !conversations.length" class="chat__aside-loading">
        <el-skeleton animated :rows="4" />
      </div>

      <EmptyState
        v-else-if="!conversations.length"
        icon="ChatDotRound"
        description="还没有会话，去别人主页打个招呼吧"
      />

      <ul v-else class="chat__list">
        <li
          v-for="item in conversations"
          :key="item.id"
          class="chat__item"
          :class="{ 'is-active': item.id === activeId }"
          @click="selectConversation(item.id)"
        >
          <el-badge :value="item.unreadCount" :hidden="!item.unreadCount" :max="99">
            <UserAvatar
              :src="item.partner.avatar"
              :nickname="item.partner.nickname"
              :size="42"
            />
          </el-badge>

          <div class="chat__item-body">
            <div class="chat__item-top">
              <span class="chat__item-name">{{ item.partner.nickname }}</span>
              <span class="chat__item-time">
                {{ conversationTime(item.lastMessageTime) }}
              </span>
            </div>
            <p class="chat__item-preview miqu-truncate">{{ item.lastMessagePreview }}</p>
          </div>
        </li>
      </ul>
    </aside>

    <section class="chat__main miqu-card">
      <template v-if="activeConversation">
        <header class="chat__head">
          <el-button
            text
            class="chat__back"
            @click="router.push(`/users/${activeConversation.partner.id}`)"
          >
            <UserAvatar
              :src="activeConversation.partner.avatar"
              :nickname="activeConversation.partner.nickname"
              :size="36"
            />
            <div class="chat__head-text">
              <strong>{{ activeConversation.partner.nickname }}</strong>
              <small>@{{ activeConversation.partner.username }}</small>
            </div>
          </el-button>
        </header>

        <div ref="scrollRef" class="chat__messages">
          <div v-if="hasOlder" class="chat__older">
            <el-button text size="small" @click="loadOlder">查看更早的消息</el-button>
          </div>

          <div
            v-for="message in messages"
            :key="message.id"
            class="chat__bubble-row"
            :class="{ 'is-mine': message.mine }"
          >
            <UserAvatar
              v-if="!message.mine"
              :src="activeConversation.partner.avatar"
              :nickname="activeConversation.partner.nickname"
              :size="32"
            />
            <div class="chat__bubble">
              <p class="chat__bubble-text">{{ message.content }}</p>
              <span class="chat__bubble-time">{{ chatTime(message.createTime) }}</span>
            </div>
          </div>
        </div>

        <footer class="chat__composer">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            maxlength="1000"
            resize="none"
            placeholder="输入消息，Enter 发送 / Shift + Enter 换行"
            @keydown="onComposerKeydown"
          />
          <el-button
            type="primary"
            round
            :loading="sending"
            :disabled="!draft.trim()"
            @click="send"
          >
            发送
          </el-button>
        </footer>
      </template>

      <EmptyState
        v-else
        icon="ChatDotRound"
        description="选择左侧的会话开始聊天"
      />
    </section>
  </div>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 296px minmax(0, 1fr);
  gap: 14px;
  height: calc(100vh - var(--miqu-header-height) - 80px);
  min-height: 460px;
}

.chat__aside {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat__aside-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--miqu-border);
}

.chat__aside-head h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.chat__aside-loading {
  padding: 16px;
}

.chat__list {
  flex: 1;
  list-style: none;
  margin: 0;
  padding: 6px;
  overflow-y: auto;
}

.chat__item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 11px;
  border-radius: var(--miqu-radius);
  cursor: pointer;
  transition: background var(--miqu-transition);
}

.chat__item:hover {
  background: var(--miqu-surface-hover);
}

.chat__item.is-active {
  background: var(--miqu-primary-soft);
}

.chat__item-body {
  flex: 1;
  min-width: 0;
}

.chat__item-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.chat__item-name {
  font-weight: 600;
  font-size: 13.5px;
}

.chat__item-time {
  font-size: 11.5px;
  color: var(--miqu-text-tertiary);
  flex-shrink: 0;
}

.chat__item-preview {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

.chat__main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat__head {
  padding: 10px 14px;
  border-bottom: 1px solid var(--miqu-border);
}

.chat__back {
  display: flex;
  align-items: center;
  gap: 10px;
  height: auto;
  padding: 4px 6px;
}

.chat__head-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.3;
}

.chat__head-text small {
  color: var(--miqu-text-tertiary);
  font-size: 11.5px;
}

.chat__messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 18px 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat__older {
  text-align: center;
}

.chat__bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 78%;
}

.chat__bubble-row.is-mine {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.chat__bubble {
  padding: 9px 13px;
  border-radius: 14px;
  background: var(--miqu-bg);
  border: 1px solid var(--miqu-border);
  border-bottom-left-radius: 4px;
}

.chat__bubble-row.is-mine .chat__bubble {
  background: var(--miqu-primary);
  border-color: var(--miqu-primary);
  color: #fff;
  border-bottom-left-radius: 14px;
  border-bottom-right-radius: 4px;
}

.chat__bubble-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat__bubble-time {
  display: block;
  margin-top: 3px;
  font-size: 10.5px;
  opacity: 0.62;
  text-align: right;
}

.chat__composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid var(--miqu-border);
}

.chat__composer :deep(.el-textarea__inner) {
  border-radius: var(--miqu-radius);
  background: var(--miqu-bg);
  box-shadow: none;
}

@media (max-width: 860px) {
  .chat {
    grid-template-columns: 1fr;
    height: auto;
  }

  .chat__aside {
    max-height: 260px;
  }

  .chat__main {
    min-height: 420px;
  }
}
</style>
