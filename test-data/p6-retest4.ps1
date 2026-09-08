# 路6 重测脚本4：KB 问答 SSE 逐包时间戳（正确契约字段 question，temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod 'http://192.168.25.129:8088/api/auth/login' -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
"login code=$($login.code)"
$t = $login.data.token
[IO.File]::WriteAllText("$PWD\test-data\p6-token.txt", $t, (New-Object Text.UTF8Encoding $false))
[IO.File]::WriteAllText("$PWD\test-data\p6-q3.json", '{"question":"p6-retest-5G套餐资费怎么算","conversationId":null}', (New-Object Text.UTF8Encoding $false))
& curl.exe -s -N -X POST 'http://192.168.25.129:8088/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $t" --data-binary "@test-data/p6-q3.json" | ForEach-Object { "$(Get-Date -Format 'HH:mm:ss.fff') | $($_.Substring(0,[Math]::Min(170,$_.Length)))" }
