<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '@/api/admin'
import type { AdminStats } from '@/api/types'

const router = useRouter()

const stats = ref<AdminStats | null>(null)
const loading = ref(false)

const cards = [
  { key: 'userTotal', label: '用户总数', icon: 'User', tone: 'blue', todayKey: 'todayNewUser' },
  { key: 'postTotal', label: '动态总数', icon: 'Document', tone: 'violet', todayKey: 'todayNewPost' },
  { key: 'commentTotal', label: '评论总数', icon: 'ChatLineSquare', tone: 'green', todayKey: 'todayNewComment' },
] as const

async function load() {
  loading.value = true
  try {
    stats.value = await adminApi.stats()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="dashboard">
    <div v-if="loading && !stats" class="dashboard__grid">
      <div v-for="n in 3" :key="n" class="miqu-card dashboard__skeleton">
        <el-skeleton animated :rows="2" />
      </div>
    </div>

    <template v-else-if="stats">
      <div class="dashboard__grid">
        <div v-for="card in cards" :key="card.key" class="stat miqu-card">
          <div class="stat__icon" :class="`stat__icon--${card.tone}`">
            <el-icon :size="20"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat__body">
            <span class="stat__label">{{ card.label }}</span>
            <strong class="stat__value">{{ stats[card.key] }}</strong>
            <span class="stat__today">
              今日新增 <b>{{ stats[card.todayKey] }}</b>
            </span>
          </div>
        </div>
      </div>

      <div class="dashboard__row">
        <div
          class="pending miqu-card"
          :class="{ 'is-active': stats.pendingReportTotal > 0 }"
          @click="router.push({ name: 'admin-reports', query: { status: 0 } })"
        >
          <div class="pending__icon">
            <el-icon :size="22"><Warning /></el-icon>
          </div>
          <div class="pending__body">
            <span class="pending__label">待处理举报</span>
            <strong class="pending__value">{{ stats.pendingReportTotal }}</strong>
          </div>
          <el-icon class="pending__arrow"><ArrowRight /></el-icon>
        </div>

        <div class="dashboard__links miqu-card">
          <h3>快捷入口</h3>
          <div class="dashboard__links-list">
            <el-button round @click="router.push({ name: 'admin-users' })">
              <el-icon><User /></el-icon> 用户管理
            </el-button>
            <el-button round @click="router.push({ name: 'admin-posts' })">
              <el-icon><Document /></el-icon> 动态管理
            </el-button>
            <el-button round @click="router.push({ name: 'admin-comments' })">
              <el-icon><ChatLineSquare /></el-icon> 评论管理
            </el-button>
            <el-button round @click="router.push({ name: 'admin-logs' })">
              <el-icon><Tickets /></el-icon> 操作日志
            </el-button>
          </div>
        </div>
      </div>

      <p class="dashboard__tip">
        <el-icon><InfoFilled /></el-icon>
        统计口径：已逻辑删除的用户、动态、评论不计入总数。
      </p>
    </template>
  </div>
</template>

<style scoped>
.dashboard__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.dashboard__skeleton {
  padding: 20px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px 22px;
}

.stat__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: var(--miqu-radius);
  flex-shrink: 0;
}

.stat__icon--blue {
  background: #eaf2ff;
  color: #3b7ddd;
}

.stat__icon--violet {
  background: var(--miqu-primary-soft);
  color: var(--miqu-primary);
}

.stat__icon--green {
  background: #e8f8f0;
  color: #1fa971;
}

.stat__body {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.stat__label {
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

.stat__value {
  font-size: 25px;
  letter-spacing: -0.02em;
}

.stat__today {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.stat__today b {
  color: var(--miqu-primary);
}

.dashboard__row {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
}

.pending {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 22px;
  cursor: pointer;
  transition: box-shadow var(--miqu-transition), transform var(--miqu-transition);
}

.pending:hover {
  box-shadow: var(--miqu-shadow-hover);
  transform: translateY(-1px);
}

.pending__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: var(--miqu-radius);
  background: var(--miqu-bg);
  color: var(--miqu-text-tertiary);
}

.pending.is-active .pending__icon {
  background: #fff1f0;
  color: #e5484d;
}

.pending__body {
  display: flex;
  flex-direction: column;
  flex: 1;
  line-height: 1.35;
}

.pending__label {
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

.pending__value {
  font-size: 25px;
  letter-spacing: -0.02em;
}

.pending.is-active .pending__value {
  color: #e5484d;
}

.pending__arrow {
  color: var(--miqu-text-tertiary);
}

.dashboard__links {
  padding: 18px 22px;
}

.dashboard__links h3 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
}

.dashboard__links-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard__tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 16px 0 0;
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

@media (max-width: 1000px) {
  .dashboard__grid {
    grid-template-columns: 1fr;
  }

  .dashboard__row {
    grid-template-columns: 1fr;
  }
}
</style>
