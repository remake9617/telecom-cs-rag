# 路6 重测脚本3：模型探针 + TICKET 对照（temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$t = Get-Content "$PWD\test-data\p6-token.txt" -Raw
$h = @{ Authorization = "Bearer $t" }
"--- /api/health/ai (chat+embedding probe):"
$a = Invoke-RestMethod 'http://192.168.25.129:8088/api/health/ai' -Headers $h -TimeoutSec 90
$a.data | ConvertTo-Json -Compress
"--- TICKET 对照（UTF-8 文件传参）:"
[IO.File]::WriteAllText("$PWD\test-data\p6-q2.json", '{"message":"p6-retest-我要投诉宽带，转人工","conversationId":null}', (New-Object Text.UTF8Encoding $false))
& curl.exe -s -N -X POST 'http://192.168.25.129:8088/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $t" --data-binary "@test-data/p6-q2.json" | ForEach-Object { "$(Get-Date -Format 'HH:mm:ss.fff') | $($_.Substring(0,[Math]::Min(130,$_.Length)))" }
