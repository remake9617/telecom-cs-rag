# =====================================================================
# Elasticsearch 8.18.8 + IK 中文分词插件
# 由 docker-compose build 构建；供混合检索的 BM25 通道做中文分词
# 注意：analysis-ik 版本必须与 ES 版本严格一致（8.18.8），否则安装失败
# =====================================================================
# 用 Docker Hub 官方镜像（可被国内 registry-mirror 加速）；与 docker.elastic.co 为同一镜像
FROM elasticsearch:8.18.8

# 安装 IK 分词插件（infinilabs 官方稳定源）
RUN bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.18.8.zip
