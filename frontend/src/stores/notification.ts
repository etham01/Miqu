import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { messageApi } from '@/api/message'
import { notificationApi } from '@/api/notification'
import type { NotificationUnread } from '@/api/types'

/** 导航栏角标的轮询间隔。聊天页面会另开一个更快的定时器。 */
const POLL_INTERVAL = 15000

/**
 * 未读计数。
 *
 * **通知与私信是两个独立的计数**，不要相加：后端刻意让私信不写通知表，
 * 否则同一件事会在两个角标里重复出现。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref<NotificationUnread>({ total: 0, follow: 0, like: 0, comment: 0 })
  const messageUnread = ref(0)

  const hasNotification = computed(() => unread.value.total > 0)
  const hasMessage = computed(() => messageUnread.value > 0)

  let timer: number | undefined

  async function refresh() {
    // 两个请求互相独立，用 allSettled 避免其中一个失败就整个角标不刷新
    const [notificationResult, messageResult] = await Promise.allSettled([
      notificationApi.unreadCount(),
      messageApi.unreadCount(),
    ])
    if (notificationResult.status === 'fulfilled') {
      unread.value = notificationResult.value
    }
    if (messageResult.status === 'fulfilled') {
      messageUnread.value = messageResult.value.total
    }
  }

  function startPolling() {
    stopPolling()
    refresh()
    timer = window.setInterval(refresh, POLL_INTERVAL)
  }

  function stopPolling() {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  }

  function reset() {
    unread.value = { total: 0, follow: 0, like: 0, comment: 0 }
    messageUnread.value = 0
  }

  return {
    unread,
    messageUnread,
    hasNotification,
    hasMessage,
    refresh,
    startPolling,
    stopPolling,
    reset,
  }
})
