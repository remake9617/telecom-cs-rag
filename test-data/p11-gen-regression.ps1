# p11: generate localhost regression copies (originals untouched) with BOM preserved
$root = 'c:\Users\17962\Desktop\Big_Java_Project\AIBishe'
$utf8bom = New-Object Text.UTF8Encoding $true
$map = @(
    @{ src = 'p9-regression.ps1';      dst = 'p11-regression.ps1' },
    @{ src = 'verify-integration.ps1'; dst = 'p11-verify.ps1' }
)
foreach ($m in $map) {
    $src = Join-Path $root ('test-data\' + $m.src)
    $text = [IO.File]::ReadAllText($src)
    $text = $text.Replace('192.168.25.129:8088', 'localhost:8080')
    $text = $text.Replace('p9', 'p11')
    $text = $text.Replace('p6-', 'p11-')
    $dst = Join-Path $root ('test-data\' + $m.dst)
    [IO.File]::WriteAllText($dst, $text, $utf8bom)
    Write-Output ("generated: {0}" -f $dst)
}
