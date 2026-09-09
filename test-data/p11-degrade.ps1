# p11 summary-degrade test: 3 more rounds on conv 36 while ALL model calls fail (invalid keys backend).
# Expect: compress warn + truncate degrade + chain continues (rewrite degrade, generation 3002 error)
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$conv = [IO.File]::ReadAllText("$PSScriptRoot\p11-conv.txt")
for ($i = 1; $i -le 3; $i++) {
    $body = '{"conversationId":' + $conv + ',"question":"p11-degrade round ' + $i + ': short answer please"}'
    $f = "$PSScriptRoot\p11-degrade$i.json"
    [IO.File]::WriteAllText($f, $body, (New-Object Text.UTF8Encoding $false))
    $out = & curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary ("@test-data\p11-degrade$i.json")
    $joined = $out -join ' '
    $tail = ($out | Select-Object -Last 2) -join ' | '
    Write-Output ("round {0}: tail=[{1}]" -f $i, $tail)
    Start-Sleep -Seconds 3
}
