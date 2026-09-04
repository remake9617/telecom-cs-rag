/// <reference types="vite/client" />

// 前端环境变量类型声明：仅暴露非敏感配置，密钥绝不进入前端。
interface ImportMetaEnv {
  /** API 基础地址，默认 http://localhost:8080 */
  readonly VITE_API_BASE_URL: string;
  /** 是否启用 MSW Mock，'true' | 'false' */
  readonly VITE_USE_MOCK: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
