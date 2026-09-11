<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/user'
import { fileApi } from '@/api/file'
import { useUserStore } from '@/stores/user'
import type { Post, UserFollow } from '@/api/types'
import { relativeTime } from '@/utils/format'
import UserAvatar from '@/components/UserAvatar.vue'
import PostCard from '@/components/PostCard.vue'
import UserCard from '@/components/UserCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref<'info' | 'posts' | 'following' | 'followers'>('info')

/* ---------------- 资料编辑 ---------------- */
const profileForm = reactive({
  nickname: '',
  gender: 0,
  birthday: '',
  bio: '',
})
const profileFormRef = ref<FormInstance>()
const savingProfile = ref(false)

const profileRules: FormRules = {
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { max: 32, message: '昵称长度不能超过 32 个字符', trigger: 'blur' },
  ],
  bio: [{ max: 255, message: '个人简介不能超过 255 个字符', trigger: 'blur' }],
}

function syncFormFromUser() {
  const user = userStore.user
  if (!user) return
  profileForm.nickname = user.nickname
  profileForm.gender = user.gender
  profileForm.birthday = user.birthday ?? ''
  profileForm.bio = user.bio ?? ''
}

async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingProfile.value = true
  try {
    await userStore.updateProfile({
      nickname: profileForm.nickname.trim(),
      gender: profileForm.gender,
      birthday: profileForm.birthday || null,
      bio: profileForm.bio.trim(),
    })
    ElMessage.success('资料已更新')
  } finally {
    savingProfile.value = false
  }
}

/* ---------------- 头像 ---------------- */
const uploadingAvatar = ref(false)

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const { url } = await fileApi.uploadImage(file)
    await userStore.updateAvatar(url)
    ElMessage.success('头像已更新')
  } finally {
    uploadingAvatar.value = false
    // 清空 input，否则连续选择同一个文件不会触发 change
    input.value = ''
  }
}

/* ---------------- 修改密码 ---------------- */
const passwordVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const savingPassword = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '原密码不能为空', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 6, max: 20, message: '新密码长度必须在 6~20 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function changePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingPassword.value = true
  try {
    await userApi.changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码已修改，请用新密码重新登录')
    passwordVisible.value = false
    // 密码变了，旧 Token 虽仍有效，但让用户重新登录更符合心理预期
    userStore.logout()
    router.push({ name: 'login' })
  } finally {
    savingPassword.value = false
  }
}

/* ---------------- 我的动态 / 关注 / 粉丝 ---------------- */
const posts = ref<Post[]>([])
const relations = ref<UserFollow[]>([])
const listLoading = ref(false)
const loadedTabs = new Set<string>()

async function loadTab(tab: 'posts' | 'following' | 'followers') {
  const user = userStore.user
  if (!user) return
  listLoading.value = true
  try {
    if (tab === 'posts') {
      const result = await userApi.posts(user.id, 1, 20)
      posts.value = result.list
    } else {
      const result =
        tab === 'following'
          ? await userApi.following(user.id, 1, 20)
          : await userApi.followers(user.id, 1, 20)
      relations.value = result.list
    }
    loadedTabs.add(tab)
  } finally {
    listLoading.value = false
  }
}

function switchTab(tab: typeof activeTab.value) {
  activeTab.value = tab
  if (tab !== 'info' && !loadedTabs.has(tab)) {
    loadTab(tab)
  }
}

function onPostDeleted(id: string) {
  posts.value = posts.value.filter((item) => item.id !== id)
  if (userStore.user) {
    userStore.user.postCount = Math.max(0, userStore.user.postCount - 1)
  }
}

onMounted(() => {
  syncFormFromUser()
})
</script>

