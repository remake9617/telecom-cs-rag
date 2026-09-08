# 补测：RBAC（VISITOR 1003）+ logout 幂等（账号在 v1 已注册）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$B = 'http://192.168.25.129:8088'
function Hdr($t) { @{ Authorization = "Bearer $t" } }

"===== RBAC：p9visitor 登录 → VISITOR 打 kb/stats 期望 200+1003 ====="
$v = Invoke-RestMethod "$B/api/auth/login" -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"p9visitor","password":"p9Pass123456"}'))
"login code=$($v.code) role=$($v.data.user.role)"
$VT = $v.data.token
$r1 = Invoke-WebRequest "$B/api/kb/bases" -Headers (Hdr $VT) -UseBasicParsing
"kb/bases HTTP=$($r1.StatusCode) code=$((($r1.Content)|ConvertFrom-Json).code)"
$r2 = Invoke-WebRequest "$B/api/stats/overview" -Headers (Hdr $VT) -UseBasicParsing
"stats HTTP=$($r2.StatusCode) code=$((($r2.Content)|ConvertFrom-Json).code)"

"===== logout 幂等：p9v2 登录 → 登出 → me 401 → 再登出 code=0 ====="
$L0 = Invoke-RestMethod "$B/api/auth/login" -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"p9v2","password":"p9Pass123456"}'))
"login code=$($L0.code)"
$L = Invoke-RestMethod "$B/api/auth/logout" -Method Post -Headers (Hdr $L0.data.token)
"logout code=$($L.code)"
try { Invoke-WebRequest "$B/api/auth/me" -Headers (Hdr $L0.data.token) -UseBasicParsing | Out-Null; "me HTTP=200" } catch { "me HTTP=$($_.Exception.Response.StatusCode.value__)（期望 401）" }
$L2 = Invoke-RestMethod "$B/api/auth/logout" -Method Post -Headers (Hdr $L0.data.token)
"logout again code=$($L2.code)（期望 0=幂等）"
