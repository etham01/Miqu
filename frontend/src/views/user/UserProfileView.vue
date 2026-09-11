<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import { conversationApi } from '@/api/message'
import { useUserStore } from '@/stores/user'
import type { Post, UserFollow, UserProfile } from '@/api/types'
import { GENDER_LABELS, relativeTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import FollowButton from '@/components/FollowButton.vue'
import PostCard from '@/components/PostCard.vue'
import UserCard from '@/components/UserCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReportDialog from '@/components/ReportDialog.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userId = computed(() => route.params.id as string)
const profile = ref<UserProfile | null>(null)
const notFound = ref(false)
const activeTab = ref<'posts' | 'following' | 'followers'>('posts')

const posts = ref<Post[]>([])
const relations = ref<UserFollow[]>([])
const loading = ref(false)
const page = ref(1)
const size = 10
const hasNext = ref(false)
const listTotal = ref(0)
const reportVisible = ref(false)

const isSelf = computed(() => userStore.user?.id === userId.value)
const following = ref(false)
const mutual = ref(false)

async function loadProfile() {
  loading.value = true
  try {
    const data = await userApi.profile(userId.value)
    profile.value = data
    following.value = data.followedByMe
    mutual.value = data.mutual
    notFound.value = false
  } catch (error) {
    if ((error as { code?: number }).code === 404) {
      notFound.value = true
    }
  } finally {
    loading.value = false
  }
}

async function loadTab(reset = true) {
  if (reset) {
    page.value = 1
    posts.value = []
    relations.value = []
  }
  loading.value = true
  try {
    if (activeTab.value === 'posts') {
      const result = await userApi.posts(userId.value, page.value, size)
      posts.value = reset ? result.list : [...posts.value, ...result.list]
      listTotal.value = result.total
      hasNext.value = result.hasNext
    } else {
      const fetcher = activeTab.value === 'following' ? userApi.following : userApi.followers
      const result = await fetcher(userId.value, page.value, size)
      relations.value = reset ? result.list : [...relations.value, ...result.list]
      listTotal.value = result.total
      hasNext.value = result.hasNext
    }
  } finally {
    loading.value = false
  }
}

function switchTab(tab: 'posts' | 'following' | 'followers') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  loadTab(true)
}

function loadMore() {
  page.value += 1
  loadTab(false)
}

function onFollowChange(payload: { following: boolean }) {
  following.value = payload.following
  if (profile.value) {
    profile.value.followedByMe = payload.following
    profile.value.followerCount = Math.max(
      0,
      profile.value.followerCount + (payload.following ? 1 : -1),
    )
  }
}

async function sendMessage() {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  // 先取回（或创建）会话，再带着会话 ID 跳到消息页
  const conversation = await conversationApi.open(userId.value)
  router.push({ name: 'messages', query: { conversation: conversation.id } })
}

function onPostDeleted(id: string) {
  posts.value = posts.value.filter((item) => item.id !== id)
  listTotal.value = Math.max(0, listTotal.value - 1)
  if (profile.value) {
    profile.value.postCount = Math.max(0, profile.value.postCount - 1)
  }
}

watch(userId, () => {
  activeTab.value = 'posts'
  profile.value = null
  notFound.value = false
  loadProfile()
  loadTab(true)
})

onMounted(() => {
  loadProfile()
  loadTab(true)
})
</script>

