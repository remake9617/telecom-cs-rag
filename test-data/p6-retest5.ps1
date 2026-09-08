# 路6 重测脚本5：建 p6 库 + 上传文档 + 轮询状态（temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$t = Get-Content "$PWD\test-data\p6-token.txt" -Raw
$h = @{ Authorization = "Bearer $t" }
"--- create kb:"
$kb = Invoke-RestMethod 'http://192.168.25.129:8088/api/kb/bases' -Method Post -ContentType 'application/json;charset=utf-8' -Headers $h -Body ([Text.Encoding]::UTF8.GetBytes('{"name":"p6-retest-kb","description":"p6 容器重测知识库"}'))
"code=$($kb.code) kbId=$($kb.data.id) name=$($kb.data.name)"
$kbId = $kb.data.id
"--- upload telecom-plans.md:"
$up = & curl.exe -s -X POST "http://192.168.25.129:8088/api/kb/$kbId/documents" -H "Authorization: Bearer $t" -F "file=@test-data/telecom-plans.md"
$up
$docId = ($up | ConvertFrom-Json).data.id
"--- poll doc status:"
for ($i=0; $i -lt 12; $i++) {
  Start-Sleep 5
  $d = Invoke-RestMethod "http://192.168.25.129:8088/api/kb/documents?current=1&size=10" -Headers $h
  $doc = $d.data.records | Where-Object { $_.id -eq $docId }
  "$(Get-Date -Format 'HH:mm:ss') status=$($doc.status) chunkCount=$($doc.chunkCount)"
  if ($doc.status -eq 'DONE' -or $doc.status -eq 'FAILED') { break }
}
"--- qa against new doc:"
[IO.File]::WriteAllText("$PWD\test-data\p6-q4.json", '{"question":"p6-retest-融合套餐包含哪些内容","conversationId":null}', (New-Object Text.UTF8Encoding $false))
& curl.exe -s -N -X POST 'http://192.168.25.129:8088/api/qa/chat/stream' -H 'Content-Type: application/json' -H "Authorization: Bearer $t" --data-binary "@test-data/p6-q4.json" | ForEach-Object { $line = $_; if ($line -match '^event:' -or $line -match 'references' -or $line -match 'done') { "$(Get-Date -Format 'HH:mm:ss.fff') | $($line.Substring(0,[Math]::Min(120,$line.Length)))" } }
