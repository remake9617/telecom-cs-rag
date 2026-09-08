# 路6 重测脚本1：登录 + 旧卷数据确认（temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod 'http://192.168.25.129:8088/api/auth/login' -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
"login code=$($login.code)"
$t = $login.data.token
[IO.File]::WriteAllText("$PWD\test-data\p6-token.txt", $t, (New-Object Text.UTF8Encoding $false))
$h = @{ Authorization = "Bearer $t" }
"localNow=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
"--- qa/conversations:"
$c = Invoke-RestMethod 'http://192.168.25.129:8088/api/qa/conversations' -Headers $h
"count=$($c.data.Count)"
$c.data | Select-Object -First 3 | ConvertTo-Json -Compress
"--- kb/documents:"
$d = Invoke-RestMethod 'http://192.168.25.129:8088/api/kb/documents?current=1&size=10' -Headers $h
"total=$($d.data.total)"
$d.data.records | Select-Object -First 3 | ConvertTo-Json -Compress
"--- kb/bases:"
$b = Invoke-RestMethod 'http://192.168.25.129:8088/api/kb/bases' -Headers $h
$b.data | ConvertTo-Json -Compress
