# p11 memory-summary test: 9 QA rounds in one conversation, round 8 assistant append hits threshold 16
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$conv = $null
for ($i = 1; $i -le 9; $i++) {
    if ($conv) {
        $body = '{"conversationId":' + $conv + ',"question":"p11-memory round ' + $i + ': about telecom 5G plan, answer in less than 15 words"}'
    } else {
        $body = '{"conversationId":null,"question":"p11-memory round 1: about telecom 5G plan, answer in less than 15 words"}'
    }
    $f = "$PSScriptRoot\p11-round$i.json"
    [IO.File]::WriteAllText($f, $body, (New-Object Text.UTF8Encoding $false))
    $out = & curl.exe -s -N -X POST 'http://localhost:8080/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary ("@test-data\p11-round$i.json")
    $doneLine = $out | Where-Object { $_ -match '"messageId"' } | Select-Object -First 1
    if ($doneLine) {
        $done = ($doneLine -replace '^data:\s*','') | ConvertFrom-Json
        $conv = $done.conversationId
        Write-Output ("round {0}: conv={1} msg={2} tokenCost={3}" -f $i, $done.conversationId, $done.messageId, $done.tokenCost)
    } else {
        Write-Output ("round {0}: NO DONE LINE, last output:" -f $i)
        $out | Select-Object -Last 4 | ForEach-Object { Write-Output ("  " + $_) }
    }
    Start-Sleep -Seconds 8
}
Write-Output ("final conv=" + $conv)
[IO.File]::WriteAllText("$PSScriptRoot\p11-conv.txt", "$conv")
