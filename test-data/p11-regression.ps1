# =====================================================================
# 集成收口 v3：A 轮 27 端点全回归 + DEF-085/088 验证 + p11 演示数据重建
# 教训：本文件必须保持 UTF-8 with BOM（PS 5.1 无 BOM 按 GBK 解析，中文即炸）；
#       函数名禁用 H（Get-History 别名遮蔽），用 Hdr
# =====================================================================
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$B = 'http://localhost:8080'
function Hdr($t) { @{ Authorization = "Bearer $t" } }

"===== ⓪ 登录 ====="
$admin = Invoke-RestMethod "$B/api/auth/login" -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"admin123"}'))
$T = $admin.data.token
"login code=$($admin.code) localNow=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

"===== ① DEF-085：未匹配 /api 路径（带 token，期望 code=1004）====="
$r = Invoke-WebRequest "$B/api/definitely-not-a-path" -Headers (Hdr $T) -UseBasicParsing
"HTTP=$($r.StatusCode) $($r.Content)"

"===== ② DEF-088：不传 question（期望 SSE error 1001 且会话数不变）====="
$before = (Invoke-RestMethod "$B/api/qa/conversations" -Headers (Hdr $T)).data.Count
[IO.File]::WriteAllText("$PWD\test-data\p11-noq.json", '{"conversationId":null}', (New-Object Text.UTF8Encoding $false))
& curl.exe -s -N -X POST "$B/api/qa/chat/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-noq.json'
""
$after = (Invoke-RestMethod "$B/api/qa/conversations" -Headers (Hdr $T)).data.Count
"conversations before=$before after=$after"

"===== ③ kb：建库/复用 → 上传/复用 → 列表形状 ====="
$kbList = Invoke-RestMethod "$B/api/kb/bases" -Headers (Hdr $T)
$exist = $kbList.data | Where-Object { $_.name -eq 'p11-演示知识库' } | Select-Object -First 1
if ($exist) { $kbId = $exist.id; "reuse kbId=$kbId" }
else {
  $kb = Invoke-RestMethod "$B/api/kb/bases" -Method Post -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes('{"name":"p11-演示知识库","description":"批次0 收口重建的演示库"}'))
  $kbId = $kb.data.id
  "create code=$($kb.code) kbId=$kbId status=$($kb.data.status) model=$($kb.data.embeddingModel)"
}
if (-not $kbId) { "FATAL: kbId null"; exit 1 }
$docs = Invoke-RestMethod "$B/api/kb/documents?kbId=$kbId&current=1&size=10" -Headers (Hdr $T)
$doc = $docs.data.records | Where-Object { $_.status -eq 'DONE' } | Select-Object -First 1
if ($doc) { $docId = $doc.id; "reuse docId=$docId chunkCount=$($doc.chunkCount)" }
else {
  $up = & curl.exe -s -X POST "$B/api/kb/documents/upload?kbId=$kbId" -H "Authorization: Bearer $T" -F 'file=@test-data/telecom-plans.md'
  $upJ = $up | ConvertFrom-Json
  "upload code=$($upJ.code) docId=$($upJ.data.id)"
  $docId = $upJ.data.id
  for ($i=0; $i -lt 15; $i++) {
    Start-Sleep 5
    $s = Invoke-RestMethod "$B/api/kb/documents/$docId/status" -Headers (Hdr $T)
    if ($s.data.status -eq 'DONE' -or $s.data.status -eq 'FAILED') { "status=$($s.data.status) chunkCount=$($s.data.chunkCount)"; break }
    "poll$i status=$($s.data.status)"
  }
}
$docs = Invoke-RestMethod "$B/api/kb/documents?current=1&size=10" -Headers (Hdr $T)
$keys = ($docs.data.records[0] | Get-Member -MemberType NoteProperty).Name -join ','
"documents keys=[$keys] total=$($docs.data.total) current=$($docs.data.current) size=$($docs.data.size)"

"===== ④ kb/search（管理员检索调试端点）====="
$sr = Invoke-RestMethod "$B/api/kb/search" -Method Post -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes('{"query":"5G套餐包含多少流量","topK":3}'))
"search code=$($sr.code) hits=$($sr.data.Count) firstScore=$($sr.data[0].score)"

