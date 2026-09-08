# 路6 重测脚本7：正确端点上传（/api/kb/documents/upload?kbId=，temp）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$t = Get-Content "$PWD\test-data\p6-token.txt" -Raw
"--- 上传 mini.md 到 kbId=3:"
$up = & curl.exe -s -X POST 'http://192.168.25.129:8088/api/kb/documents/upload?kbId=3' -H "Authorization: Bearer $t" -F 'file=@test-data/p6-mini.md'
$up
$docId = ($up | ConvertFrom-Json).data.id
"docId=$docId"
"--- 轮询状态:"
for ($i=0; $i -lt 12; $i++) {
  Start-Sleep 5
  $s = Invoke-RestMethod "http://192.168.25.129:8088/api/kb/documents/$docId/status" -Headers @{ Authorization = "Bearer $t" }
  "$(Get-Date -Format 'HH:mm:ss') status=$($s.data.status) chunkCount=$($s.data.chunkCount)"
  if ($s.data.status -eq 'DONE' -or $s.data.status -eq 'FAILED') { break }
}
