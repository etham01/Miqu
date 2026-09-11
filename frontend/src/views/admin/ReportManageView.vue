<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminReport } from '@/api/types'
import {
  REPORT_REASON_LABELS,
  REPORT_STATUS_LABELS,
  TARGET_TYPE_LABELS,
} from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()

const list = ref<AdminReport[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  status: route.query.status !== undefined ? Number(route.query.status) : undefined,
  page: 1,
  size: 10,
})

const handleVisible = ref(false)
const submitting = ref(false)
const current = ref<AdminReport | null>(null)

const handleForm = reactive({
  status: 1,
  handleRemark: '',
  action: 'NONE',
})

/**
 * 可选的处置动作由举报目标类型决定。
 * 后端也会校验（动作与目标类型不匹配返回 400），前端先过滤一遍避免用户白填。
 */
const actionOptions = computed(() => {
  const options = [{ value: 'NONE', label: '不处置（仅记录结论）' }]
  if (!current.value) return options
  switch (current.value.targetType) {
    case 2:
      options.push({ value: 'DELETE_POST', label: '删除被举报的动态' })
      break
    case 3:
      options.push({ value: 'DELETE_COMMENT', label: '删除被举报的评论' })
      break
    case 1:
      options.push({ value: 'DISABLE_USER', label: '禁用被举报的用户' })
      break
    default:
      break
  }
  return options
})

const isReject = computed(() => handleForm.status === 2)

async function load() {
  loading.value = true
  try {
    const result = await adminApi.reports({
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

function onFilterChange() {
  query.page = 1
  load()
}

function openHandle(row: AdminReport) {
  current.value = row
  handleForm.status = 1
  handleForm.handleRemark = ''
  handleForm.action = 'NONE'
  handleVisible.value = true
}

async function submitHandle() {
  if (!current.value) return

  // 驳回与处置动作互斥，后端也会拦（400），这里提前纠正而不是让用户吃一个报错
  const action = isReject.value ? 'NONE' : handleForm.action

  try {
    await ElMessageBox.confirm(
      isReject.value
        ? '将判定该举报未违规并驳回，确定吗？'
        : `将标记为已处理${action !== 'NONE' ? '，并同时执行处置动作' : ''}，此操作不可撤销。确定吗？`,
      '处理举报',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  submitting.value = true
  try {
    await adminApi.handleReport(current.value.id, {
      status: handleForm.status,
      handleRemark: handleForm.handleRemark.trim(),
      action,
    })
    ElMessage.success('处理完成')
    handleVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function statusTagType(status: number): 'warning' | 'success' | 'info' {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div class="manage">
    <div class="manage__toolbar miqu-card">
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        clearable
        style="width: 150px"
        @change="onFilterChange"
      >
        <el-option label="待处理" :value="0" />
        <el-option label="已处理" :value="1" />
        <el-option label="已驳回" :value="2" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <span class="manage__total">共 {{ total }} 条举报</span>
    </div>

    <div class="miqu-card">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />

        <el-table-column label="被举报对象" min-width="280">
          <template #default="{ row }">
            <div class="report__target">
              <el-tag size="small" type="info" round>
                {{ TARGET_TYPE_LABELS[row.targetType] ?? '未知' }}
              </el-tag>
              <span class="report__preview">
                {{ row.targetPreview }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="举报原因" width="140">
          <template #default="{ row }">
            {{ REPORT_REASON_LABELS[row.reasonType] ?? row.reasonType }}
          </template>
        </el-table-column>

        <el-table-column label="举报人" width="160">
          <template #default="{ row }">
            <RouterLink :to="`/users/${row.reporter.id}`" class="report__user">
              <UserAvatar
                :src="row.reporter.avatar"
                :nickname="row.reporter.nickname"
                :size="26"
              />
              <span>{{ row.reporter.nickname }}</span>
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" round>
              {{ REPORT_STATUS_LABELS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="举报时间" width="170" />

        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              size="small"
              type="primary"
              :disabled="row.status !== 0"
              @click="openHandle(row)"
            >
              {{ row.status === 0 ? '处理' : '已处理' }}
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState icon="Warning" description="没有符合条件的举报" />
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

    <el-dialog v-model="handleVisible" title="处理举报" width="520px">
      <div v-if="current" class="handle">
        <div class="handle__target">
          <el-tag size="small" type="info" round>
            {{ TARGET_TYPE_LABELS[current.targetType] }}
          </el-tag>
          <span>{{ current.targetPreview }}</span>
        </div>

        <div class="handle__reason">
          <strong>{{ REPORT_REASON_LABELS[current.reasonType] }}</strong>
          <p v-if="current.reasonDetail">{{ current.reasonDetail }}</p>
        </div>

        <el-form label-position="top">
          <el-form-item label="处理结论">
            <el-radio-group v-model="handleForm.status">
              <el-radio :value="1">违规成立（已处理）</el-radio>
              <el-radio :value="2">未违规（驳回）</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="!isReject" label="同时执行处置">
            <el-select v-model="handleForm.action" style="width: 100%">
              <el-option
                v-for="option in actionOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>

          <el-alert
            v-else
            type="info"
            :closable="false"
            show-icon
            title="驳回表示判定未违规，因此不能同时删除内容"
            class="handle__alert"
          />

          <el-form-item label="处理备注">
            <el-input
              v-model="handleForm.handleRemark"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              resize="none"
              placeholder="记录处理依据，便于后续追溯"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">
          确认处理
        </el-button>
      </template>
    </el-dialog>
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

.report__target {
  display: flex;
  align-items: center;
  gap: 8px;
}

.report__preview {
  color: var(--miqu-text-secondary);
  line-height: 1.5;
}

.report__user {
  display: flex;
  align-items: center;
  gap: 7px;
}

.manage__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}

.handle__target {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--miqu-radius-sm);
  background: var(--miqu-bg);
  line-height: 1.5;
}

.handle__reason {
  margin: 12px 0 16px;
}

.handle__reason p {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--miqu-text-secondary);
}

.handle__alert {
  margin-bottom: 18px;
}
</style>
