import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    // 把框架与组件库拆成独立 chunk：它们不随业务代码变化，
    // 浏览器可以长期缓存，业务代码更新时用户不必重新下载 1MB+ 的依赖。
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
        },
      },
    },
    // Element Plus 全量引入后是 1.09MB（gzip 后约 342KB），必然触发体积告警。
    // 这个阈值只是让构建输出保持可读，**并没有真正减小体积**。
    // 真正要做的是改成按需引入：
    //   npm i -D unplugin-vue-components unplugin-auto-import
    //   并配上 ElementPlusResolver，可把这一块压到 150KB 左右。
    // 当前保留全量引入是有意取舍：少两个构建插件，配置更直白，
    // 本地开发与演示场景下体积不是瓶颈。
    chunkSizeWarningLimit: 1200,
  },
  server: {
    port: 5173,
    // 开发期把 /api 与 /uploads 代理到后端。
    // 这样做的好处是前端代码里一律用相对路径，既不涉及 CORS，
    // 也不用在代码里硬编码后端地址——换端口只改这一处。
    // 后端默认端口是 8081（8080 被 Windows 服务占用，见 README）。
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