<template>
  <div class="profile-page">
    <div v-if="notFound" class="miqu-card">
      <EmptyState icon="UserFilled" description="该用户不存在或已注销">
        <el-button @click="router.push({ name: 'search' })">去找找其他人</el-button>
      </EmptyState>
    </div>

    <div v-else-if="!profile" class="miqu-card profile-page__skeleton">
      <el-skeleton animated :rows="4" />
    </div>

    <template v-else>
      <section class="hero miqu-card">
        <div class="hero__top">
          <UserAvatar :src="profile.avatar" :nickname="profile.nickname" :size="84" />

          <div class="hero__actions">
            <template v-if="isSelf">
              <el-button round @click="router.push({ name: 'profile' })">编辑资料</el-button>
            </template>
            <template v-else-if="userStore.isLogin">
              <FollowButton
                :user-id="profile.id"
                :following="following"
                :mutual="mutual"
                @change="onFollowChange"
              />
              <el-button round @click="sendMessage">
                <el-icon><ChatDotRound /></el-icon> 私信
              </el-button>
              <el-dropdown trigger="click">
                <el-button text circle><el-icon><MoreFilled /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="reportVisible = true">
                      <el-icon><Warning /></el-icon> 举报
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
            <template v-else>
              <el-button
                type="primary"
                round
                @click="router.push({ name: 'login', query: { redirect: route.fullPath } })"
              >
                登录后关注
              </el-button>
            </template>
          </div>
        </div>

        <div class="hero__info">
          <div class="hero__name">
            <h1>{{ profile.nickname }}</h1>
            <span class="hero__username">@{{ profile.username }}</span>
            <el-tag v-if="mutual" size="small" type="success" round>互相关注</el-tag>
          </div>
          <p class="hero__bio">{{ profile.bio || '这个人很神秘，什么都没留下' }}</p>
          <div class="hero__meta">
            <span v-if="profile.gender">{{ GENDER_LABELS[profile.gender] }}</span>
            <span>加入于 {{ relativeTime(profile.createTime) }}</span>
          </div>
        </div>

        <div class="hero__stats">
          <button class="hero__stat" @click="switchTab('posts')">
            <strong>{{ profile.postCount }}</strong>
            <span>动态</span>
          </button>
          <button class="hero__stat" @click="switchTab('following')">
            <strong>{{ profile.followingCount }}</strong>
            <span>关注</span>
          </button>
          <button class="hero__stat" @click="switchTab('followers')">
            <strong>{{ profile.followerCount }}</strong>
            <span>粉丝</span>
          </button>
        </div>
      </section>

      <div class="tabs miqu-card">
        <button
          class="tabs__item"
          :class="{ 'is-active': activeTab === 'posts' }"
          @click="switchTab('posts')"
        >
          动态
        </button>
        <button
          class="tabs__item"
          :class="{ 'is-active': activeTab === 'following' }"
          @click="switchTab('following')"
        >
          关注
        </button>
        <button
          class="tabs__item"
          :class="{ 'is-active': activeTab === 'followers' }"
          @click="switchTab('followers')"
        >
          粉丝
        </button>
      </div>

      <template v-if="activeTab === 'posts'">
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          @deleted="onPostDeleted"
        />
        <EmptyState
          v-if="!posts.length && !loading"
          icon="Document"
          description="还没有发布任何动态"
        />
      </template>

      <template v-else>
        <UserCard
          v-for="item in relations"
          :key="item.id"
          :id="item.id"
          :username="item.username"
          :nickname="item.nickname"
          :avatar="item.avatar"
          :bio="item.bio"
          :followed-by-me="item.followedByMe"
        />
        <EmptyState
          v-if="!relations.length && !loading"
          icon="UserFilled"
          :description="activeTab === 'following' ? '还没有关注任何人' : '还没有粉丝'"
        />
      </template>

      <div v-if="hasNext" class="load-more">
        <el-button :loading="loading" round @click="loadMore">加载更多</el-button>
      </div>

      <ReportDialog v-model="reportVisible" :target-type="1" :target-id="profile.id" />
    </template>
  </div>
</template>

<style scoped>
.profile-page__skeleton {
  padding: 24px;
}

.hero {
  padding: 24px 26px 6px;
  margin-bottom: 14px;
}

.hero__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.hero__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hero__info {
  margin-top: 14px;
}

.hero__name {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.hero__name h1 {
  margin: 0;
  font-size: 21px;
  letter-spacing: -0.02em;
}

.hero__username {
  font-size: 13px;
  color: var(--miqu-text-tertiary);
}

.hero__bio {
  margin: 8px 0 0;
  color: var(--miqu-text-secondary);
  line-height: 1.7;
  max-width: 620px;
}

.hero__meta {
  display: flex;
  gap: 14px;
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

.hero__stats {
  display: flex;
  gap: 28px;
  margin-top: 18px;
  padding: 14px 0 16px;
  border-top: 1px solid var(--miqu-border);
}

.hero__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
}

.hero__stat strong {
  font-size: 17px;
  color: var(--miqu-text);
}

.hero__stat span {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.hero__stat:hover span {
  color: var(--miqu-primary);
}

.tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  margin-bottom: 16px;
}

.tabs__item {
  padding: 7px 18px;
  border: none;
  background: transparent;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.tabs__item:hover {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.tabs__item.is-active {
  background: var(--miqu-primary-soft);
  color: var(--miqu-primary);
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 12px 0;
}
</style>
