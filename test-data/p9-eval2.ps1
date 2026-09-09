# p9 runtime verification, part 2: cross-KB run fix + DISLIKE reflux + stats read side
$ErrorActionPreference = 'Stop'
$root = "c:\Users\17962\Desktop\Big_Java_Project\AIBishe"
$base = "http://localhost:8080"

function Read-EnvKey([string]$key) {
    $line = Get-Content "$root\.env" | Where-Object { $_ -like "$key=*" } | Select-Object -First 1
    return $line.Substring($key.Length + 1)
}
function Post-Json([string]$uri, [object]$obj, $headers) {
    $json = $obj | ConvertTo-Json -Depth 6 -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($json)
    $r = Invoke-RestMethod -Uri $uri -Method Post -ContentType "application/json; charset=utf-8" -Body $bytes -Headers $headers
    if ($r.code -ne 0) { throw "POST $uri failed: code=$($r.code) msg=$($r.message)" }
    return $r.data
}
function Get-Api([string]$uri, $headers) {
    $r = Invoke-RestMethod -Uri $uri -Method Get -Headers $headers
    if ($r.code -ne 0) { throw "GET $uri failed: code=$($r.code) msg=$($r.message)" }
    return $r.data
}
function Wait-Run([long]$rid, $headers) {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $run = Get-Api "$base/api/eval/runs/$rid" $headers
        if ($run.status -ne 'RUNNING') { return $run }
    }
    return $run
}

$adminPw = Read-EnvKey 'ADMIN_DEFAULT_PASSWORD'
$login = Post-Json "$base/api/auth/login" @{ username = 'admin'; password = $adminPw } @{}
$H = @{ Authorization = "Bearer $($login.token)" }
Write-Output "[1] login OK"

# ---------- A. cross-KB run (after loadEvaluableCases fix): mini case (no case kbId) forced under kb 1 ----------
$cases = Get-Api "$base/api/eval/cases" $H
$miniCase2 = $cases | Where-Object { $_.note -like 'p9 mini-doc case NO kbId*' } | Select-Object -First 1
if (-not $miniCase2) { throw "miniCase2 not found" }
$rid = (Post-Json "$base/api/eval/runs" @{ mode = 'HYBRID_RERANK'; caseIds = @($miniCase2.id); kbId = 1 } $H).runId
$run = Wait-Run $rid $H
Write-Output ("[A] cross-KB run={0} kbId={1} status={2} cases={3} recall={4} hitRate={5} refP={6} (EXPECT cases=1 recall=0)" -f $rid, $run.kbId, $run.status, $run.caseCount, $run.recallAtK, $run.hitRate, $run.refPrecision)
$rid2 = (Post-Json "$base/api/eval/runs" @{ mode = 'HYBRID_RERANK'; caseIds = @($miniCase2.id); kbId = 7 } $H).runId
$run2 = Wait-Run $rid2 $H
Write-Output ("[A] own-KB   run={0} kbId={1} status={2} cases={3} recall={4} hitRate={5} refP={6} (EXPECT recall=1)" -f $rid2, $run2.kbId, $run2.status, $run2.caseCount, $run2.recallAtK, $run2.hitRate, $run2.refPrecision)

# ---------- B. DISLIKE reflux ----------
$q2 = (Get-Content "$root\test-data\q2.json" -Raw -Encoding UTF8 | ConvertFrom-Json).question
$sseBody = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json @{ question = $q2 } -Compress))
$sseFile = "$root\test-data\p9-sse-req.json"
[IO.File]::WriteAllBytes($sseFile, $sseBody)
$sseOut = curl.exe -s -N --max-time 120 -H "Authorization: Bearer $($login.token)" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$sseFile" "$base/api/qa/chat/stream"
$sseOut | Out-File "$root\test-data\p9-sse.log" -Encoding UTF8
$msgId = $null
for ($i = 0; $i -lt $sseOut.Count; $i++) {
    if ($sseOut[$i] -match '^event:\s*done' -and $i + 1 -lt $sseOut.Count -and $sseOut[$i + 1] -match '^data:(.*)$') {
        $payload = $Matches[1]
        if ($payload -match '"messageId":(\d+)') { $msgId = [long]$Matches[1] }
        Write-Output "[B] done payload: $($payload.Trim())"
        break
    }
}
Write-Output "[B] SSE done messageId=$msgId"
if (-not $msgId) { throw "no done.messageId parsed" }

Post-Json "$base/api/feedback" @{ messageId = $msgId; type = 'DISLIKE'; comment = 'p9 dislike reflux test' } $H | Out-Null
Write-Output "[B] feedback DISLIKE posted on message $msgId"
$n1 = Post-Json "$base/api/eval/cases/dislike-sync" @{} $H
$n2 = Post-Json "$base/api/eval/cases/dislike-sync" @{} $H
Write-Output "[B] dislike-sync #1 inserted=$n1 (expect 1); #2 inserted=$n2 (expect 0, idempotent)"
$cases = Get-Api "$base/api/eval/cases" $H
$dis = $cases | Where-Object { $_.source -eq 'DISLIKE' }
$dis | ForEach-Object { Write-Output "[B] DISLIKE case id=$($_.id) note=$($_.note) chunks=$($_.expectedChunkIds) docs=$($_.expectedDocIds)" }

# ---------- C. cs-stats read side ----------
$stats = Get-Api "$base/api/stats/eval-runs?limit=20" $H
Write-Output "[C] /api/stats/eval-runs rows=$($stats.Count)"
$stats | Select-Object -First 12 | ForEach-Object { Write-Output ("    run={0} mode={1,-14} kb={2} status={3} recall={4} mrr={5}" -f $_.id, $_.mode, $_.kbId, $_.status, $_.recallAtK, $_.mrr) }

Write-Output "DONE p9-eval2.ps1"
