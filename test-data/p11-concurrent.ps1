# p11 rate-limit concurrency test: 20 concurrent requests from a FRESH user (p11blast).
# Lua atomicity => allowed count must NOT exceed threshold 10.
$root = 'c:\Users\17962\Desktop\Big_Java_Project\AIBishe'
$reg = $null
try { $reg = Invoke-RestMethod 'http://localhost:8080/api/auth/register' -Method Post -ContentType 'application/json' -Body '{"username":"p11blast","password":"p11Blast123456"}' } catch {}
if (-not $reg.data.token) {
    $lg = Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"p11blast","password":"p11Blast123456"}'
    $T = $lg.data.token
    Write-Output ("login existing p11blast code={0}" -f $lg.code)
} else {
    $T = $reg.data.token
    Write-Output ("register p11blast code={0} role={1}" -f $reg.code, $reg.data.user.role)
}
$jobs = @()
1..20 | ForEach-Object {
    $jobs += Start-Job -ScriptBlock {
        param($t)
        $out = & curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $t" --data-binary '@c:\Users\17962\Desktop\Big_Java_Project\AIBishe\test-data\p11-empty.json'
        $joined = $out -join ' '
        if ($joined -match '"code":1005') { 'L' } elseif ($joined -match '"code":1001') { 'A' } else { 'U' }
    } -ArgumentList $T
}
$jobs | Wait-Job -Timeout 120 | Out-Null
$results = $jobs | Receive-Job
$jobs | Remove-Job -Force
$allowed = ($results | Where-Object { $_ -eq 'A' }).Count
$limited = ($results | Where-Object { $_ -eq 'L' }).Count
$unknown = ($results | Where-Object { $_ -eq 'U' }).Count
Write-Output ("concurrent-20 result: allowed={0} limited={1} unknown={2} (expect allowed<=10)" -f $allowed, $limited, $unknown)
