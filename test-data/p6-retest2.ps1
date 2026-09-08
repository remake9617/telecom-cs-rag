# 路6 重测脚本2：KB 问答 SSE 逐包时间戳（temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$t = Get-Content "$PWD\test-data\p6-token.txt" -Raw
[IO.File]::WriteAllText("$PWD\test-data\p6-q.json", '{"message":"p6-retest-5G套餐资费怎么算","conversationId":null}', (New-Object Text.UTF8Encoding $false))
& curl.exe -s -N -X POST 'http://192.168.25.129:8088/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $t" --data-binary "@test-data/p6-q.json" | ForEach-Object { "$(Get-Date -Format 'HH:mm:ss.fff') | $($_.Substring(0,[Math]::Min(150,$_.Length)))" }
