# path8: Redis 直连实测（RESP over TCP，替代 docker exec redis-cli）
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$addr = "192.168.25.129"
$port = 16379

function Invoke-Redis {
    param([string[]]$CmdArgs)
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect($addr, $port)
    $stream = $client.GetStream()
    # RESP 数组协议：*N $len arg ...
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.Append("*$($CmdArgs.Count)`r`n")
    foreach ($a in $CmdArgs) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($a)
        [void]$sb.Append("`$$($bytes.Length)`r`n$a`r`n")
    }
    $payload = [Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $stream.Write($payload, 0, $payload.Length)
    $stream.Flush()
    Start-Sleep -Milliseconds 200
    $buf = New-Object byte[] 65536
    $out = New-Object System.Text.StringBuilder
    while ($stream.DataAvailable) {
        $n = $stream.Read($buf, 0, $buf.Length)
        [void]$out.Append([Text.Encoding]::UTF8.GetString($buf, 0, $n))
        Start-Sleep -Milliseconds 50
    }
    $client.Close()
    return $out.ToString().Trim()
}

"== 1. KEYS jwt:* =="
Invoke-Redis @("KEYS", "jwt:*")

"== 2. 逐 key TTL/GET（黑名单 TTL 期望为小于 86400 的正数）=="
$keys = (Invoke-Redis @("KEYS", "jwt:blacklist:*")) -split "`r`n" | Where-Object { $_ -notmatch '^[\*\$]' }
foreach ($k in $keys) {
    $k = $k.Trim()
    if ($k) {
        "key=$k"
        "  TTL = $(Invoke-Redis @('TTL', $k))"
        "  VAL = $(Invoke-Redis @('GET', $k))"
    }
}

"== 3. 禁用即时失效模拟：SET jwt:user-invalid:1 <now> =="
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
"SET result: $(Invoke-Redis @('SET', 'jwt:user-invalid:1', "$nowMs", 'EX', '86400'))"
"GET: $(Invoke-Redis @('GET', 'jwt:user-invalid:1')) (now=$nowMs)"
