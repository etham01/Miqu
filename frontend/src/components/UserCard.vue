<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useUserStore } from '@/stores/user'
import FollowButton from '@/components/FollowButton.vue'
import UserAvatar from '@/components/UserAvatar.vue'

const props = defineProps<{
  id: string
  username: string
  nickname: string
  avatar: string
  bio: string
  /** 粉丝数，搜索结果里展示 */
  followerCount?: number
  followedByMe: boolean
}>()

/** 关注状态变化需要通知父组件，否则搜索结果里的粉丝数不会跟着变。 */
const emit = defineEmits<{
  (e: 'follow-change', payload: { id: string; following: boolean }): void
}>()

const userStore = useUserStore()
const following = ref(props.followedByMe)

const isSelf = computed(() => userStore.user?.id === props.id)
const showFollowButton = computed(() => userStore.isLogin && !isSelf.value)

function onFollowChange(payload: { following: boolean; followerCount: number }) {
  following.value = payload.following
  emit('follow-change', { id: props.id, following: payload.following })
}
</script>

<template>
  <div class="user-card miqu-card miqu-card--hoverable">
    <RouterLink :to="`/users/${id}`" class="user-card__main">
      <UserAvatar :src="avatar" :nickname="nickname" :size="48" />
      <div class="user-card__text">
        <div class="user-card__title">
          <span class="user-card__nickname">{{ nickname }}</span>
          <span class="user-card__username">@{{ username }}</span>
        </div>
        <p class="user-card__bio miqu-clamp-2">{{ bio || '这个人很神秘，什么都没留下' }}</p>
        <span v-if="followerCount !== undefined" class="user-card__stat">
          {{ followerCount }} 粉丝
        </span>
      </div>
    </RouterLink>

    <div v-if="showFollowButton" class="user-card__action">
      <FollowButton
        :user-id="id"
        :following="following"
        size="small"
        @change="onFollowChange"
      />
    </div>
  </div>
</template>

<style scoped>
.user-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  margin-bottom: 10px;
}

.user-card__main {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.user-card__text {
  min-width: 0;
}

.user-card__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.user-card__nickname {
  font-weight: 600;
}

.user-card__username {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.user-card__bio {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--miqu-text-secondary);
  line-height: 1.5;
}

.user-card__stat {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.user-card__action {
  flex-shrink: 0;
}
</style>
