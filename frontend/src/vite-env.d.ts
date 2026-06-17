/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 请求前缀 */
  readonly VITE_API_PREFIX: string;
  /** 路由基础路径 */
  readonly VITE_BASE_PATH: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