<template>
  <div class="profile">
    <section class="profile__hero miqu-card">
      <label class="profile__avatar" :class="{ 'is-loading': uploadingAvatar }">
        <UserAvatar
          :src="userStore.user?.avatar"
          :nickname="userStore.displayName"
          :size="84"
        />
        <span class="profile__avatar-mask">
          <el-icon v-if="!uploadingAvatar"><Camera /></el-icon>
          <el-icon v-else class="is-spin"><Loading /></el-icon>
        </span>
        <input
          type="file"
          accept="image/jpeg,image/png,image/gif,image/webp"
          hidden
          @change="onAvatarChange"
        />
      </label>

      <div class="profile__hero-text">
        <h1>{{ userStore.user?.nickname }}</h1>
        <p class="profile__username">@{{ userStore.user?.username }}</p>
        <div class="profile__stats">
          <span><strong>{{ userStore.user?.postCount ?? 0 }}</strong> 动态</span>
          <span><strong>{{ userStore.user?.followingCount ?? 0 }}</strong> 关注</span>
          <span><strong>{{ userStore.user?.followerCount ?? 0 }}</strong> 粉丝</span>
        </div>
      </div>

      <div class="profile__hero-actions">
        <el-button round @click="router.push(`/users/${userStore.user?.id}`)">
          查看我的主页
        </el-button>
      </div>
    </section>

    <div class="profile__tabs miqu-card">
      <button
        v-for="tab in [
          { key: 'info', label: '基本资料' },
          { key: 'posts', label: '我的动态' },
          { key: 'following', label: '我的关注' },
          { key: 'followers', label: '我的粉丝' },
        ]"
        :key="tab.key"
        class="profile__tab"
        :class="{ 'is-active': activeTab === tab.key }"
        @click="switchTab(tab.key as typeof activeTab.value)"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- 基本资料 -->
    <section v-if="activeTab === 'info'" class="miqu-card profile__panel">
      <h2 class="profile__panel-title">基本资料</h2>

      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        label-position="top"
        class="profile__form"
      >
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="profileForm.nickname" maxlength="32" />
        </el-form-item>

        <div class="profile__grid">
          <el-form-item label="性别">
            <el-radio-group v-model="profileForm.gender">
              <el-radio :value="0">保密</el-radio>
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="生日">
            <el-date-picker
              v-model="profileForm.birthday"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <el-form-item label="个人简介" prop="bio">
          <el-input
            v-model="profileForm.bio"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            resize="none"
          />
        </el-form-item>

        <div class="profile__form-footer">
          <el-button type="primary" round :loading="savingProfile" @click="saveProfile">
            保存修改
          </el-button>
        </div>
      </el-form>

      <div class="profile__divider" />

      <h2 class="profile__panel-title">账号安全</h2>
      <div class="profile__security">
        <div>
          <strong>登录密码</strong>
          <p>定期更换密码可以降低账号被盗的风险</p>
        </div>
        <el-button round @click="passwordVisible = true">修改密码</el-button>
      </div>

      <div class="profile__security">
        <div>
          <strong>邮箱</strong>
          <p>{{ userStore.user?.email }}</p>
        </div>
        <el-tag size="small" type="info" round>暂不支持修改</el-tag>
      </div>

      <div class="profile__security">
        <div>
          <strong>注册时间</strong>
          <p>{{ relativeTime(userStore.user?.createTime) }}</p>
        </div>
      </div>
    </section>

    <!-- 我的动态 -->
    <template v-else-if="activeTab === 'posts'">
      <div v-if="listLoading" class="miqu-card profile__panel">
        <el-skeleton animated :rows="4" />
      </div>
      <template v-else>
        <PostCard
          v-for="post in posts"
          :key="post.id"
          :post="post"
          @deleted="onPostDeleted"
        />
        <EmptyState v-if="!posts.length" icon="Document" description="你还没有发布过动态">
          <el-button type="primary" round @click="router.push({ name: 'home' })">
            去发布第一条
          </el-button>
        </EmptyState>
      </template>
    </template>

    <!-- 我的关注 / 粉丝 -->
    <template v-else>
      <div v-if="listLoading" class="miqu-card profile__panel">
        <el-skeleton animated :rows="4" />
      </div>
      <template v-else>
        <UserCard
          v-for="item in relations"
          :key="item.id"
          :id="item.id"
          :username="item.username"
          :nickname="item.nickname"
          :avatar="item.avatar"
          :bio="item.bio"
          :followed-by-me="item.followedByMe"
        />
        <EmptyState
          v-if="!relations.length"
          icon="UserFilled"
          :description="activeTab === 'following' ? '你还没有关注任何人' : '还没有人关注你'"
        >
          <el-button v-if="activeTab === 'following'" @click="router.push({ name: 'search' })">
            去发现有趣的人
          </el-button>
        </EmptyState>
      </template>
    </template>

    <!-- 修改密码 -->
    <el-dialog v-model="passwordVisible" title="修改密码" width="420px">
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingPassword" @click="changePassword">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile__hero {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 26px;
  margin-bottom: 14px;
}

.profile__avatar {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  flex-shrink: 0;
}

.profile__avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  opacity: 0;
  transition: opacity var(--miqu-transition);
}

.profile__avatar:hover .profile__avatar-mask,
.profile__avatar.is-loading .profile__avatar-mask {
  opacity: 1;
}

.is-spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.profile__hero-text {
  flex: 1;
  min-width: 0;
}

.profile__hero-text h1 {
  margin: 0;
  font-size: 21px;
  letter-spacing: -0.02em;
}

.profile__username {
  margin: 3px 0 0;
  color: var(--miqu-text-tertiary);
  font-size: 13px;
}

.profile__stats {
  display: flex;
  gap: 18px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--miqu-text-secondary);
}

.profile__stats strong {
  color: var(--miqu-text);
}

.profile__tabs {
  display: flex;
  gap: 4px;
  padding: 8px 12px;
  margin-bottom: 16px;
}

.profile__tab {
  padding: 7px 18px;
  border: none;
  background: transparent;
  border-radius: var(--miqu-radius-sm);
  color: var(--miqu-text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--miqu-transition), color var(--miqu-transition);
}

.profile__tab:hover {
  background: var(--miqu-primary-softer);
  color: var(--miqu-primary);
}

.profile__tab.is-active {
  background: var(--miqu-primary-soft);
  color: var(--miqu-primary);
}

.profile__panel {
  padding: 22px 26px;
}

.profile__panel-title {
  margin: 0 0 18px;
  font-size: 15px;
  font-weight: 600;
}

.profile__form {
  max-width: 560px;
}

.profile__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.profile__form-footer {
  margin-top: 4px;
}

.profile__divider {
  height: 1px;
  background: var(--miqu-border);
  margin: 26px 0 22px;
}

.profile__security {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--miqu-border);
}

.profile__security:last-child {
  border-bottom: none;
}

.profile__security strong {
  font-size: 14px;
}

.profile__security p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--miqu-text-tertiary);
}

@media (max-width: 640px) {
  .profile__hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile__grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