"===== ⑤ kb/url 抓取入库 + DELETE（用后即删）====="
$u = Invoke-RestMethod "$B/api/kb/documents/url" -Method Post -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes('{"url":"https://example.com/","kbId":' + $kbId + '}'))
"url code=$($u.code) urlDocId=$($u.data.id) sourceType=$($u.data.sourceType)"
if ($u.data.id) {
  $del = Invoke-RestMethod "$B/api/kb/documents/$($u.data.id)" -Method Delete -Headers (Hdr $T)
  "delete url-doc code=$($del.code)"
}

"===== ⑥ qa SSE（q1，逐包时间戳）====="
[IO.File]::WriteAllText("$PWD\test-data\p11-q1.json", '{"question":"p11-129套餐包含多少流量","conversationId":null}', (New-Object Text.UTF8Encoding $false))
$sse1 = & curl.exe -s -N -X POST "$B/api/qa/chat/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-q1.json' | ForEach-Object { "$(Get-Date -Format 'HH:mm:ss.fff')|$_" }
"sse1 lines=$($sse1.Count) first=$($sse1[0])"
$sse1 | Select-Object -Last 3
$doneLine = $sse1 | Where-Object { $_ -match '"messageId"' } | Select-Object -First 1
$done1 = (($doneLine -split '\|',2)[1] -replace '^data:\s*','') | ConvertFrom-Json
"conv=$($done1.conversationId) msg=$($done1.messageId) tokenCost=$($done1.tokenCost)"
$convId = $done1.conversationId; $msgA = $done1.messageId
if (-not $convId) { "FATAL: convId null"; exit 1 }

"===== ⑦ qa SSE（q2 追问，复用会话）====="
[IO.File]::WriteAllText("$PWD\test-data\p11-q2.json", ('{"conversationId":' + $convId + ',"question":"p11-那它的通话分钟是多少"}'), (New-Object Text.UTF8Encoding $false))
$sse2 = & curl.exe -s -N -X POST "$B/api/qa/chat/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-q2.json' | ForEach-Object { "$(Get-Date -Format 'HH:mm:ss.fff')|$_" }
$doneLine2 = $sse2 | Where-Object { $_ -match '"messageId"' } | Select-Object -First 1
$done2 = (($doneLine2 -split '\|',2)[1] -replace '^data:\s*','') | ConvertFrom-Json
"conv2=$($done2.conversationId) msg2=$($done2.messageId) tokenCost2=$($done2.tokenCost)"
$msgB = $done2.messageId

"===== ⑧ 历史回放：引用溯源 ====="
$hist = Invoke-RestMethod "$B/api/qa/conversations/$convId/messages" -Headers (Hdr $T)
"code=$($hist.code) msgs=$($hist.data.Count)"
$asst = $hist.data | Where-Object { $_.role -eq 'assistant' } | Select-Object -First 1
"ref0 docTitle=$($asst.references[0].docTitle) score=$($asst.references[0].score) chunkTextLen=$($asst.references[0].chunkText.Length)"
"msgVO keys=[$(($hist.data[0] | Get-Member -MemberType NoteProperty).Name -join ',')]"

"===== ⑨ feedback 点赞/点踩 ====="
$f1 = Invoke-RestMethod "$B/api/feedback" -Method Post -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes(('{"messageId":' + $msgA + ',"type":"LIKE"}')))
$f2 = Invoke-RestMethod "$B/api/feedback" -Method Post -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes(('{"messageId":' + $msgB + ',"type":"DISLIKE"}')))
"like code=$($f1.code) dislike code=$($f2.code)"

"===== ⑩ ticket：手动建单 → 管理员回复 → 列表 ====="
$tk = Invoke-RestMethod "$B/api/ticket" -Method Post -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes('{"question":"p11-手动创建的演示工单"}'))
"create code=$($tk.code) ticketId=$($tk.data.id) status=$($tk.data.status)"
$rp = Invoke-RestMethod "$B/api/ticket/$($tk.data.id)/reply" -Method Put -ContentType 'application/json;charset=utf-8' -Headers (Hdr $T) -Body ([Text.Encoding]::UTF8.GetBytes('{"reply":"p11-演示回复：您好，问题已处理。"}'))
"reply code=$($rp.code) status=$($rp.data.status) repliedAt=$($rp.data.repliedAt)"
$tl = Invoke-RestMethod "$B/api/ticket?status=OPEN&current=1&size=10" -Headers (Hdr $T)
"ticketList code=$($tl.code) total=$($tl.data.total)"

