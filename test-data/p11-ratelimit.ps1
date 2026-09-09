# p11 rate-limit sequential test: 12 rapid requests with empty question (zero model cost).
# Allowed ones get SSE error 1001 (empty-question guard after passing rate limit),
# limited ones get SSE error 1005 (rate limit rejects BEFORE any business logic).
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$allowed = 0; $limited = 0
for ($i = 1; $i -le 12; $i++) {
    $out = & curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-empty.json'
    $joined = $out -join ' '
    if ($joined -match '"code":1005') { $limited++ }
    elseif ($joined -match '"code":1001') { $allowed++ }
    else { Write-Output ("req {0}: UNEXPECTED: {1}" -f $i, $joined) }
}
Write-Output ("sequential result: allowed={0} limited={1} (expect 10 / 2)" -f $allowed, $limited)
