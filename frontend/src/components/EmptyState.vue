<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
    /** Element Plus 图标组件名，已全局注册 */
    icon?: string
  }>(),
  { description: '这里还什么都没有', icon: 'Files' },
)
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon">
      <el-icon :size="34"><component :is="icon" /></el-icon>
    </div>
    <p class="empty-state__text">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 56px 20px;
  text-align: center;
}

.empty-state__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.empty-state__text {
  margin: 0;
  color: var(--miqu-text-tertiary);
  font-size: 14px;
}
</style>