"===== ⑪ stats：overview / hot-questions / trend ====="
$ov = Invoke-RestMethod "$B/api/stats/overview" -Headers (Hdr $T)
"overview code=$($ov.code) ask=$($ov.data.askCount) resolveRate=$($ov.data.resolveRate) ticketRate=$($ov.data.ticketRate) open=$($ov.data.openTicketCount) kb=$($ov.data.kbCount) doc=$($ov.data.docCount) user=$($ov.data.userCount)"
$hq = Invoke-RestMethod "$B/api/stats/hot-questions?limit=10" -Headers (Hdr $T)
"hot code=$($hq.code) top=$($hq.data[0].question)/$($hq.data[0].count)"
$tr = Invoke-RestMethod "$B/api/stats/trend?days=7" -Headers (Hdr $T)
"trend code=$($tr.code) rows=$($tr.data.Count) date0=$($tr.data[0].date)（应 yyyy-MM-dd）"

"===== ⑫ system/users 分页 ====="
$us = Invoke-RestMethod "$B/api/system/users?current=1&size=10" -Headers (Hdr $T)
"users code=$($us.code) total=$($us.data.total) first=$($us.data.records[0].username)"

"===== ⑬ RBAC：VISITOR 1003 / 无 token 401 / ping 200 ====="
$reg = Invoke-RestMethod "$B/api/auth/register" -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"p11visitor","password":"p11Pass123456"}'))
"register code=$($reg.code) role=$($reg.data.user.role)"
$VT = $reg.data.token
$r1 = Invoke-WebRequest "$B/api/kb/bases" -Headers (Hdr $VT) -UseBasicParsing
"visitor kb/bases HTTP=$($r1.StatusCode) code=$((($r1.Content)|ConvertFrom-Json).code)"
$r2 = Invoke-WebRequest "$B/api/stats/overview" -Headers (Hdr $VT) -UseBasicParsing
"visitor stats HTTP=$($r2.StatusCode) code=$((($r2.Content)|ConvertFrom-Json).code)"
try { Invoke-WebRequest "$B/api/health/ai" -UseBasicParsing | Out-Null; "no-token health/ai HTTP=200" } catch { "no-token health/ai HTTP=$($_.Exception.Response.StatusCode.value__)（期望 401）" }
$pg = Invoke-WebRequest "$B/api/health/ping" -UseBasicParsing
"no-token ping HTTP=$($pg.StatusCode)（期望 200）"

"===== ⑭ logout（路8）：注册→登出→me 401→重复登出幂等 ====="
$reg2 = Invoke-RestMethod "$B/api/auth/register" -Method Post -ContentType 'application/json;charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"p11v2","password":"p11Pass123456"}'))
$L = Invoke-RestMethod "$B/api/auth/logout" -Method Post -Headers (Hdr $reg2.data.token)
"logout code=$($L.code)"
try { Invoke-WebRequest "$B/api/auth/me" -Headers (Hdr $reg2.data.token) -UseBasicParsing | Out-Null; "me after logout HTTP=200" } catch { "me after logout HTTP=$($_.Exception.Response.StatusCode.value__)（期望 401）" }
$L2 = Invoke-RestMethod "$B/api/auth/logout" -Method Post -Headers (Hdr $reg2.data.token)
"logout again code=$($L2.code)（幂等）"

"===== ⑮ DELETE 会话（用即弃会话）====="
[IO.File]::WriteAllText("$PWD\test-data\p11-q3.json", '{"question":"p11-delete-test 一次性会话","conversationId":null}', (New-Object Text.UTF8Encoding $false))
$d1 = & curl.exe -s -N -X POST "$B/api/qa/chat/stream" -H 'Content-Type: application/json' -H "Authorization: Bearer $T" --data-binary '@test-data/p11-q3.json'
$dd = (($d1 | Where-Object { $_ -match '"messageId"' } | Select-Object -First 1) -replace '^data:\s*','' -replace '^[\d:.]+\|','') | ConvertFrom-Json
$dl = Invoke-RestMethod "$B/api/qa/conversations/$($dd.conversationId)" -Method Delete -Headers (Hdr $T)
"delete conv code=$($dl.code)（convId=$($dd.conversationId)）"
"===== 全回归脚本结束 $(Get-Date -Format 'HH:mm:ss') ====="
