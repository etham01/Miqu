<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminComment } from '@/api/types'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()

const list = ref<AdminComment[]>([])
const loading = ref(false)
const total = ref(0)

const query = reactive({
  postId: (route.query.postId as string) ?? '',
  keyword: '',
  page: 1,
  size: 10,
})

async function load() {
  loading.value = true
  try {
    const result = await adminApi.comments({
      postId: query.postId ? query.postId : undefined,
      keyword: query.keyword.trim() || undefined,
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
  query.postId = ''
  query.keyword = ''
  query.page = 1
  load()
}

async function remove(row: AdminComment) {
  try {
    await ElMessageBox.confirm(
      '删除后该评论不可恢复，动态的评论数会同步减少。确定删除吗？',
      '删除评论',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await adminApi.deleteComment(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="manage">
    <div class="manage__toolbar miqu-card">
      <el-input
        v-model="query.keyword"
        placeholder="搜索评论内容"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-input
        v-model="query.postId"
        placeholder="按动态 ID 过滤"
        clearable
        style="width: 170px"
        @keyup.enter="onSearch"
      />

      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <span class="manage__total">共 {{ total }} 条评论</span>
    </div>

    <div class="miqu-card">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />

        <el-table-column label="评论者" min-width="180">
          <template #default="{ row }">
            <RouterLink :to="`/users/${row.author.id}`" class="manage__author">
              <UserAvatar
                :src="row.author.avatar"
                :nickname="row.author.nickname"
                :size="30"
              />
              <span>{{ row.author.nickname }}</span>
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column label="内容" min-width="300">
          <template #default="{ row }">
            <span class="manage__content">{{ row.content }}</span>
          </template>
        </el-table-column>

        <el-table-column label="所属动态" width="130">
          <template #default="{ row }">
            <RouterLink :to="`/posts/${row.postId}`" class="manage__link">
              动态 #{{ row.postId }}
            </RouterLink>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="评论时间" width="170" />

        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState icon="ChatLineSquare" description="没有找到符合条件的评论" />
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

.manage__author {
  display: flex;
  align-items: center;
  gap: 8px;
}

.manage__content {
  color: var(--miqu-text-secondary);
  line-height: 1.5;
}

.manage__link {
  color: var(--miqu-primary);
  font-size: 12.5px;
}

.manage__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}
</style>
