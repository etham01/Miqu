<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/admin'
import type { AdminPost } from '@/api/types'
import { truncate } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import EmptyState from '@/components/EmptyState.vue'

const list = ref<AdminPost[]>([])
const loading = ref(false)
const total = ref(0)
const detail = ref<AdminPost | null>(null)
const detailVisible = ref(false)

const query = reactive({
  keyword: '',
  page: 1,
  size: 10,
})

async function load() {
  loading.value = true
  try {
    const result = await adminApi.posts({
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
  query.keyword = ''
  query.page = 1
  load()
}

function openDetail(row: AdminPost) {
  detail.value = row
  detailVisible.value = true
}

async function remove(row: AdminPost) {
  try {
    await ElMessageBox.confirm(
      '删除后该动态及其评论、图片、点赞都会被清理，且不可恢复。确定删除吗？',
      '删除动态',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  await adminApi.deletePost(row.id)
  ElMessage.success('已删除')
  detailVisible.value = false
  load()
}

onMounted(load)
</script>

<template>
  <div class="manage">
    <div class="manage__toolbar miqu-card">
      <el-input
        v-model="query.keyword"
        placeholder="搜索动态内容"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <span class="manage__total">共 {{ total }} 条动态</span>
    </div>

    <div class="miqu-card">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />

        <el-table-column label="作者" min-width="180">
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

        <el-table-column label="内容" min-width="280">
          <template #default="{ row }">
            <span class="manage__content">
              {{ truncate(row.content, 60) || '（纯图片动态）' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <span class="manage__muted">{{ row.images.length }} 张</span>
          </template>
        </el-table-column>

        <el-table-column label="互动" width="140">
          <template #default="{ row }">
            <span class="manage__muted">
              {{ row.likeCount }} 赞 · {{ row.commentCount }} 评
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="发布时间" width="170" />

        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openDetail(row)">查看</el-button>
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState icon="Document" description="没有找到符合条件的动态" />
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

    <el-dialog v-model="detailVisible" title="动态详情" width="600px">
      <div v-if="detail" class="detail">
        <div class="detail__author">
          <UserAvatar
            :src="detail.author.avatar"
            :nickname="detail.author.nickname"
            :size="40"
          />
          <div>
            <strong>{{ detail.author.nickname }}</strong>
            <small>@{{ detail.author.username }} · {{ detail.createTime }}</small>
          </div>
        </div>

        <p class="detail__content">{{ detail.content || '（无文本内容）' }}</p>

        <div v-if="detail.images.length" class="detail__images">
          <el-image
            v-for="(url, index) in detail.images"
            :key="index"
            :src="url"
            :preview-src-list="detail.images"
            :initial-index="index"
            fit="cover"
            preview-teleported
          />
        </div>

        <div class="detail__stats">
          <span>{{ detail.likeCount }} 点赞</span>
          <span>{{ detail.commentCount }} 评论</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="danger" @click="detail && remove(detail)">删除动态</el-button>
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

.manage__muted {
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

.manage__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
}

.detail__author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail__author small {
  display: block;
  color: var(--miqu-text-tertiary);
  font-size: 12px;
}

.detail__content {
  margin: 14px 0 0;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail__images {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
}

.detail__images :deep(.el-image) {
  width: 100%;
  aspect-ratio: 1 / 1;
  border-radius: var(--miqu-radius-sm);
  overflow: hidden;
}

.detail__stats {
  display: flex;
  gap: 16px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--miqu-border);
  font-size: 13px;
  color: var(--miqu-text-secondary);
}
</style>
