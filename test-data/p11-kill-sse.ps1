# p11 DEF-081: long-running SSE request to be killed mid-stream
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
& curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-kill.json'
