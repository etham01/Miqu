<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import { notificationApi } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import type { Notification } from '@/api/types'
import { relativeTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const notificationStore = useNotificationStore()

const activeTab = ref<'all' | '1' | '2' | '3'>('all')
const list = ref<Notification[]>([])
const loading = ref(false)
const page = ref(1)
const size = 20
const total = ref(0)
const hasNext = ref(false)

const tabs = [
  { key: 'all', label: '全部' },
  { key: '1', label: '关注' },
  { key: '2', label: '点赞' },
  { key: '3', label: '评论' },
] as const

const typeParam = computed(() =>
  activeTab.value === 'all' ? undefined : Number(activeTab.value),
)

/** 通知文案：不同类型说不同的话，但都要带上触发者昵称。 */
function describe(item: Notification): string {
  switch (item.type) {
    case 1:
      return '关注了你'
    case 2:
      return '赞了你的动态'
    case 3:
      return '评论了你的动态'
    default:
      return '给你发来了消息'
  }
}

/**
 * 请求序号。
 *
 * 「加载更多」的重复点击靠 loading 挡住，但**切换通知类型**是用户主动发起的新查询：
 * 若因上一个请求还在飞就丢掉它，就会出现"高亮是『点赞』、列表里却混着『关注』"的错位。
 * 因此 reset=true 时照发，改用序号丢弃**过期响应**。
 */
let requestSeq = 0

async function load(reset = true) {
  if (loading.value && !reset) return
  const seq = ++requestSeq
  if (reset) page.value = 1
  loading.value = true
  try {
    const result = await notificationApi.list({
      type: typeParam.value,
      page: page.value,
      size,
    })
    if (seq !== requestSeq) return
    list.value = reset ? result.list : [...list.value, ...result.list]
    total.value = result.total
    hasNext.value = result.hasNext
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function loadMore() {
  page.value += 1
  load(false)
}

function switchTab(key: typeof activeTab.value) {
  if (activeTab.value === key) return
  activeTab.value = key
  load(true)
}

async function markRead(item: Notification) {
  if (item.isRead) return
  await notificationApi.markRead(item.id)
  item.isRead = true
  notificationStore.refresh()
}

async function markAllRead() {
  if (!notificationStore.hasNotification) {
    ElMessage.info('没有未读通知')
    return
  }
  await notificationApi.markAllRead()
  list.value.forEach((item) => {
    item.isRead = true
  })
  notificationStore.refresh()
  ElMessage.success('已全部标记为已读')
}

onMounted(() => load(true))
</script>

<template>
  <div class="notifications">
    <div class="notifications__head miqu-card">
      <div class="notifications__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="notifications__tab"
          :class="{ 'is-active': activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
          <span
            v-if="tab.key === '1' && notificationStore.unread.follow"
            class="notifications__dot"
          />
          <span
            v-if="tab.key === '2' && notificationStore.unread.like"
            class="notifications__dot"
          />
          <span
            v-if="tab.key === '3' && notificationStore.unread.comment"
            class="notifications__dot"
          />
        </button>
      </div>

      <el-button text size="small" @click="markAllRead">
        <el-icon><Check /></el-icon> 全部已读
      </el-button>
    </div>

    <div v-if="loading && !list.length" class="miqu-card notifications__skeleton">
      <el-skeleton animated :rows="5" />
    </div>

    <EmptyState v-else-if="!list.length" icon="Bell" description="还没有收到通知" />

    <template v-else>
      <ul class="notifications__list miqu-card">
        <li
          v-for="item in list"
          :key="item.id"
          class="notifications__item"
          :class="{ 'is-unread': !item.isRead }"
          @click="markRead(item)"
        >
          <RouterLink :to="`/users/${item.actor.id}`" class="notifications__actor">
            <UserAvatar :src="item.actor.avatar" :nickname="item.actor.nickname" :size="40" />
          </RouterLink>

          <div class="notifications__body">
            <p class="notifications__text">
              <RouterLink :to="`/users/${item.actor.id}`" class="notifications__name">
                {{ item.actor.nickname }}
              </RouterLink>
              {{ describe(item) }}
            </p>

            <!-- 内容快照：目标动态被删除后仍能显示，不会变成空白条目 -->
            <p v-if="item.content" class="notifications__snapshot">
              「{{ item.content }}」
            </p>

            <span class="notifications__time">{{ relativeTime(item.createTime) }}</span>
          </div>

          <RouterLink
            v-if="item.postId"
            :to="`/posts/${item.postId}`"
            class="notifications__link"
            @click.stop
          >
            查看
          </RouterLink>
          <span v-else-if="!item.isRead" class="notifications__unread-dot" />
        </li>
      </ul>

      <div v-if="hasNext" class="notifications__more">
        <el-button :loading="loading" round @click="loadMore">加载更多</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.notifications__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  margin-bottom: 14px;
}

.notifications__tabs {
  display: flex;
  gap: 4px;
}

.notifications__tab {
  position: relative;
  padding: 7px 16px;
  border: none;
  background: transparent;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.notifications__tab:hover {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.notifications__tab.is-active {
  background: var(--miqu-primary-soft);
  color: var(--miqu-primary);
}

.notifications__dot {
  position: absolute;
  top: 6px;
  right: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--miqu-accent);
}

.notifications__skeleton {
  padding: 20px;
}

.notifications__list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow: hidden;
}

.notifications__item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 15px 18px;
  border-bottom: 1px solid var(--miqu-border);
  cursor: pointer;
  transition: background var(--miqu-transition);
}

.notifications__item:last-child {
  border-bottom: none;
}

.notifications__item:hover {
  background: var(--miqu-surface-hover);
}

.notifications__item.is-unread {
  background: var(--miqu-primary-softer);
}

.notifications__body {
  flex: 1;
  min-width: 0;
}

.notifications__text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.6;
}

.notifications__name {
  font-weight: 600;
}

.notifications__name:hover {
  color: var(--miqu-primary);
}

.notifications__snapshot {
  margin: 4px 0 0;
  padding: 6px 10px;
  border-radius: var(--miqu-radius-sm);
  background: var(--miqu-bg);
  color: var(--miqu-text-secondary);
  font-size: 12.5px;
  line-height: 1.5;
}

.notifications__time {
  display: inline-block;
  margin-top: 5px;
  font-size: 11.5px;
  color: var(--miqu-text-tertiary);
}

.notifications__link {
  flex-shrink: 0;
  align-self: center;
  font-size: 12.5px;
  color: var(--miqu-primary);
}

.notifications__unread-dot {
  flex-shrink: 0;
  align-self: center;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--miqu-accent);
}

.notifications__more {
  display: flex;
  justify-content: center;
  padding: 14px 0;
}
</style>
