# p11 idempotency test for POST /api/ticket
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$h = @{ Authorization = "Bearer $T" }
$body = '{"conversationId":null,"question":"p11-idempotency-probe"}'
$b1 = Invoke-RestMethod 'http://localhost:8080/api/ticket' -Method Post -ContentType 'application/json' -Headers $h -Body $body
Write-Output ("create1 code={0} id={1}" -f $b1.code, $b1.data.id)
$b2 = Invoke-RestMethod 'http://localhost:8080/api/ticket' -Method Post -ContentType 'application/json' -Headers $h -Body $body
Write-Output ("create2 code={0} id={1} (expect same id)" -f $b2.code, $b2.data.id)
if ($b1.data.id -eq $b2.data.id) { Write-Output 'IDEMPOTENT-PASS' } else { Write-Output 'IDEMPOTENT-FAIL' }
$mine = Invoke-RestMethod 'http://localhost:8080/api/ticket/mine' -Headers $h
$count = ($mine.data | Where-Object { $_.question -eq 'p11-idempotency-probe' }).Count
Write-Output ("tickets with probe question: {0} (expect 1)" -f $count)
Write-Output 'sleeping 65s for idempotency TTL expiry...'
Start-Sleep -Seconds 65
$b3 = Invoke-RestMethod 'http://localhost:8080/api/ticket' -Method Post -ContentType 'application/json' -Headers $h -Body $body
Write-Output ("create3 after TTL code={0} id={1} (expect NEW id)" -f $b3.code, $b3.data.id)
if ($b3.data.id -ne $b1.data.id) { Write-Output 'TTL-EXPIRY-PASS' } else { Write-Output 'TTL-EXPIRY-FAIL' }
