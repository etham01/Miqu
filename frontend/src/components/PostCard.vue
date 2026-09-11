<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postApi } from '@/api/post'
import { useUserStore } from '@/stores/user'
import { relativeTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import CommentList from '@/components/CommentList.vue'
import ReportDialog from '@/components/ReportDialog.vue'

const props = defineProps<{ post: import('@/api/types').Post }>()
const emit = defineEmits<{ (e: 'deleted', id: string): void }>()

const userStore = useUserStore()

const liked = ref(props.post.likedByMe)
const likeCount = ref(props.post.likeCount)
const commentCount = ref(props.post.commentCount)
const likePending = ref(false)
const showComments = ref(false)
const reportVisible = ref(false)

/** 1 张图单独排版，2~4 张走两列，5~9 张走三列。 */
const gridClass = computed(() => {
  const count = props.post.images.length
  if (count === 1) return 'grid--single'
  if (count <= 4) return 'grid--double'
  return 'grid--triple'
})

/**
 * 点赞按钮在请求进行中必须禁用。
 *
 * 取消点赞在"本来就没点赞"时返回 404，连点两次会弹出错误提示；
 * 这里用 pending 标记挡住重复点击，是需求里那条 404 约定的必要配套。
 */
async function toggleLike() {
  if (likePending.value) return
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  likePending.value = true
  try {
    const result = liked.value
      ? await postApi.unlike(props.post.id)
      : await postApi.like(props.post.id)
    liked.value = result.liked
    likeCount.value = result.likeCount
  } catch (error) {
    const code = (error as { code?: number }).code
    // 本地状态与服务端不一致时以服务端返回为准
    if (code === 409) {
      liked.value = true
      ElMessage.info('已经点过赞了')
    } else if (code === 404) {
      liked.value = false
      ElMessage.info('尚未点赞')
    }
  } finally {
    likePending.value = false
  }
}

async function removePost() {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除这条动态吗？', '删除动态', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await postApi.remove(props.post.id)
  ElMessage.success('已删除')
  emit('deleted', props.post.id)
}

function onCommentCountChange(delta: number) {
  commentCount.value = Math.max(0, commentCount.value + delta)
}
</script>

<template>
  <article class="post miqu-card miqu-card--hoverable">
    <header class="post__head">
      <RouterLink :to="`/users/${post.author.id}`" class="post__author">
        <UserAvatar
          :src="post.author.avatar"
          :nickname="post.author.nickname"
          :size="42"
        />
        <div class="post__author-text">
          <span class="post__nickname">{{ post.author.nickname }}</span>
          <span class="post__meta">@{{ post.author.username }} · {{ relativeTime(post.createTime) }}</span>
        </div>
      </RouterLink>

      <el-dropdown trigger="click" @command="(c: string) => c === 'delete' ? removePost() : (reportVisible = true)">
        <el-button text circle class="post__more">
          <el-icon><MoreFilled /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="post.mine || userStore.isAdmin" command="delete">
              <el-icon><Delete /></el-icon> 删除
            </el-dropdown-item>
            <el-dropdown-item v-if="!post.mine && userStore.isLogin" command="report">
              <el-icon><Warning /></el-icon> 举报
            </el-dropdown-item>
            <el-dropdown-item v-if="post.mine" disabled>这是你发布的动态</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <p v-if="post.content" class="post__content">{{ post.content }}</p>

    <div v-if="post.images.length" class="post__images" :class="gridClass">
      <el-image
        v-for="(url, index) in post.images"
        :key="index"
        :src="url"
        :preview-src-list="post.images"
        :initial-index="index"
        fit="cover"
        loading="lazy"
        class="post__image"
        preview-teleported
      />
    </div>

    <footer class="post__actions">
      <button
        class="post__action"
        :class="{ 'is-active': liked }"
        :disabled="likePending"
        @click="toggleLike"
      >
        <el-icon><component :is="liked ? 'StarFilled' : 'Star'" /></el-icon>
        <span>{{ likeCount || '点赞' }}</span>
      </button>

      <button
        class="post__action"
        :class="{ 'is-active': showComments }"
        @click="showComments = !showComments"
      >
        <el-icon><ChatDotRound /></el-icon>
        <span>{{ commentCount || '评论' }}</span>
      </button>
    </footer>

    <transition name="fade-slide">
      <CommentList
        v-if="showComments"
        :post-id="post.id"
        @count-change="onCommentCountChange"
      />
    </transition>

    <ReportDialog v-model="reportVisible" :target-type="2" :target-id="post.id" />
  </article>
</template>

<style scoped>
.post {
  padding: 18px 20px 12px;
  margin-bottom: 14px;
}

.post__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.post__author {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.post__author-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.4;
}

.post__nickname {
  font-weight: 600;
  color: var(--miqu-text);
}

.post__meta {
  color: var(--miqu-text-tertiary);
  font-size: 12px;
}

.post__more {
  color: var(--miqu-text-tertiary);
}

.post__content {
  margin: 12px 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.75;
}

.post__images {
  margin-top: 12px;
  display: grid;
  gap: 6px;
}

.grid--single {
  grid-template-columns: minmax(0, 340px);
}

.grid--double {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.grid--triple {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.post__image {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: var(--miqu-radius);
  overflow: hidden;
  cursor: zoom-in;
  background: var(--miqu-border);
}

.grid--single .post__image {
  aspect-ratio: 4 / 3;
}

.post__actions {
  display: flex;
  gap: 4px;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid var(--miqu-border);
}

.post__action {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: none;
  background: transparent;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.post__action:hover:not(:disabled) {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.post__action:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.post__action.is-active {
  color: var(--miqu-primary);
}

.post__action.is-active:first-child {
  color: var(--miqu-accent);
}
</style>
