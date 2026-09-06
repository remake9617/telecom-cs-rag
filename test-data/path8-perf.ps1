# path8: 性能对照（ping=无 Redis 基线 vs me=含黑名单 EXISTS + user-invalid GET）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$base = "http://localhost:8080"
$loginBody = [Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}')
$tok = (Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json").data.token

function Bench($name, $sb) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt 20; $i++) { & $sb }
    $sw.Stop()
    "$name : total=$($sw.ElapsedMilliseconds)ms avg=$([math]::Round($sw.ElapsedMilliseconds/20.0,1))ms"
}

Bench "ping x20 (no auth, no Redis)" { Invoke-WebRequest -Uri "$base/api/health/ping" -UseBasicParsing | Out-Null }
Bench "me   x20 (auth + 2 Redis ops)" { Invoke-WebRequest -Uri "$base/api/auth/me" -Headers @{ Authorization = "Bearer $tok" } -UseBasicParsing | Out-Null }
Bench "me   x20 (auth + 2 Redis ops)" { Invoke-WebRequest -Uri "$base/api/auth/me" -Headers @{ Authorization = "Bearer $tok" } -UseBasicParsing | Out-Null }
