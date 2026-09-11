<script setup lang="ts">
import { computed } from 'vue'
import { avatarText } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    src?: string | null
    nickname?: string | null
    size?: number
    /** 点击是否跳转到该用户主页；不传 userId 时不可点击 */
    userId?: string | null
  }>(),
  { size: 40, src: '', nickname: '', userId: null },
)

/** 从昵称派生一个稳定的底色，让不同用户的默认头像有区分度。 */
const fallbackStyle = computed(() => {
  const text = props.nickname || '?'
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 360
  }
  return {
    width: `${props.size}px`,
    height: `${props.size}px`,
    fontSize: `${Math.round(props.size * 0.42)}px`,
    background: `hsl(${hash}, 62%, 92%)`,
    color: `hsl(${hash}, 48%, 38%)`,
  }
})

const boxStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
}))

function onClick() {
  if (props.userId) {
    window.open(`/users/${props.userId}`, '_blank')
  }
}
</script>

<template>
  <img
    v-if="src"
    :src="src"
    :alt="nickname || '头像'"
    class="miqu-avatar"
    :class="{ 'is-clickable': !!userId }"
    :style="boxStyle"
    @click="onClick"
  />
  <span
    v-else
    class="miqu-avatar miqu-avatar--fallback"
    :class="{ 'is-clickable': !!userId }"
    :style="fallbackStyle"
    @click="onClick"
  >
    {{ avatarText(nickname || '') }}
  </span>
</template>

<style scoped>
.miqu-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  background: var(--miqu-primary-soft);
  user-select: none;
}

.miqu-avatar--fallback {
  font-weight: 600;
  line-height: 1;
}

.miqu-avatar.is-clickable {
  cursor: pointer;
  transition: transform var(--miqu-transition);
}

.miqu-avatar.is-clickable:hover {
  transform: scale(1.06);
}
</style>
