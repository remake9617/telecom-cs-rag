// JWT 本地持久化：仅存 token，绝不存任何密钥。
// 说明：MVP 用 localStorage（刷新保持登录）；如需更强安全可后续换 httpOnly Cookie（属后端改造，届时同步契约）。

const TOKEN_KEY = 'cs_access_token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}
