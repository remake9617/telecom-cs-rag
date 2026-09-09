# p11 DEF-081 v2: start curl, kill it mid-stream after 4s, then verify partial persistence
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$outFile = "$PSScriptRoot\p11-kill-out.txt"
if (Test-Path $outFile) { Remove-Item $outFile -Force }
$p = Start-Process -FilePath curl.exe -ArgumentList @(
        '-s','-N','-X','POST','http://localhost:8080/api/qa/chat/stream',
        '-H','"Content-Type: application/json"',
        '-H',"`"Authorization: Bearer $T`"",
        '--data-binary','@test-data/p11-kill.json'
    ) -RedirectStandardOutput $outFile -PassThru -NoNewWindow
Start-Sleep -Seconds 7
Stop-Process -Id $p.Id -Force
Write-Output ("killed curl pid={0} after 7s" -f $p.Id)
Start-Sleep -Seconds 6
$raw = [IO.File]::ReadAllText($outFile)
$msgCount = ([regex]::Matches($raw, 'event:message')).Count
$hasDone = $raw -match 'event:done'
Write-Output ("client received: messageEvents={0} hasDone={1} (expect hasDone=False)" -f $msgCount, $hasDone)
$h = @{ Authorization = "Bearer $T" }
$convList = Invoke-RestMethod 'http://localhost:8080/api/qa/conversations' -Headers $h
$conv = $convList.data | Where-Object { $_.title -like 'p11-kill-test*' } | Select-Object -First 1
if (-not $conv) { Write-Output 'PARTIAL-CHECK-FAIL: conversation not found'; exit 1 }
$msgs = Invoke-RestMethod ("http://localhost:8080/api/qa/conversations/" + $conv.id + "/messages") -Headers $h
$asst = $msgs.data | Where-Object { $_.role -eq 'assistant' } | Select-Object -Last 1
Write-Output ("convId={0} totalMsgs={1} assistantContentLen={2} (expect >0, partial persisted)" -f $conv.id, $msgs.data.Count, $(if ($asst) { $asst.content.Length } else { -1 }))
