# 路6 重测脚本6：上传最小化排查（temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$t = Get-Content "$PWD\test-data\p6-token.txt" -Raw
"--- 上传 mini.md 到 kbId=3 (p6-retest-kb):"
& curl.exe -s -X POST 'http://192.168.25.129:8088/api/kb/3/documents' -H "Authorization: Bearer $t" -F 'file=@test-data/p6-mini.md'
""
"--- 上传 mini.md 到 kbId=1 (M4验证知识库):"
& curl.exe -s -X POST 'http://192.168.25.129:8088/api/kb/1/documents' -H "Authorization: Bearer $t" -F 'file=@test-data/p6-mini.md'
""
"--- 直连 VM 宿主 9200 确认 ES 可写(插入测试后删除):"
& curl.exe -s -X POST 'http://192.168.25.129:9200/cs_knowledge_chunk/_doc' -H 'Content-Type: application/json' -d '{"content":"p6-write-probe","metadata":{"chunk_id":"p6-probe","doc_id":"p6-probe"}}'
