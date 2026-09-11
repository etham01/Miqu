<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/user'

const props = withDefaults(
  defineProps<{
    userId: string
    following: boolean
    /** 互相关注时展示不同文案 */
    mutual?: boolean
    size?: 'small' | 'default'
  }>(),
  { mutual: false, size: 'default' },
)

const emit = defineEmits<{
  (e: 'change', payload: { following: boolean; followerCount: number }): void
}>()

const following = ref(props.following)
const loading = ref(false)

const label = computed(() => {
  if (!following.value) return '关注'
  return props.mutual ? '互相关注' : '已关注'
})

/**
 * 请求进行中必须禁用按钮。
 *
 * 这不是可选的体验优化：取消关注/点赞在"本来就没关注"时会返回 404，
 * 用户连点两次就会弹出一个"尚未关注该用户"的错误提示。
 */
async function toggle() {
  if (loading.value) return
  loading.value = true
  try {
    const result = following.value
      ? await userApi.unfollow(props.userId)
      : await userApi.follow(props.userId)
    following.value = result.following
    ElMessage.success(result.following ? '已关注' : '已取消关注')
    emit('change', { following: result.following, followerCount: result.followerCount })
  } catch (error) {
    // 409 已经关注 / 404 尚未关注：说明本地状态与后端不一致，以服务端为准刷新
    const code = (error as { code?: number }).code
    if (code === 409) {
      following.value = true
      ElMessage.info('已经关注过了')
    } else if (code === 404) {
      following.value = false
      ElMessage.info('尚未关注该用户')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-button
    :type="following ? 'default' : 'primary'"
    :size="size"
    :loading="loading"
    :disabled="loading"
    round
    @click.stop.prevent="toggle"
  >
    {{ label }}
  </el-button>
</template>
