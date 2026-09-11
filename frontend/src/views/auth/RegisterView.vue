<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
  gender: 0,
  birthday: '',
  bio: '',
})

/**
 * 前端校验与后端保持同一套规则（长度、字符集），
 * 目的是尽早反馈；真正的判定仍以后端为准——前端校验只是体验优化。
 */
const rules: FormRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度必须在 4~20 个字符之间', trigger: 'blur' },
    {
      pattern: /^[A-Za-z][A-Za-z0-9_]*$/,
      message: '用户名必须以字母开头，且只能包含字母、数字和下划线',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度必须在 6~20 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { max: 32, message: '昵称长度不能超过 32 个字符', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  bio: [{ max: 255, message: '个人简介不能超过 255 个字符', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register({
      username: form.username.trim(),
      password: form.password,
      nickname: form.nickname.trim(),
      email: form.email.trim(),
      gender: form.gender,
      // 空字符串会被后端当成非法日期，必须转成 null
      birthday: form.birthday || null,
      bio: form.bio.trim(),
    })
    ElMessage.success('注册成功，请登录')
    router.replace({ name: 'login', query: { username: form.username.trim() } })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <div class="auth__panel">
      <RouterLink to="/login" class="auth__back">
        <el-icon><ArrowLeft /></el-icon> 返回登录
      </RouterLink>

      <h1 class="auth__title">创建账号</h1>
      <p class="auth__subtitle">加入 Miqu，开始发现有趣的人</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="字母开头，4~20 位" clearable />
        </el-form-item>

        <div class="auth__row">
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="6~20 位" show-password />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再次输入"
              show-password
            />
          </el-form-item>
        </div>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="展示给其他人的名字" clearable />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于接收通知" clearable />
        </el-form-item>

        <div class="auth__row">
          <el-form-item label="性别">
            <el-radio-group v-model="form.gender">
              <el-radio :value="0">保密</el-radio>
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="生日">
            <el-date-picker
              v-model="form.birthday"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="个人简介" prop="bio">
          <el-input
            v-model="form.bio"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            resize="none"
            placeholder="介绍一下自己（选填）"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="auth__submit"
          :loading="loading"
          @click="submit"
        >
          注册
        </el-button>
      </el-form>

      <p class="auth__switch">
        已有账号？
        <RouterLink to="/login" class="auth__link">去登录</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 24px;
  background:
    radial-gradient(900px 460px at 50% -10%, #eef0ff 0%, rgba(238, 240, 255, 0) 70%),
    var(--miqu-bg);
}

.auth__panel {
  width: 100%;
  max-width: 520px;
  background: var(--miqu-surface);
  border-radius: 20px;
  border: 1px solid var(--miqu-border);
  box-shadow: var(--miqu-shadow-hover);
  padding: 30px 32px 26px;
}

.auth__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--miqu-text-secondary);
  margin-bottom: 14px;
}

.auth__back:hover {
  color: var(--miqu-primary);
}

.auth__title {
  margin: 0;
  font-size: 22px;
  letter-spacing: -0.02em;
}

.auth__subtitle {
  margin: 4px 0 22px;
  font-size: 13px;
  color: var(--miqu-text-tertiary);
}

.auth__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.auth__submit {
  width: 100%;
  margin-top: 6px;
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

@media (max-width: 560px) {
  .auth__row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
