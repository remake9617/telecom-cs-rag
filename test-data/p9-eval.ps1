# p9 runtime verification script (path9 eval pipeline)
# All CJK content is loaded from test-data JSON files - no CJK literals in this script (GBK-safe).
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

# ---------- 1. login ----------
$adminPw = Read-EnvKey 'ADMIN_DEFAULT_PASSWORD'
$login = Post-Json "$base/api/auth/login" @{ username = 'admin'; password = $adminPw } @{}
$H = @{ Authorization = "Bearer $($login.token)" }
Write-Output "[1] login OK, user=$($login.user.username) role=$($login.user.role)"

# ---------- 2. existing KB list ----------
$kbs = Get-Api "$base/api/kb/bases" $H
$kbs | ForEach-Object { Write-Output "[2] existing kb id=$($_.id) name=$($_.name)" }

# ---------- 3. baseline determinism: /api/kb/search with default RetrievalRequest (mode/kbId/minScore all null) ----------
$q1 = (Get-Content "$root\test-data\q1.json" -Raw -Encoding UTF8 | ConvertFrom-Json).question
$q2 = (Get-Content "$root\test-data\q2.json" -Raw -Encoding UTF8 | ConvertFrom-Json).question
$q3 = (Get-Content "$root\test-data\q3.json" -Raw -Encoding UTF8 | ConvertFrom-Json).question

$responses = @()
for ($i = 1; $i -le 3; $i++) {
    $responses += (ConvertTo-Json (Post-Json "$base/api/kb/search" @{ query = $q1; topK = 5 } $H) -Depth 8 -Compress)
}
if ($responses[0] -eq $responses[1] -and $responses[1] -eq $responses[2]) { Write-Output "[3] baseline determinism: 3 calls IDENTICAL (full-response JSON diff)" } else { Write-Output "[3] baseline determinism: DIFF DETECTED!"; $responses | Out-File "$root\test-data\p9-baseline-diff.txt" }
$responses[0] | Out-File "$root\test-data\p9-baseline-q1.json" -Encoding UTF8
$first = $responses[0] | ConvertFrom-Json
Write-Output "[3] q1 default-path hits: $(@($first).Count)"
$first | ForEach-Object { Write-Output ("    chunkId={0} docId={1} score={2} rerank={3}" -f $_.chunkId, $_.docId, $_.score, $_.rerankScore) }

# ---------- 4. create eval KB ----------
$newKb = Post-Json "$base/api/kb/bases" @{ name = "p9-eval-kb"; description = "path9 kbId filter eval corpus" } $H
$newKbId = $newKb.id
Write-Output "[4] created kb id=$newKbId name=$($newKb.name)"

# ---------- 5. upload mini doc ----------
$up = curl.exe -s -H "Authorization: Bearer $($login.token)" -F "file=@$root\test-data\p6-mini.md" "$base/api/kb/documents/upload?kbId=$newKbId" | ConvertFrom-Json
if ($up.code -ne 0) { throw "upload failed: $($up.message)" }
$docId = $up.data.id
Write-Output "[5] uploaded doc id=$docId kb=$newKbId status=$($up.data.status)"

# ---------- 6. poll status DONE ----------
$st = $null
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $st = Get-Api "$base/api/kb/documents/$docId/status" $H
    if ($st.status -eq 'DONE' -or $st.status -eq 'FAILED') { break }
}
Write-Output "[6] doc status=$($st.status) chunkCount=$($st.chunkCount)"
if ($st.status -ne 'DONE') { throw "mini doc not DONE" }

# ---------- 7. discover expected chunks ----------
# mini doc case: search corpus-specific term, keep hits belonging to new doc
$miniQuery = "39 5GB"
$miniHits = Post-Json "$base/api/kb/search" @{ query = $miniQuery; topK = 10 } $H
$miniChunkIds = @($miniHits | Where-Object { $_.docId -eq $docId } | Select-Object -ExpandProperty chunkId -Unique)
Write-Output "[7] mini doc expected chunks: $($miniChunkIds -join ',')"

# seed cases q1~q3: expected = top-2 chunk ids from default-path search (smoke-level annotation, batch1 will expand)
$seedMap = @(
    @{ q = $q1; src = 'MANUAL' },
    @{ q = $q2; src = 'MANUAL' },
    @{ q = $q3; src = 'MANUAL' }
)
$seedCaseIds = @()
$idx = 0
foreach ($s in $seedMap) {
    $idx++
    $hits = Post-Json "$base/api/kb/search" @{ query = $s.q; topK = 5 } $H
    $cids = @($hits | Select-Object -First 2 -ExpandProperty chunkId)
    $dids = @($hits | Select-Object -First 2 -ExpandProperty docId -Unique)
    # NOTE: seed cases intentionally have NO kbId (top-2 chunks may span KBs); retrieval runs unfiltered for ablation
    $c = Post-Json "$base/api/eval/cases" @{ question = $s.q; expectedChunkIds = $cids; expectedDocIds = $dids; source = $s.src; note = "p9 seed case $idx (top-2 of default path)" } $H
    $seedCaseIds += $c.id
    Write-Output "[7] seed case id=$($c.id) q=$($s.q.Substring(0,10))... expectedChunks=$($cids -join ',')"
}
$miniCase = Post-Json "$base/api/eval/cases" @{ kbId = $newKbId; question = $miniQuery; expectedChunkIds = $miniChunkIds; source = 'MANUAL'; note = "p9 mini-doc case kb=$newKbId" } $H
Write-Output "[7] mini case id=$($miniCase.id)"
# second mini case WITHOUT case-level kbId, for run-level kbId cross-contamination test
$miniCase2 = Post-Json "$base/api/eval/cases" @{ question = $miniQuery; expectedChunkIds = $miniChunkIds; source = 'MANUAL'; note = "p9 mini-doc case NO kbId (cross test)" } $H
Write-Output "[7] mini case (no kbId) id=$($miniCase2.id)"

