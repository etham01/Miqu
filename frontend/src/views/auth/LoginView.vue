<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    await notificationStore.refresh()
    notificationStore.startPolling()
    ElMessage.success('欢迎回来')
    // 登录前想去哪就回哪，没有则回首页
    const redirect = route.query.redirect as string | undefined
    router.replace(redirect || '/')
  } finally {
    loading.value = false
  }
}

/** 演示账号：种子数据里固定的几个，方便直接体验不同角色。 */
const demoAccounts = [
  { label: '普通用户', username: 'test001', password: '123456' },
  { label: '管理员', username: 'admin', password: '123456' },
  { label: '被禁用', username: 'banned001', password: '123456' },
]

function fillDemo(account: (typeof demoAccounts)[number]) {
  form.username = account.username
  form.password = account.password
}
</script>

<template>
  <div class="auth">
    <div class="auth__panel">
      <div class="auth__brand">
        <span class="auth__brand-mark">M</span>
        <div>
          <h1 class="auth__brand-name">Miqu</h1>
          <p class="auth__brand-sub">觅取 —— 发现、认识、交流</p>
        </div>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable>
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="submit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="auth__submit"
          :loading="loading"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>

      <p class="auth__switch">
        还没有账号？
        <RouterLink to="/register" class="auth__link">立即注册</RouterLink>
      </p>

      <div class="auth__demo">
        <span class="auth__demo-title">演示账号（点击填入）</span>
        <div class="auth__demo-list">
          <el-button
            v-for="account in demoAccounts"
            :key="account.username"
            size="small"
            round
            @click="fillDemo(account)"
          >
            {{ account.label }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(900px 460px at 50% -10%, #eef0ff 0%, rgba(238, 240, 255, 0) 70%),
    var(--miqu-bg);
}

.auth__panel {
  width: 100%;
  max-width: 400px;
  background: var(--miqu-surface);
  border-radius: 20px;
  border: 1px solid var(--miqu-border);
  box-shadow: var(--miqu-shadow-hover);
  padding: 34px 32px 28px;
}

.auth__brand {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 26px;
}

.auth__brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: var(--miqu-primary);
  color: #fff;
  font-size: 22px;
  font-weight: 700;
}

.auth__brand-name {
  margin: 0;
  font-size: 21px;
  letter-spacing: -0.02em;
}

.auth__brand-sub {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

.auth__submit {
  width: 100%;
  margin-top: 4px;
  height: 44px;
  border-radius: 12px;
  font-size: 15px;
}

.auth__switch {
  margin: 18px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--miqu-text-secondary);
}

.auth__link {
  color: var(--miqu-primary);
  font-weight: 500;
}

.auth__demo {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed var(--miqu-border-strong);
}

.auth__demo-title {
  font-size: 12px;
  color: var(--miqu-text-tertiary);
}

.auth__demo-list {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
</style>
