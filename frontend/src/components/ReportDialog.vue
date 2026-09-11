<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reportApi } from '@/api/report'
import { REPORT_REASON_LABELS } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  /** 1 用户 2 动态 3 评论 */
  targetType: number
  targetId: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submitted'): void
}>()

const reasonType = ref<number>(1)
const reasonDetail = ref('')
const submitting = ref(false)

const reasons = Object.entries(REPORT_REASON_LABELS).map(([value, label]) => ({
  value: Number(value),
  label,
}))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      // 每次打开都重置，避免上一次的草稿串到这一次
      reasonType.value = 1
      reasonDetail.value = ''
    }
  },
)

async function submit() {
  if (submitting.value) return
  submitting.value = true
  try {
    await reportApi.create({
      targetType: props.targetType,
      targetId: props.targetId,
      reasonType: reasonType.value,
      reasonDetail: reasonDetail.value.trim(),
    })
    ElMessage.success('举报已提交，我们会尽快处理')
    emit('submitted')
    emit('update:modelValue', false)
  } catch (error) {
    // 409 重复举报：接口会给出明确文案，这里只需保持弹窗打开让用户看到
    const code = (error as { code?: number }).code
    if (code === 409) {
      emit('update:modelValue', false)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="举报"
    width="440px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top">
      <el-form-item label="举报原因">
        <el-radio-group v-model="reasonType">
          <el-radio v-for="item in reasons" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="补充说明（选填）">
        <el-input
          v-model="reasonDetail"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          placeholder="可以补充一些细节，便于我们判断"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>
