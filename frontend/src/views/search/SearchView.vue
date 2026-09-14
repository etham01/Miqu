<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import type { UserSearchItem } from '@/api/types'
import UserCard from '@/components/UserCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()

const keyword = ref((route.query.keyword as string) ?? '')
const results = ref<UserSearchItem[]>([])
const loading = ref(false)
const searched = ref(false)
const page = ref(1)
const size = 10
const total = ref(0)
const hasNext = ref(false)

/**
 * 请求序号。
 *
 * 「加载更多」的重复点击靠 loading 挡住，但**换关键词**是用户主动发起的新查询：
 * 若因上一个请求还在飞就丢掉它，就会出现"地址栏已经换了关键词、结果还是上一个"的错位。
 * 因此 reset=true 时照发，改用序号丢弃**过期响应**。
 */
let requestSeq = 0

async function search(reset = true) {
  const value = keyword.value.trim()
  if (!value) {
    // 清空关键词同样是一次"新查询"：让在飞的旧请求失效，
    // 否则旧响应回来会把已经清空的列表重新填上。
    requestSeq++
    results.value = []
    searched.value = false
    return
  }

  if (loading.value && !reset) return
  const seq = ++requestSeq
  if (reset) page.value = 1
  loading.value = true
  try {
    const result = await userApi.search(value, page.value, size)
    if (seq !== requestSeq) return
    results.value = reset ? result.list : [...results.value, ...result.list]
    total.value = result.total
    hasNext.value = result.hasNext
    searched.value = true
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function loadMore() {
  page.value += 1
  search(false)
}

function onSubmit() {
  const value = keyword.value.trim()
  // 把关键词同步到地址栏，刷新或分享链接后仍能复现同一次搜索
  router.replace({ name: 'search', query: value ? { keyword: value } : {} })
  search(true)
}

function onFollowChange(payload: { id: string; following: boolean }) {
  const target = results.value.find((item) => item.id === payload.id)
  if (target) {
    target.followedByMe = payload.following
    // 关注接口会回传最新粉丝数，但列表项里没有该字段，这里做本地增减
    target.followerCount = Math.max(0, target.followerCount + (payload.following ? 1 : -1))
  }
}

watch(
  () => route.query.keyword,
  (value) => {
    const next = (value as string) ?? ''
    if (next !== keyword.value) {
      keyword.value = next
      search(true)
    }
  },
)

onMounted(() => {
  if (keyword.value) search(true)
})
</script>

<template>
  <div class="search">
    <div class="search__bar miqu-card">
      <el-input
        v-model="keyword"
        size="large"
        placeholder="输入昵称或用户名，找到想认识的人"
        clearable
        @keyup.enter="onSubmit"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" size="large" round :loading="loading" @click="onSubmit">
        搜索
      </el-button>
    </div>

    <div v-if="loading && !results.length" class="search__skeleton">
      <div v-for="n in 4" :key="n" class="miqu-card search__skeleton-card">
        <el-skeleton animated :rows="2" />
      </div>
    </div>

    <EmptyState
      v-else-if="!searched"
      icon="Search"
      description="输入关键词开始探索"
    />

    <EmptyState
      v-else-if="!results.length"
      icon="UserFilled"
      :description="`没有找到与「${keyword}」相关的用户`"
    />

    <template v-else>
      <p class="search__count">找到 {{ total }} 位用户</p>

      <UserCard
        v-for="item in results"
        :key="item.id"
        :id="item.id"
        :username="item.username"
        :nickname="item.nickname"
        :avatar="item.avatar"
        :bio="item.bio"
        :follower-count="item.followerCount"
        :followed-by-me="item.followedByMe"
        @follow-change="onFollowChange"
      />

      <div v-if="hasNext" class="search__more">
        <el-button :loading="loading" round @click="loadMore">加载更多</el-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.search__bar {
  display: flex;
  gap: 10px;
  padding: 16px;
  margin-bottom: 18px;
}

.search__bar :deep(.el-input__wrapper) {
  box-shadow: none;
  background: var(--miqu-bg);
  border-radius: 10px;
}

.search__count {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--miqu-text-tertiary);
}

.search__skeleton-card {
  padding: 18px;
  margin-bottom: 10px;
}

.search__more {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}
</style>
