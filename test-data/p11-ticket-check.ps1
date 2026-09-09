# p11: verify only ONE ticket with probe question exists in DB (via mine list)
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$h = @{ Authorization = "Bearer $T" }
$mine = Invoke-RestMethod 'http://localhost:8080/api/ticket/mine' -Headers $h
$probe = @($mine.data | Where-Object { $_.question -eq 'p11-idempotency-probe' })
Write-Output ("probe tickets total={0} (expect 2: one in-window id=11, one after TTL id=12)" -f $probe.Count)
$probe | ForEach-Object { Write-Output ("  id={0} status={1}" -f $_.id, $_.status) }
