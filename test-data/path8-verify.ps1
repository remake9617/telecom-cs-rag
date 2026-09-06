# path8-jwt 接口级实测（登出黑名单 / TTL / 性能 / 禁用失效 / 回归）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$base = "http://localhost:8080"

function Post-Json($url, $json) {
    Invoke-RestMethod -Uri $url -Method Post -Body ([Text.Encoding]::UTF8.GetBytes($json)) -ContentType "application/json"
}
function Get-Auth($url, $tok) {
    try { $r = Invoke-WebRequest -Uri $url -Headers @{ Authorization = "Bearer $tok" } -UseBasicParsing -TimeoutSec 10
          return @{ status = [int]$r.StatusCode; body = $r.Content } }
    catch { $resp = $_.Exception.Response
            if ($resp -ne $null) { return @{ status = [int]$resp.StatusCode; body = "" } }
            return @{ status = "ERR"; body = $_.Exception.Message } }
}

# ---- 1. 登录取 token A ----
$r1 = Post-Json "$base/api/auth/login" '{"username":"admin","password":"admin123"}'
"STEP1 login: code=$($r1.code) user=$($r1.data.user.username) role=$($r1.data.user.role)"
$tokA = $r1.data.token

# ---- 2. token A 打 me：期望 200 + code=0 ----
$m1 = Get-Auth "$base/api/auth/me" $tokA
"STEP2 me(before logout): HTTP=$($m1.status) body=$($m1.body)"

# ---- 3. POST /api/auth/logout ----
try {
    $lo = Invoke-RestMethod -Uri "$base/api/auth/logout" -Method Post -Headers @{ Authorization = "Bearer $tokA" }
    "STEP3 logout: HTTP=200 code=$($lo.code) message=$($lo.message)"
} catch { "STEP3 logout: ERR $($_.Exception.Message)" }

# ---- 4. 同一 token 再打 me：期望 HTTP 401（核心证据）----
$m2 = Get-Auth "$base/api/auth/me" $tokA
"STEP4 me(after logout): HTTP=$($m2.status) body=$($m2.body)"

# ---- 5. 重新登录取 token B，期望正常 ----
$r2 = Post-Json "$base/api/auth/login" '{"username":"admin","password":"admin123"}'
$tokB = $r2.data.token
$m3 = Get-Auth "$base/api/auth/me" $tokB
"STEP5 re-login me: HTTP=$($m3.status) body=$($m3.body)"

# ---- 6. 性能：连续 20 次 me（token B），记录总耗时与平均 ----
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$okCnt = 0
for ($i = 0; $i -lt 20; $i++) {
    $mm = Get-Auth "$base/api/auth/me" $tokB
    if ($mm.status -eq 200) { $okCnt++ }
}
$sw.Stop()
"STEP6 perf 20x me: ok=$okCnt/20 total=$($sw.ElapsedMilliseconds)ms avg=$([math]::Round($sw.ElapsedMilliseconds/20.0,1))ms"

# ---- 7. 回归：无 token / VISITOR code=1003 / ping ----
$noTok = Get-Auth "$base/api/auth/me" ""
"STEP7a me(no token): HTTP=$($noTok.status)"
try {
    $reg = Post-Json "$base/api/auth/register" '{"username":"p8visitor","password":"p8visitor123"}'
    "STEP7b register: code=$($reg.code) role=$($reg.data.user.role)"
    $vTok = $reg.data.token
    $kb = Get-Auth "$base/api/kb/bases" $vTok
    "STEP7c VISITOR /api/kb/bases: HTTP=$($kb.status) body=$($kb.body)"
} catch { "STEP7b/c: ERR $($_.Exception.Message)" }
$ping = Invoke-WebRequest -Uri "$base/api/health/ping" -UseBasicParsing
"STEP7d ping: HTTP=$([int]$ping.StatusCode) body=$($ping.Content)"
