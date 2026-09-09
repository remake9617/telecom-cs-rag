# p11: capture ONE raw rate-limited SSE response for evidence (admin window still saturated)
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
Write-Output '--- raw response of an over-threshold request ---'
& curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-empty.json'
Write-Output ''
Write-Output '--- raw response with browser-like Accept header (Accept: text/event-stream) ---'
& curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H 'Accept: text/event-stream' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-empty.json'
