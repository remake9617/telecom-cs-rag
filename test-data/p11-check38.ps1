# p11: re-check conv 38 partial persistence
$T = [IO.File]::ReadAllText("$PSScriptRoot\p11-token.txt")
$h = @{ Authorization = "Bearer $T" }
$msgs = Invoke-RestMethod 'http://localhost:8080/api/qa/conversations/38/messages' -Headers $h
Write-Output ("conv38 msgs={0}" -f $msgs.data.Count)
foreach ($m in $msgs.data) {
    Write-Output ("  id={0} role={1} contentLen={2} tokenCost={3}" -f $m.id, $m.role, $m.content.Length, $m.tokenCost)
}
