#!/usr/bin/env bash
# =====================================================================
# ES 就绪后：创建带 IK 中文分词的 chunk 索引 + 验证分词效果
# 用法（在能连到 ES 的机器上）：
#   VM 内：      bash deploy/elasticsearch/init-index.sh
#   Windows 连VM：ES_HOST=http://192.168.25.129:9200 bash deploy/elasticsearch/init-index.sh
# =====================================================================
set -e
ES_HOST="${ES_HOST:-http://localhost:9200}"
INDEX="cs_knowledge_chunk"
DIR="$(cd "$(dirname "$0")" && pwd)"

echo "==> 检查 ES: $ES_HOST"
curl -sf "$ES_HOST" >/dev/null || { echo "ES 未就绪，请先在 VM 内 docker compose up -d --build"; exit 1; }

if curl -sf "$ES_HOST/$INDEX" >/dev/null; then
  echo "==> 索引 $INDEX 已存在，跳过（如需重建：curl -X DELETE $ES_HOST/$INDEX）"
else
  echo "==> 创建索引 $INDEX（content=ik_max_word/ik_smart, embedding=dense_vector 1024 cosine）"
  curl -X PUT "$ES_HOST/$INDEX" -H 'Content-Type: application/json' -d @"$DIR/es-chunk-mapping.json"
  echo ""
  echo "==> 索引创建完成"
fi

echo "==> 验证 IK 分词（期望切出 5g/畅享/套餐/资费 等词，而非逐字切分）"
curl -X POST "$ES_HOST/$INDEX/_analyze" -H 'Content-Type: application/json' \
  -d '{"analyzer":"ik_max_word","text":"5G畅享套餐资费怎么算"}'
echo ""
