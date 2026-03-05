/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly BASE_URL: string
  // 可以在这里添加更多环境变量类型
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