# ---------- 8. four-mode ablation (seeds only) ----------
$modes = @('VECTOR', 'BM25', 'HYBRID', 'HYBRID_RERANK')
foreach ($m in $modes) {
    $runId = (Post-Json "$base/api/eval/runs" @{ mode = $m; caseIds = $seedCaseIds; topK = 5 } $H).runId
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $run = Get-Api "$base/api/eval/runs/$runId" $H
        if ($run.status -ne 'RUNNING') { break }
    }
    Write-Output ("[8] run={0} mode={1,-14} status={2} cases={3} recall={4} precision={5} mrr={6} ndcg={7} hitRate={8} refP={9}" -f `
        $runId, $m, $run.status, $run.caseCount, $run.recallAtK, $run.precisionAtK, $run.mrr, $run.ndcg, $run.hitRate, $run.refPrecision)
    if ($run.status -eq 'FAILED') { Write-Output "    error: $($run.errorMsg)" }
}

# ---------- 9. kbId filter: no cross-KB contamination ----------
$r1 = (Post-Json "$base/api/eval/runs" @{ mode = 'HYBRID_RERANK'; kbId = $newKbId } $H).runId   # loads only kb-scoped mini case
$r2 = (Post-Json "$base/api/eval/runs" @{ mode = 'HYBRID_RERANK'; caseIds = @($miniCase2.id); kbId = 1 } $H).runId  # cross-KB: mini corpus retrieved under kb 1
foreach ($rid in @($r1, $r2)) {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $run = Get-Api "$base/api/eval/runs/$rid" $H
        if ($run.status -ne 'RUNNING') { break }
    }
    Write-Output ("[9] run={0} kbId={1} status={2} cases={3} recall={4} hitRate={5} refP={6}" -f $rid, $run.kbId, $run.status, $run.caseCount, $run.recallAtK, $run.hitRate, $run.refPrecision)
}
Write-Output "[9] EXPECT: kbId=$newKbId run recall=1.0 (own corpus); kbId=1 run recall=0 (no cross-KB hits)"

# ---------- 10. minScore filter ----------
$rs1 = (Post-Json "$base/api/eval/runs" @{ mode = 'HYBRID_RERANK'; caseIds = $seedCaseIds; topK = 5 } $H).runId
$rs2 = (Post-Json "$base/api/eval/runs" @{ mode = 'HYBRID_RERANK'; caseIds = $seedCaseIds; topK = 5; minScore = 2.0 } $H).runId
foreach ($rid in @($rs1, $rs2)) {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        $run = Get-Api "$base/api/eval/runs/$rid" $H
        if ($run.status -ne 'RUNNING') { break }
    }
    Write-Output ("[10] run={0} minScore={1} status={2} recall={3} precision={4}" -f $rid, $(if ($rid -eq $rs2) {'2.0'} else {'null'}), $run.status, $run.recallAtK, $run.precisionAtK)
}
Write-Output "[10] EXPECT: minScore=2.0 (above any rerank/RRF score) filters everything -> recall=0"

# ---------- 11. DISLIKE reflux ----------
$sseBody = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json @{ question = $q2 } -Compress))
$sseFile = "$root\test-data\p9-sse-req.json"
[IO.File]::WriteAllBytes($sseFile, $sseBody)
$sseOut = curl.exe -s -N --max-time 120 -H "Authorization: Bearer $($login.token)" -H "Content-Type: application/json; charset=utf-8" --data-binary "@$sseFile" "$base/api/qa/chat/stream"
$sseOut | Out-File "$root\test-data\p9-sse.log" -Encoding UTF8
$doneLine = ($sseOut | Select-String -Pattern 'event:\s*done' -Context 0,1)
$msgId = $null
if ($doneLine) {
    $dataLine = ($doneLine -split "`n") | Where-Object { $_ -match '^data:' } | Select-Object -First 1
    if ($dataLine -match '"messageId":(\d+)') { $msgId = [long]$Matches[1] }
}
Write-Output "[11] SSE done received, messageId=$msgId"
if (-not $msgId) { throw "no done.messageId in SSE" }

$fb = Post-Json "$base/api/feedback" @{ messageId = $msgId; type = 'DISLIKE'; comment = 'p9 dislike reflux test' } $H
Write-Output "[11] feedback DISLIKE posted"

$n1 = Post-Json "$base/api/eval/cases/dislike-sync" @{} $H
Write-Output "[11] dislike-sync #1 inserted=$n1 (expect >=1)"
$n2 = Post-Json "$base/api/eval/cases/dislike-sync" @{} $H
Write-Output "[11] dislike-sync #2 inserted=$n2 (expect 0, idempotent)"

$cases = Get-Api "$base/api/eval/cases" $H
$dis = $cases | Where-Object { $_.source -eq 'DISLIKE' }
$dis | ForEach-Object { Write-Output "[11] DISLIKE case id=$($_.id) note=$($_.note) qLen=$($_.question.Length)" }

# ---------- 12. cs-stats read side ----------
$stats = Get-Api "$base/api/stats/eval-runs?limit=20" $H
Write-Output "[12] /api/stats/eval-runs rows=$($stats.Count)"
$stats | Select-Object -First 12 | ForEach-Object { Write-Output ("    run={0} mode={1,-14} kb={2} status={3} recall={4}" -f $_.id, $_.mode, $_.kbId, $_.status, $_.recallAtK) }

Write-Output "DONE p9-eval.ps1"
