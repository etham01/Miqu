<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { useUserStore } from '@/stores/user'
import type { Post } from '@/api/types'
import PostCard from '@/components/PostCard.vue'
import PostEditor from '@/components/PostEditor.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const userStore = useUserStore()

const tab = ref<'latest' | 'following'>('latest')
const posts = ref<Post[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const hasNext = ref(false)
const loading = ref(false)

/**
 * 请求序号。
 *
 * 「加载更多」的重复点击要靠 loading 挡住，但**切换 tab** 是用户主动发起的新查询，
 * 不能因为上一个请求还在飞就把它丢掉——否则会出现"标签已经变成『我的关注』，
 * 列表里还是最新动态"的错位（两类数据来自不同接口，视觉上却无法区分）。
 *
 * 所以：reset=true 时即使正在 loading 也照发，改用序号把**过期响应**丢弃。
 */
let requestSeq = 0

async function load(reset = false) {
  if (loading.value && !reset) return
  const seq = ++requestSeq
  if (reset) page.value = 1
  loading.value = true
  try {
    const result = await postApi.list(tab.value, page.value, size)
    // 已有更新的请求发出，本次响应已过期，直接丢弃，避免覆盖新数据
    if (seq !== requestSeq) return
    posts.value = reset ? result.list : [...posts.value, ...result.list]
    total.value = result.total
    hasNext.value = result.hasNext
  } finally {
    // 只有最后一次请求才有资格复位 loading，否则会把新请求的转圈提前关掉
    if (seq === requestSeq) loading.value = false
  }
}

function loadMore() {
  page.value += 1
  load()
}

/**
 * 切换列表模式。
 *
 * 「我的关注」需要登录（后端对该模式返回 401），
 * 未登录时直接引导去登录，而不是发一个注定失败的请求再弹错误提示。
 */
function selectTab(value: 'latest' | 'following') {
  if (value === tab.value) return
  if (value === 'following' && !userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: '/' } })
    return
  }
  tab.value = value
  load(true)
}

function onCreated() {
  tab.value = 'latest'
  load(true)
}

function onDeleted(id: string) {
  posts.value = posts.value.filter((item) => item.id !== id)
  total.value = Math.max(0, total.value - 1)
}

function refresh() {
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="home">
    <PostEditor v-if="userStore.isLogin" @created="onCreated" />

    <div class="home__tabs miqu-card">
      <button
        class="home__tab"
        :class="{ 'is-active': tab === 'latest' }"
        @click="selectTab('latest')"
      >
        最新动态
      </button>
      <button
        class="home__tab"
        :class="{ 'is-active': tab === 'following' }"
        @click="selectTab('following')"
      >
        <el-icon><Star /></el-icon>
        我的关注
      </button>
      <div class="home__tabs-spacer" />
      <el-button text size="small" :loading="loading" @click="refresh">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <div v-if="loading && !posts.length" class="home__skeleton">
      <div v-for="n in 3" :key="n" class="miqu-card home__skeleton-card">
        <el-skeleton animated :rows="3" />
      </div>
    </div>

    <EmptyState
      v-else-if="!posts.length"
      icon="Document"
      :description="
        tab === 'following' ? '你关注的人还没有发布动态' : '还没有人发布动态，来做第一个吧'
      "
    >
      <el-button v-if="tab === 'following'" @click="router.push({ name: 'search' })">
        去发现有趣的人
      </el-button>
    </EmptyState>

    <template v-else>
      <PostCard
        v-for="post in posts"
        :key="post.id"
        :post="post"
        @deleted="onDeleted"
      />

      <div class="home__more">
        <el-button v-if="hasNext" :loading="loading" round @click="loadMore">
          加载更多
        </el-button>
        <span v-else class="home__end">已经到底了 · 共 {{ total }} 条动态</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.home__tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  margin-bottom: 16px;
}

.home__tab {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 15px;
  border: none;
  background: transparent;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.home__tab:hover {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.home__tab.is-active {
  background: var(--miqu-primary-soft);
  color: var(--miqu-primary);
}

.home__tabs-spacer {
  flex: 1;
}

.home__skeleton-card {
  padding: 20px;
  margin-bottom: 14px;
}

.home__more {
  display: flex;
  justify-content: center;
  padding: 12px 0 4px;
}

.home__end {
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}
</style>
