# path8: 禁用即时失效验证 v2（RESP 长度动态计算）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$base = "http://localhost:8080"
$script:key = "jwt:user-invalid:1"

function Invoke-Redis {
    param([string[]]$CmdArgs)
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect("192.168.25.129", 16379)
    $stream = $client.GetStream()
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("*$($CmdArgs.Count)`r`n")
    foreach ($a in $CmdArgs) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($a)
        [void]$sb.Append("`$$($bytes.Length)`r`n$a`r`n")
    }
    $payload = [Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $stream.Write($payload, 0, $payload.Length)
    $stream.Flush()
    Start-Sleep -Milliseconds 300
    $buf = New-Object byte[] 65536
    $out = New-Object System.Text.StringBuilder
    while ($stream.DataAvailable) {
        $n = $stream.Read($buf, 0, $buf.Length)
        [void]$out.Append([Text.Encoding]::UTF8.GetString($buf, 0, $n))
        Start-Sleep -Milliseconds 50
    }
    $client.Close()
    return $out.ToString().Trim()
}

# STEP-A: 先登录拿 token X（此刻它的 issuedAt 是"过去"）
$loginBody = [Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}')
$rx = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$tokX = $rx.data.token
"STEP-A login tokenX: code=$($rx.code)"

# STEP-B: 写失效时间戳 = 现在（token X 的 issuedAt 必然早于它）
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
"STEP-B SET: $(Invoke-Redis @('SET', $script:key, "$nowMs", 'EX', '86400')) (ts=$nowMs)"

# STEP-C: token X → 期望 401
try {
    $r = Invoke-WebRequest -Uri "$base/api/auth/me" -Headers @{ Authorization = "Bearer $tokX" } -UseBasicParsing
    "STEP-C me(tokenX): HTTP=$([int]$r.StatusCode)  <-- 期望 401，异常！"
} catch { "STEP-C me(tokenX): HTTP=$([int]$_.Exception.Response.StatusCode)  <-- 期望 401" }

# STEP-D: 重新登录 token Y（issuedAt 晚于时间戳）→ 期望 200
$ry = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$tokY = $ry.data.token
try {
    $r = Invoke-WebRequest -Uri "$base/api/auth/me" -Headers @{ Authorization = "Bearer $tokY" } -UseBasicParsing
    "STEP-D me(tokenY): HTTP=$([int]$r.StatusCode)  <-- 期望 200"
} catch { "STEP-D me(tokenY): HTTP=$([int]$_.Exception.Response.StatusCode)  <-- 期望 200，异常！" }

# STEP-E: 清理验证用 key
"STEP-E DEL: $(Invoke-Redis @('DEL', $script:key))"
