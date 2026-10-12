import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias: {
      '@': '/src'
    }
  },
  server: {
    // 【修复】dev 环境把 /api 反代到后端，避免依赖后端 CORS 白名单，
    // 也让「同源相对路径」在生产(nginx)与开发(vite)下行为一致。
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8081',
        changeOrigin: true
      },
      '/ws': {
        target: process.env.VITE_DEV_API_TARGET || 'http://localhost:8081',
        changeOrigin: true,
        ws: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/tokens.scss";`
      }
    }
  }
})
