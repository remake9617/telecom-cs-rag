# =====================================================================
# 后端镜像（路6 / B1）—— 多阶段构建
# 构建上下文 = 仓库根目录（docker-compose.yml 的 backend.build.context = .），
# 因为 Maven 多模块聚合必须从根 pom 开始打包。
# 安全红线：密钥绝不进镜像 —— 不 COPY .env、不用 ARG 传密钥，全部由 compose
# 的 env_file/environment 运行期注入。
# =====================================================================

# ---------- 阶段 1：构建（root pom 已配阿里云镜像，国内网络可直接拉依赖） ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷各模块 pom 并预拉依赖：源码不变时命中 Docker 层缓存，跳过依赖下载
COPY pom.xml ./
COPY cs-bootstrap/pom.xml cs-bootstrap/
COPY cs-framework/pom.xml cs-framework/
COPY cs-infra-ai/pom.xml cs-infra-ai/
COPY cs-ingestion/pom.xml cs-ingestion/
COPY cs-knowledge/pom.xml cs-knowledge/
COPY cs-qa/pom.xml cs-qa/
COPY cs-stats/pom.xml cs-stats/
COPY cs-system/pom.xml cs-system/
COPY cs-ticket/pom.xml cs-ticket/
RUN mvn -B -q dependency:go-offline

# 再拷源码做真正打包；产物为 cs-bootstrap/target/cs-server.jar（finalName 已配 repackage）
COPY cs-bootstrap/src cs-bootstrap/src
COPY cs-framework/src cs-framework/src
COPY cs-infra-ai/src cs-infra-ai/src
COPY cs-ingestion/src cs-ingestion/src
COPY cs-knowledge/src cs-knowledge/src
COPY cs-qa/src cs-qa/src
COPY cs-stats/src cs-stats/src
COPY cs-system/src cs-system/src
COPY cs-ticket/src cs-ticket/src
RUN mvn -B clean package -DskipTests

# ---------- 阶段 2：运行（仅 JRE，非 root 用户） ----------
FROM eclipse-temurin:17-jre

# compose 的 backend healthcheck 用 curl 探测 /api/health/ping（基础镜像不带）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行：最小权限原则
RUN useradd --system --uid 10001 csuser

WORKDIR /app
COPY --from=build /build/cs-bootstrap/target/cs-server.jar app.jar
USER csuser

EXPOSE 8080
# JAVA_OPTS 由 compose 注入（堆大小 + 时区 -Duser.timezone）；未注入时兜底保东八区。
# 注意：容器内 JVM 默认 UTC，只设 TZ 环境变量对 JVM 不够，必须 -Duser.timezone 双保险，
# 否则时间字段「格式对但值差 8 小时」（契约要求东八区）。
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--Duser.timezone=Asia/Shanghai} -jar app.jar"]
