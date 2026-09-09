$targets = @(
    @{ name = 'ES_9200';    host = '192.168.25.129'; port = 9200 },
    @{ name = 'MYSQL_13306'; host = '192.168.25.129'; port = 13306 },
    @{ name = 'REDIS_16379'; host = '192.168.25.129'; port = 16379 },
    @{ name = 'BACKEND_8080'; host = 'localhost'; port = 8080 }
)
foreach ($t in $targets) {
    $c = New-Object Net.Sockets.TcpClient
    try {
        $ok = $c.ConnectAsync($t.host, $t.port).Wait(3000)
        if ($ok) { Write-Output ("{0} -> OPEN" -f $t.name) } else { Write-Output ("{0} -> CLOSED/TIMEOUT" -f $t.name) }
    } catch {
        Write-Output ("{0} -> ERROR {1}" -f $t.name, $_.Exception.Message)
    } finally { $c.Close() }
}
