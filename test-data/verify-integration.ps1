# =====================================================================
# M3 集成接线验证：登录 → TICKET 问答建工单 → 查工单确认闭环
# 用法：powershell -ExecutionPolicy Bypass -File test-data\verify-integration.ps1
# 编码注意：本文件必须保持 UTF-8 with BOM！PS 5.1 对无 BOM 文件按 GBK 解析，
#   中文字面量会变成乱码并经 q3.json 写进数据库（路6 已踩坑，勿删 BOM）。
# =====================================================================
[Console]::OutputEncoding = [Text.Encoding]::UTF8
cd c:\Users\17962\Desktop\Big_Java_Project\AIBishe
$base = "http://192.168.25.129:8088"   # 路6 容器实测：改走 VM 全栈 nginx 同源入口（原 localhost:8080 直连后端）

# ① 登录拿 JWT（admin/admin123，M3 播种的管理员）
$login = Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType "application/json;charset=utf-8" -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
$token = $login.data.token
Write-Host "① 登录: code=$($login.code) user=$($login.data.user.username)/$($login.data.user.role) tokenLen=$($token.Length)"

# ② 带 JWT 问 TICKET 问题（SSE），应触发 QaService→TicketService.createTicket 真建工单
Write-Host "`n② TICKET 问答 SSE 事件流（应含'已创建工单(编号X)' + ticket_hint + done）:"
[IO.File]::WriteAllText("$PWD\test-data\q3.json", '{"question":"p6-我要投诉，网速太慢了，帮我转人工客服"}', (New-Object Text.UTF8Encoding $false))
curl.exe -N -s -X POST "$base/api/qa/chat/stream" -H "Content-Type: application/json" -H "Authorization: Bearer $token" --data-binary "@test-data/q3.json"

# ③ 查我的工单，确认②真的建了单（接线成功的铁证）
Write-Host "`n③ 我的工单 /api/ticket/mine（应看到刚建的单，question=投诉内容, status=OPEN）:"
Invoke-RestMethod "$base/api/ticket/mine" -Headers @{ Authorization = "Bearer $token" } | ConvertTo-Json -Depth 5
