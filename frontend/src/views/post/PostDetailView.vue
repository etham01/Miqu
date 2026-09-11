<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { postApi } from '@/api/post'
import { useUserStore } from '@/stores/user'
import type { Post } from '@/api/types'
import PostCard from '@/components/PostCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = computed(() => route.params.id as string)
const post = ref<Post | null>(null)
const loading = ref(false)
const notFound = ref(false)

async function load() {
  loading.value = true
  notFound.value = false
  try {
    post.value = await postApi.detail(postId.value)
  } catch (error) {
    // 动态可能已被作者或管理员删除；通知里点进来时这种情况很常见
    if ((error as { code?: number }).code === 404) {
      notFound.value = true
    }
  } finally {
    loading.value = false
  }
}

function onDeleted() {
  post.value = null
  notFound.value = true
}

watch(postId, load)
onMounted(load)
</script>

<template>
  <div class="post-detail">
    <el-button text class="post-detail__back" @click="router.back()">
      <el-icon><ArrowLeft /></el-icon> 返回
    </el-button>

    <div v-if="loading" class="miqu-card post-detail__skeleton">
      <el-skeleton animated :rows="4" />
    </div>

    <div v-else-if="notFound || !post" class="miqu-card">
      <EmptyState icon="Document" description="这条动态不存在或已被删除">
        <el-button @click="router.push({ name: 'home' })">回到首页</el-button>
      </EmptyState>
    </div>

    <PostCard v-else :post="post" @deleted="onDeleted" />

    <p v-if="!userStore.isLogin && post" class="post-detail__hint">
      <RouterLink to="/login" class="post-detail__link">登录</RouterLink> 后可以点赞和评论
    </p>
  </div>
</template>

<style scoped>
.post-detail__back {
  margin-bottom: 10px;
  color: var(--miqu-text-secondary);
}

.post-detail__skeleton {
  padding: 20px;
}

.post-detail__hint {
  margin: 14px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--miqu-text-tertiary);
}

.post-detail__link {
  color: var(--miqu-primary);
  font-weight: 500;
}
</style>
