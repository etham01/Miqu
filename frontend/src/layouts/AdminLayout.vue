<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => (route.name as string) ?? 'admin-dashboard')

const title = computed(() => (route.meta.title as string) ?? '管理后台')
</script>

<template>
  <div class="admin">
    <aside class="admin__sidebar">
      <div class="admin__brand">
        <span class="admin__brand-mark">M</span>
        <div class="admin__brand-text">
          <strong>Miqu</strong>
          <small>管理后台</small>
        </div>
      </div>

      <el-menu :default-active="activeMenu" class="admin__menu" router>
        <el-menu-item index="admin-dashboard" :route="{ name: 'admin-dashboard' }">
          <el-icon><DataLine /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="admin-users" :route="{ name: 'admin-users' }">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="admin-posts" :route="{ name: 'admin-posts' }">
          <el-icon><Document /></el-icon>
          <span>动态管理</span>
        </el-menu-item>
        <el-menu-item index="admin-comments" :route="{ name: 'admin-comments' }">
          <el-icon><ChatLineSquare /></el-icon>
          <span>评论管理</span>
        </el-menu-item>
        <el-menu-item index="admin-reports" :route="{ name: 'admin-reports' }">
          <el-icon><Warning /></el-icon>
          <span>举报管理</span>
        </el-menu-item>
        <el-menu-item index="admin-logs" :route="{ name: 'admin-logs' }">
          <el-icon><Tickets /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <div class="admin__body">
      <header class="admin__header">
        <h1 class="admin__title">{{ title }}</h1>
        <div class="admin__header-right">
          <el-button text @click="router.push({ name: 'home' })">
            <el-icon><Back /></el-icon> 返回前台
          </el-button>
          <div class="admin__user">
            <UserAvatar
              :src="userStore.user?.avatar"
              :nickname="userStore.displayName"
              :size="30"
            />
            <span>{{ userStore.displayName }}</span>
          </div>
        </div>
      </header>

      <main class="admin__content">
        <RouterView v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: var(--miqu-bg);
}

.admin__sidebar {
  width: 212px;
  flex-shrink: 0;
  background: var(--miqu-surface);
  border-right: 1px solid var(--miqu-border);
  display: flex;
  flex-direction: column;
}

.admin__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--miqu-border);
}

.admin__brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--miqu-primary);
  color: #fff;
  font-weight: 700;
}

.admin__brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.admin__brand-text small {
  color: var(--miqu-text-tertiary);
  font-size: 11px;
}

.admin__menu {
  border-right: none;
  padding: 10px;
}

.admin__menu :deep(.el-menu-item) {
  border-radius: var(--miqu-radius-sm);
  margin-bottom: 2px;
  height: 42px;
}

.admin__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.admin__header {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--miqu-surface);
  border-bottom: 1px solid var(--miqu-border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.admin__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

.admin__header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.admin__user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--miqu-text-secondary);
}

.admin__content {
  flex: 1;
  padding: 20px 24px 40px;
  min-width: 0;
}
</style>
