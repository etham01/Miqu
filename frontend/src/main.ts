import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import '@/styles/global.css'

import App from './App.vue'
import router from './router'
import { onUnauthorized, USER_KEY } from '@/api/request'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 全量注册图标：项目里用到的图标分散在各页面，逐个 import 收益不大
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

/* ------------------------------------------------------------------
   登录失效的统一处理。
   在 request.ts 里直接 import router / store 会形成循环依赖
   （router → views → api → request），所以改由这里注入回调。
   ------------------------------------------------------------------ */
onUnauthorized(() => {
  useUserStore().clear()
  useNotificationStore().reset()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

/* ------------------------------------------------------------------
   启动时恢复登录态：
   本地有 Token 就拉一次 /users/me 校验并刷新用户信息。
   失败会让 store 自行清空——这比"顶着一个失效 Token 到处请求"要好。
   ------------------------------------------------------------------ */
async function bootstrap() {
  const userStore = useUserStore()

  if (userStore.token) {
    // 先用本地缓存渲染，避免首屏闪烁；再异步校正
    await userStore.fetchMe()
  }

  if (userStore.isLogin) {
    useNotificationStore().startPolling()
  }

  app.mount('#app')
}

// 清掉可能存在的旧版本缓存键，避免升级后读到不兼容的结构
if (!localStorage.getItem('miqu_version')) {
  localStorage.removeItem(USER_KEY)
  localStorage.setItem('miqu_version', '1')
}

bootstrap()
