# p11: grep backend log (ASCII patterns only); param 1 = log file
param([string]$log = 'p11-backend.log')
$patterns = @('QaService', 'ConversationMemoryService', ' ERROR ', 'QueryRewrite', 'IntentService', 'facade')
$hits = Select-String -Path $log -Pattern ($patterns -join '|')
Write-Output ("{0}: total hits {1}" -f $log, $hits.Count)
$hits | Select-Object -Last 30 | ForEach-Object {
    $line = $_.Line
    if ($line.Length -gt 400) { $line = $line.Substring(0, 400) + ' ...<TRUNC>' }
    Write-Output $line
}
