<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

/**
 * 图片是否加载失败。
 *
 * {#if src} 只能判断"有没有配头像"，判断不了"这张图能不能取到"——
 * 外链图床被网络策略拦截、文件被清理、路径失效时，地址非空但内容取不到，
 * 结果就是一枚永久破图（既不显示首字母占位，也不会自愈）。
 *
 * 这里是全站头像的唯一出口（导航栏 / 动态 / 评论 / 私信 / 通知 / 后台共 15 处），
 * 所以兜底只要做在这一层，全站同时生效。
 */
const failed = ref(false)

/** 头像地址变了就重试——否则换成新头像后仍会停留在上一次的失败态。 */
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)

const showImage = computed(() => Boolean(props.src) && !failed.value)

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
    v-if="showImage"
    :src="src!"
    :alt="nickname || '头像'"
    class="miqu-avatar"
    :class="{ 'is-clickable': !!userId }"
    :style="boxStyle"
    @click="onClick"
    @error="failed = true"
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
