import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { guestOnly: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { guestOnly: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'search',
        name: 'search',
        component: () => import('@/views/search/SearchView.vue'),
        meta: { title: '发现' },
      },
      {
        path: 'users/:id',
        name: 'user-profile',
        component: () => import('@/views/user/UserProfileView.vue'),
        meta: { title: '用户主页' },
      },
      {
        // 通知里的「查看」会跳到单条动态。动态可能已被删除，
        // 页面需要能优雅地展示"不存在"而不是白屏。
        path: 'posts/:id',
        name: 'post-detail',
        component: () => import('@/views/post/PostDetailView.vue'),
        meta: { title: '动态详情' },
      },
      {
        path: 'messages',
        name: 'messages',
        component: () => import('@/views/message/MessageView.vue'),
        meta: { requiresAuth: true, title: '消息' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/notification/NotificationView.vue'),
        meta: { requiresAuth: true, title: '通知' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/ProfileView.vue'),
        meta: { requiresAuth: true, title: '个人中心' },
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'admin-dashboard',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据概览' },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserManageView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'posts',
        name: 'admin-posts',
        component: () => import('@/views/admin/PostManageView.vue'),
        meta: { title: '动态管理' },
      },
      {
        path: 'comments',
        name: 'admin-comments',
        component: () => import('@/views/admin/CommentManageView.vue'),
        meta: { title: '评论管理' },
      },
      {
        path: 'reports',
        name: 'admin-reports',
        component: () => import('@/views/admin/ReportManageView.vue'),
        meta: { title: '举报管理' },
      },
      {
        path: 'logs',
        name: 'admin-logs',
        component: () => import('@/views/admin/LogView.vue'),
        meta: { title: '操作日志' },
      },
    ],
  },
  {
    // 兜底：未匹配的路径回首页，而不是停在空白页
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth && !userStore.isLogin) {
    ElMessage.warning('请先登录')
    // 带上来源，登录后跳回用户原本想去的页面
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    ElMessage.error('无权限访问管理后台')
    return { name: 'home' }
  }

  if (to.meta.guestOnly && userStore.isLogin) {
    return { name: 'home' }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · Miqu 觅取` : 'Miqu 觅取'
})

export default router
