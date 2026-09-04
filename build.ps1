<#
  本项目构建/启动脚本 —— 临时 JDK17 + 自动加载 .env 环境变量，均不改全局配置。
  用法：
    .\build.ps1                                   # mvn clean compile
    .\build.ps1 clean install -DskipTests         # 打包安装到本地仓库
    .\build.ps1 spring-boot:run -pl cs-bootstrap  # 启动后端（自动加载 .env 的 Key / ES_URIS）
  若提示脚本被禁用：Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArgs)
if (-not $MavenArgs -or $MavenArgs.Count -eq 0) { $MavenArgs = @("clean", "compile") }

# 1) 临时 JDK17（仅当前进程，不动全局 JAVA_HOME，其他项目仍用 JDK8）
$env:JAVA_HOME = "C:\Users\17962\.jdks\ms-17.0.20.1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 2) 从项目根 .env 加载环境变量（供 Spring Boot ${ES_URIS}/${AI_DASHSCOPE_API_KEY} 等读取）
#    比 spring.config.import 更可靠：不受启动工作目录影响，环境变量优先级最高
$envFile = Join-Path $PSScriptRoot ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
        $kv = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim(), 'Process')
    }
    Write-Host "[build] 已加载 .env：ES_URIS=$env:ES_URIS  MYSQL_HOST=$env:MYSQL_HOST" -ForegroundColor Cyan
} else {
    Write-Host "[build] 警告：未找到 $envFile（Key/地址将为空）" -ForegroundColor Yellow
}

Write-Host "[build] JAVA_HOME=$env:JAVA_HOME（全局不变） | mvn $($MavenArgs -join ' ')" -ForegroundColor Cyan
& mvn @MavenArgs
