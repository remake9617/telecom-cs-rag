<#
  本项目专用构建脚本 —— 临时使用 JDK 17，不修改全局 JAVA_HOME（你的其他项目仍用 JDK 8）。
  原理：仅在当前 PowerShell 进程内设置 JAVA_HOME/Path，脚本结束即失效，不写系统环境变量。

  用法：
    .\build.ps1                                 # 默认执行 mvn clean compile
    .\build.ps1 clean package                   # 自定义 Maven 参数
    .\build.ps1 spring-boot:run -pl cs-bootstrap # 启动应用（需 VM 中间件 + API Key 就绪）

  若提示"脚本被禁用"，先执行（仅对当前会话放开，安全）：
    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#>
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArgs)

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) { $MavenArgs = @("clean", "compile") }

# 项目 JDK 17（IDEA 下载于 .jdks）；如换机器或升级 JDK，仅需改这一行路径
$env:JAVA_HOME = "C:\Users\17962\.jdks\ms-17.0.20.1"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

Write-Host "[build] 临时 JAVA_HOME = $env:JAVA_HOME（全局 JAVA_HOME 不变）" -ForegroundColor Cyan
& java -version
Write-Host "[build] 执行：mvn $($MavenArgs -join ' ')" -ForegroundColor Cyan
& mvn @MavenArgs
