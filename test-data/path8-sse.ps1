# path8: SSE 实测（DEF-078 触发路径）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$loginBody = [Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}')
$tok = (Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json").data.token
$bodyFile = "$env:TEMP\p8-sse.json"
[IO.File]::WriteAllBytes($bodyFile, [Text.Encoding]::UTF8.GetBytes('{"question":"129套餐包含多少流量"}'))
"TOKEN_LEN=$($tok.Length)"
curl.exe -N -s -X POST "http://localhost:8080/api/qa/chat/stream" -H "Authorization: Bearer $tok" -H "Content-Type: application/json" --data-binary "@$bodyFile" --max-time 120
