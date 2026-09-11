<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import UserAvatar from '@/components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const keyword = ref('')

const activeNav = computed(() => {
  if (route.name === 'search') return 'search'
  if (route.name === 'home') return 'home'
  return ''
})

onMounted(() => {
  if (userStore.isLogin) {
    notificationStore.startPolling()
  }
})

function onSearch() {
  const value = keyword.value.trim()
  if (!value) return
  router.push({ name: 'search', query: { keyword: value } })
}

async function onCommand(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return // 用户取消
    }
    userStore.logout()
    notificationStore.reset()
    notificationStore.stopPolling()
    router.push({ name: 'login' })
    return
  }
  router.push({ name: command })
}
</script>

<template>
  <div class="layout">
    <header class="layout__header">
      <div class="miqu-container layout__header-inner">
        <RouterLink to="/" class="layout__logo">
          <span class="layout__logo-mark">M</span>
          <span class="layout__logo-text">Miqu</span>
        </RouterLink>

        <nav class="layout__nav">
          <RouterLink
            to="/"
            class="layout__nav-item"
            :class="{ 'is-active': activeNav === 'home' }"
          >
            首页
          </RouterLink>
          <RouterLink
            to="/search"
            class="layout__nav-item"
            :class="{ 'is-active': activeNav === 'search' }"
          >
            发现
          </RouterLink>
        </nav>

        <div class="layout__search">
          <el-input
            v-model="keyword"
            placeholder="搜索用户"
            clearable
            @keyup.enter="onSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="layout__actions">
          <template v-if="userStore.isLogin">
            <RouterLink
              to="/messages"
              class="layout__icon-btn"
              :class="{ 'is-active': route.name === 'messages' }"
              title="消息"
            >
              <el-badge
                :value="notificationStore.messageUnread"
                :hidden="!notificationStore.hasMessage"
                :max="99"
              >
                <el-icon :size="19"><ChatDotRound /></el-icon>
              </el-badge>
            </RouterLink>

            <RouterLink
              to="/notifications"
              class="layout__icon-btn"
              :class="{ 'is-active': route.name === 'notifications' }"
              title="通知"
            >
              <el-badge
                :value="notificationStore.unread.total"
                :hidden="!notificationStore.hasNotification"
                :max="99"
              >
                <el-icon :size="19"><Bell /></el-icon>
              </el-badge>
            </RouterLink>

            <el-dropdown trigger="click" @command="onCommand">
              <div class="layout__user">
                <UserAvatar
                  :src="userStore.user?.avatar"
                  :nickname="userStore.displayName"
                  :size="34"
                />
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon> 个人中心
                  </el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" command="admin-dashboard">
                    <el-icon><Setting /></el-icon> 管理后台
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon> 退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>

          <template v-else>
            <el-button text @click="router.push({ name: 'login' })">登录</el-button>
            <el-button type="primary" round @click="router.push({ name: 'register' })">
              注册
            </el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="layout__main">
      <div class="miqu-container">
        <RouterView v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </div>
    </main>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.layout__header {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--miqu-header-height);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--miqu-border);
}

.layout__header-inner {
  height: 100%;
  display: flex;
  align-items: center;
  gap: 24px;
}

.layout__logo {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 700;
  font-size: 17px;
  letter-spacing: -0.02em;
}

.layout__logo-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--miqu-primary);
  color: #fff;
  font-size: 15px;
}

.layout__nav {
  display: flex;
  gap: 4px;
}

.layout__nav-item {
  padding: 6px 12px;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  font-weight: 500;
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.layout__nav-item:hover {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.layout__nav-item.is-active {
  background: var(--miqu-primary-soft);
  color: var(--miqu-primary);
}

.layout__search {
  margin-left: auto;
  width: 240px;
}

.layout__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.layout__icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.layout__icon-btn:hover,
.layout__icon-btn.is-active {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.layout__user {
  cursor: pointer;
  padding-left: 6px;
  outline: none;
}

.layout__main {
  flex: 1;
  padding: 24px 0 56px;
}

/* 窄屏：隐藏导航与搜索框，优先保证核心内容可读 */
@media (max-width: 768px) {
  .layout__nav,
  .layout__search {
    display: none;
  }
}
</style>
