<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadUserFile } from 'element-plus'
import { postApi } from '@/api/post'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/UserAvatar.vue'

const emit = defineEmits<{ (e: 'created'): void }>()

const userStore = useUserStore()

const content = ref('')
const fileList = ref<UploadUserFile[]>([])
const submitting = ref(false)
const expanded = ref(false)

const MAX_IMAGES = 9

const canSubmit = computed(
  () => (content.value.trim().length > 0 || fileList.value.length > 0) && !submitting.value,
)

function onExceed() {
  ElMessage.warning(`最多只能上传 ${MAX_IMAGES} 张图片`)
}

/**
 * 上传成功后把后端返回的 URL 写回文件项，提交时直接取用。
 *
 * 后端的响应体是统一格式 `{code, message, data}`，且**失败时 HTTP 状态仍是 200**，
 * 所以不能只看 HTTP 层，必须判断 `code`。
 */
function onSuccess(
  response: { code: number; data?: { url: string }; message: string },
  file: UploadFile,
) {
  if (response.code !== 200 || !response.data) {
    file.status = 'fail'
    ElMessage.error(response.message || '上传失败')
    return
  }
  file.url = response.data.url
  file.status = 'success'
}

function onError(_error: Error, file: UploadFile) {
  file.status = 'fail'
  ElMessage.error('上传失败，请重试')
}

async function submit() {
  if (!canSubmit.value) {
    ElMessage.warning('写点什么或配张图吧')
    return
  }

  const images = fileList.value
    .filter((file) => file.status === 'success' && file.url)
    .map((file) => file.url as string)

  if (fileList.value.length > 0 && images.length === 0) {
    ElMessage.warning('图片还没上传完成')
    return
  }

  submitting.value = true
  try {
    await postApi.create({ content: content.value.trim(), images })
    content.value = ''
    fileList.value = []
    expanded.value = false
    ElMessage.success('发布成功')
    emit('created')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="editor miqu-card">
    <div class="editor__head">
      <UserAvatar :src="userStore.user?.avatar" :nickname="userStore.displayName" :size="42" />
      <el-input
        v-model="content"
        type="textarea"
        :rows="expanded ? 4 : 2"
        maxlength="1000"
        show-word-limit
        resize="none"
        placeholder="分享点什么…"
        class="editor__input"
        @focus="expanded = true"
      />
    </div>

    <el-upload
      v-model:file-list="fileList"
      class="editor__upload"
      action="/api/files/image"
      name="file"
      :headers="{
        Authorization: `Bearer ${userStore.token}`,
      }"
      list-type="picture-card"
      accept="image/jpeg,image/png,image/gif,image/webp"
      :limit="MAX_IMAGES"
      :on-exceed="onExceed"
      :on-success="onSuccess"
      :on-error="onError"
    >
      <el-icon><Plus /></el-icon>
    </el-upload>

    <div class="editor__footer">
      <span class="editor__hint">
        图片最多 {{ MAX_IMAGES }} 张、每张不超过 5MB
      </span>
      <el-button
        type="primary"
        round
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >
        发布
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.editor {
  padding: 18px 20px;
  margin-bottom: 16px;
}

.editor__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.editor__input {
  flex: 1;
}

.editor__input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  padding: 6px 0;
  font-size: 15px;
  background: transparent;
}

.editor__input :deep(.el-textarea__inner:focus) {
  box-shadow: none;
}

.editor__upload {
  margin-top: 8px;
}

.editor__upload :deep(.el-upload--picture-card),
.editor__upload :deep(.el-upload-list__item) {
  width: 84px;
  height: 84px;
  border-radius: var(--miqu-radius);
}

.editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--miqu-border);
}

.editor__hint {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}
</style>
