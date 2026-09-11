<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { adminApi } from '@/api/admin'
import type { AdminOperationLog } from '@/api/types'
import { TARGET_TYPE_LABELS } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref<AdminOperationLog[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  operationType: '',
  page: 1,
  size: 10,
})

const operationTypes = [
  { value: 'DISABLE_USER', label: '禁用用户' },
  { value: 'ENABLE_USER', label: '解禁用户' },
  { value: 'DELETE_POST', label: '删除动态' },
  { value: 'DELETE_COMMENT', label: '删除评论' },
  { value: 'HANDLE_REPORT', label: '处理举报' },
]

const OPERATION_LABELS: Record<string, string> = Object.fromEntries(
  operationTypes.map((item) => [item.value, item.label]),
)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.logs({
      operationType: query.operationType || undefined,
      page: query.page,
      size: query.size,
    })
    list.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="manage">
    <div class="manage__toolbar miqu-card">
      <el-select
        v-model="query.operationType"
        placeholder="全部操作类型"
        clearable
        style="width: 180px"
        @change="onFilterChange"
      >
        <el-option
          v-for="item in operationTypes"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <span class="manage__total">共 {{ total }} 条记录</span>
    </div>

    <div class="miqu-card">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />

        <el-table-column label="操作人" width="170">
          <template #default="{ row }">
            <RouterLink :to="`/users/${row.admin.id}`" class="log__admin">
              <UserAvatar
                :src="row.admin.avatar"
                :nickname="row.admin.nickname"
                :size="26"
              />
              <span>{{ row.admin.nickname }}</span>
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column label="操作类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" round>
              {{ OPERATION_LABELS[row.operationType] ?? row.operationType }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="目标" width="150">
          <template #default="{ row }">
            <span class="log__target">
              {{ TARGET_TYPE_LABELS[row.targetType] ?? '-' }}
              <template v-if="row.targetId">#{{ row.targetId }}</template>
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="detail" label="摘要" min-width="260" />

        <el-table-column prop="ip" label="IP" width="140" />

        <el-table-column prop="createTime" label="操作时间" width="170" />

        <template #empty>
          <EmptyState icon="Tickets" description="还没有操作记录" />
        </template>
      </el-table>

      <div class="manage__pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :total="total"
          background
          @current-change="
            (page: number) => {
              query.page = page
              load()
            }
          "
        />
      </div>
    </div>

    <p class="manage__note">
      <el-icon><InfoFilled /></el-icon>
      操作日志只读，不提供修改与删除入口；记录中不含密码、Token 等敏感信息。
    </p>
  </div>
</template>

<style scoped>
.manage__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 14px;
}

.manage__total {
  margin-left: auto;
  font-size: 13px;
  color: var(--miqu-text-tertiary);
}

.log__admin {
  display: flex;
  align-items: center;
  gap: 7px;
}

.log__target {
  font-size: 12.5px;
  color: var(--miqu-text-secondary);
}

.manage__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}

.manage__note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 0;
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}
</style>
