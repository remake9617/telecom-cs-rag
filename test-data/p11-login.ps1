# p11 login helper (ASCII only)
$login = Invoke-RestMethod 'http://localhost:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
Write-Output ("login code={0} role={1}" -f $login.code, $login.data.user.role)
[IO.File]::WriteAllText("$PSScriptRoot\p11-token.txt", $login.data.token)
Write-Output "token saved"
