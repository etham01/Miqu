<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commentApi } from '@/api/post'
import { useUserStore } from '@/stores/user'
import type { Comment } from '@/api/types'
import { relativeTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const props = defineProps<{ postId: string }>()
const emit = defineEmits<{ (e: 'count-change', delta: number): void }>()

const userStore = useUserStore()

const comments = ref<Comment[]>([])
const loading = ref(false)
const submitting = ref(false)
const total = ref(0)
const page = ref(1)
const size = 10
const hasNext = ref(false)
const draft = ref('')

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) page.value = 1
    const result = await commentApi.list(props.postId, page.value, size)
    comments.value = reset ? result.list : [...comments.value, ...result.list]
    total.value = result.total
    hasNext.value = result.hasNext
  } finally {
    loading.value = false
  }
}

function loadMore() {
  page.value += 1
  load()
}

async function submit() {
  const content = draft.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const created = await commentApi.create(props.postId, content)
    draft.value = ''
    // 评论列表按时间正序，新评论追加到末尾
    comments.value = [...comments.value, created]
    total.value += 1
    emit('count-change', 1)
  } finally {
    submitting.value = false
  }
}

async function remove(comment: Comment) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除评论', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await commentApi.remove(comment.id)
  comments.value = comments.value.filter((item) => item.id !== comment.id)
  total.value -= 1
  emit('count-change', -1)
  ElMessage.success('已删除')
}

onMounted(() => load(true))
</script>

<template>
  <section class="comments">
    <div v-if="loading && !comments.length" class="comments__loading">
      <el-skeleton :rows="2" animated />
    </div>

    <EmptyState
      v-else-if="!comments.length"
      icon="ChatLineSquare"
      description="还没有评论，来说点什么吧"
    />

    <ul v-else class="comments__list">
      <li v-for="comment in comments" :key="comment.id" class="comments__item">
        <RouterLink :to="`/users/${comment.author.id}`">
          <UserAvatar
            :src="comment.author.avatar"
            :nickname="comment.author.nickname"
            :size="32"
          />
        </RouterLink>

        <div class="comments__body">
          <div class="comments__meta">
            <span class="comments__nickname">{{ comment.author.nickname }}</span>
            <span class="comments__time">{{ relativeTime(comment.createTime) }}</span>
          </div>
          <p class="comments__text">{{ comment.content }}</p>
        </div>

        <el-button
          v-if="comment.mine || userStore.isAdmin"
          text
          size="small"
          class="comments__delete"
          @click="remove(comment)"
        >
          删除
        </el-button>
      </li>
    </ul>

    <div v-if="hasNext" class="comments__more">
      <el-button text size="small" :loading="loading" @click="loadMore">
        查看更多评论（{{ total }}）
      </el-button>
    </div>

    <div v-if="userStore.isLogin" class="comments__editor">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        resize="none"
        placeholder="友善地表达你的看法…"
        @keydown.ctrl.enter="submit"
      />
      <div class="comments__editor-actions">
        <span class="comments__hint">Ctrl + Enter 发送</span>
        <el-button type="primary" size="small" :loading="submitting" @click="submit">
          发表
        </el-button>
      </div>
    </div>

    <p v-else class="comments__login-hint">
      <RouterLink to="/login" class="comments__login-link">登录</RouterLink> 后可以参与讨论
    </p>
  </section>
</template>

<style scoped>
.comments {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--miqu-border-strong);
}

.comments__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comments__item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.comments__body {
  flex: 1;
  min-width: 0;
}

.comments__meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.comments__nickname {
  font-weight: 600;
  font-size: 13px;
}

.comments__time {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.comments__text {
  margin: 2px 0 0;
  font-size: 13.5px;
  line-height: 1.65;
  color: var(--miqu-text-secondary);
  word-break: break-word;
  white-space: pre-wrap;
}

.comments__delete {
  flex-shrink: 0;
  color: var(--miqu-text-tertiary);
}

.comments__more {
  margin-top: 10px;
  text-align: center;
}

.comments__editor {
  margin-top: 16px;
}

.comments__editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.comments__hint {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.comments__login-hint {
  margin: 16px 0 0;
  font-size: 13px;
  color: var(--miqu-text-tertiary);
  text-align: center;
}

.comments__login-link {
  color: var(--miqu-primary);
  font-weight: 500;
}

.comments__loading {
  padding: 8px 0;
}
</style>
