<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import type { AdminUser } from '@/api/types'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore()

const list = ref<AdminUser[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  keyword: '',
  status: undefined as number | undefined,
  page: 1,
  size: 10,
})

async function load() {
  loading.value = true
  try {
    const result = await adminApi.users({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      page: query.page,
      size: query.size,
    })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.status = undefined
  query.page = 1
  load()
}

function onPageChange(page: number) {
  query.page = page
  load()
}

/** 不能操作自己、不能禁用管理员 —— 后端也会拦，前端先给出明确反馈。 */
function canToggle(row: AdminUser): boolean {
  if (row.id === userStore.user?.id) return false
  return row.role !== 2
}

function toggleDisabledReason(row: AdminUser): string {
  if (row.id === userStore.user?.id) return '不能对自己执行该操作'
  if (row.role === 2) return '不能禁用管理员账号'
  return ''
}

async function toggleStatus(row: AdminUser) {
  const nextStatus = row.status === 1 ? 0 : 1
  const actionText = nextStatus === 0 ? '禁用' : '解禁'

  try {
    await ElMessageBox.confirm(
      nextStatus === 0
        ? `禁用后「${row.nickname}」将立即无法登录，且已签发的 Token 会马上失效。确定继续吗？`
        : `确定要解禁「${row.nickname}」吗？`,
      `${actionText}用户`,
      { confirmButtonText: actionText, cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  await adminApi.updateUserStatus(row.id, nextStatus)
  row.status = nextStatus
  ElMessage.success(`已${actionText}`)
}

onMounted(load)
</script>

<template>
  <div class="manage">
    <div class="manage__toolbar miqu-card">
      <el-input
        v-model="query.keyword"
        placeholder="搜索用户名或昵称"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="0" />
      </el-select>

      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>

      <span class="manage__total">共 {{ total }} 位用户</span>
    </div>

    <div class="miqu-card">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <RouterLink :to="`/users/${row.id}`" class="manage__user">
              <UserAvatar :src="row.avatar" :nickname="row.nickname" :size="36" />
              <div class="manage__user-text">
                <strong>{{ row.nickname }}</strong>
                <small>@{{ row.username }}</small>
              </div>
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="邮箱" min-width="190" />

        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 2 ? 'warning' : 'info'" size="small" round>
              {{ row.role === 2 ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" round>
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="数据" width="180">
          <template #default="{ row }">
            <span class="manage__counts">
              {{ row.postCount }} 动态 · {{ row.followerCount }} 粉丝
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="注册时间" width="170" />

        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-tooltip
              :content="toggleDisabledReason(row)"
              :disabled="canToggle(row)"
              placement="top"
            >
              <span>
                <el-button
                  text
                  size="small"
                  :type="row.status === 1 ? 'danger' : 'primary'"
                  :disabled="!canToggle(row)"
                  @click="toggleStatus(row)"
                >
                  {{ row.status === 1 ? '禁用' : '解禁' }}
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState icon="UserFilled" description="没有找到符合条件的用户" />
        </template>
      </el-table>

      <div class="manage__pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          background
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.manage__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.manage__total {
  margin-left: auto;
  font-size: 13px;
  color: var(--miqu-text-tertiary);
}

.manage__user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.manage__user-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.manage__user-text small {
  color: var(--miqu-text-tertiary);
  font-size: 12px;
}

.manage__counts {
  font-size: 12.5px;
  color: var(--miqu-text-secondary);
}

.manage__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}
</style>
