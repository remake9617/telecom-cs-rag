# =====================================================================
# 前端镜像（路6 / B1）—— 多阶段构建：node 构建 dist → nginx 托管 + 同源反代
# 构建上下文 = 仓库根目录（需访问 deploy/nginx/nginx.conf）
# =====================================================================

# ---------- 阶段 1：构建（D25 锁 node >= 20；lock 与 package.json 已同步可安全 npm ci） ----------
FROM node:20 AS build
WORKDIR /app

# .npmrc（国内 registry 镜像）+ lock 先拷：依赖层缓存
COPY frontend/.npmrc frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./

# 生产构建的关键裁决（FD-9 / config.ts）：VITE_API_BASE_URL 必须传空字符串！
# 生产走 nginx 同源反代，前端请求 /api 相对路径；
# 若把 dev 的 http://localhost:8080 打进产物，公网访客的浏览器会去打「他自己的 localhost」，全站接口失败。
# 进程环境变量优先级高于 .env 文件，因此这里能覆盖 frontend/.env 里的 dev 值。
ENV VITE_API_BASE_URL=""
ENV VITE_USE_MOCK="false"
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:alpine
COPY deploy/nginx/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
