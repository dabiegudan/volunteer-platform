import { defineConfig } from 'vitest/config'
import { fileURLToPath } from 'url'

export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    // 测试环境
    environment: 'happy-dom',

    // 全局设置
    globals: true,

    // 测试文件匹配
    include: ['tests/**/*.{test,spec}.{js,ts}'],

    // 设置文件
    setupFiles: ['tests/setup.js'],

    // 覆盖率配置
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: [
        'node_modules/',
        'dist/',
        'tests/',
        'scripts/',
        '*.config.*'
      ]
    },

    // CSS处理
    css: {
      modules: {
        classNameStrategy: 'non-scoped'
      }
    }
  },

  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`
      }
    }
  }
})
