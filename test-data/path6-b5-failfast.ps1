# =====================================================================
# 路6 B5 配置 fail-fast 回归脚本（DEF-030）
# 用法：powershell -ExecutionPolicy Bypass -File test-data\path6-b5-failfast.ps1 [case]
#   case = strict-empty  : 严格模式 + JWT_SECRET 为空     -> 期望拒绝启动，清单含 [缺  失] cs.jwt.secret
#   case = strict-weak   : 严格模式 + JWT_SECRET 弱默认值  -> 期望拒绝启动，清单含 [弱默认] cs.jwt.secret
#   case = strict-short  : 严格模式 + JWT_SECRET <32 字节  -> 期望拒绝启动，清单含 [强度低] cs.jwt.secret
#   case = lenient-weak  : 宽松模式 + 弱默认值            -> 期望打 WARN 后继续启动（需中间件，Ctrl+C 停）
#   case = strict-ok     : 严格模式 + 强配置（临时环境变量注入）-> 期望正常启动（需中间件与 8080 空闲）
# 原理：OS 环境变量优先级高于 application.yml 与 spring.config.import 的 .env，
#       因此这里用进程环境变量覆盖，不动 .env 文件本体。
# =====================================================================
param([string]$case = "strict-empty")

$java = "C:\Users\17962\.jdks\ms-17.0.20.1\bin\java.exe"
$jar  = "cs-bootstrap\target\cs-server.jar"
$out  = "test-data\path6-b5-run.log"

# 清掉可能干扰的覆盖变量
Remove-Item Env:CS_STRICT_CONFIG -ErrorAction SilentlyContinue
Remove-Item Env:JWT_SECRET       -ErrorAction SilentlyContinue
Remove-Item Env:ADMIN_DEFAULT_PASSWORD -ErrorAction SilentlyContinue

switch ($case) {
  "strict-empty" { $env:CS_STRICT_CONFIG = "true";  $env:JWT_SECRET = "" }
  "strict-weak"  { $env:CS_STRICT_CONFIG = "true";  $env:JWT_SECRET = "dev-only-cs-server-jwt-secret-key-change-me-in-prod" }
  "strict-short" { $env:CS_STRICT_CONFIG = "true";  $env:JWT_SECRET = "short-secret-only-20-bytes!" }
  "lenient-weak" { $env:JWT_SECRET = "dev-only-cs-server-jwt-secret-key-change-me-in-prod" }
  "strict-ok"    {
    $env:CS_STRICT_CONFIG = "true"
    # 注意：ADMIN_DEFAULT_PASSWORD 仅本次进程内临时覆盖用于过闸，不改 .env、不触发重新播种（admin 已存在，幂等跳过）
    $env:ADMIN_DEFAULT_PASSWORD = "P6-strict-gate-test-2026!"
    # JWT_SECRET 不设置 -> 回落 .env 里的真实强密钥
  }
  default { Write-Host "未知 case: $case"; exit 1 }
}

Write-Host "[path6] case=$case  启动中（失败情形应在环境准备阶段即终止）..." -ForegroundColor Cyan

if ($case -in @("strict-empty", "strict-weak", "strict-short")) {
  # 期望快速失败：最多等 90s，若还在跑说明 fail-fast 未生效，杀掉并判失败
  $p = Start-Process -FilePath $java -ArgumentList "-jar", $jar -PassThru -NoNewWindow `
        -RedirectStandardOutput $out -RedirectStandardError "$out.err"
  if (-not $p.WaitForExit(90000)) {
    $p.Kill()
    Write-Host "[path6] FAIL：90s 内未退出，fail-fast 未生效（进程已被杀）" -ForegroundColor Red
    exit 1
  }
  Write-Host "[path6] 进程已退出，ExitCode=$($p.ExitCode)（非 0 = 拒绝启动）" -ForegroundColor Cyan
} else {
  # 期望持续运行（宽松 WARN 后启动 / 严格过闸后启动）：前台跑，用户 Ctrl+C 停
  & $java -jar $jar 2>&1 | Tee-Object -FilePath $out
  return
}

Write-Host "`n[path6] ===== 启动日志尾部（含安全闸清单，不回显密钥值）=====" -ForegroundColor Yellow
Get-Content $out -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -Last 25
Get-Content "$out.err" -Encoding UTF8 -ErrorAction SilentlyContinue | Select-Object -Last 25
Remove-Item "$out.err" -ErrorAction SilentlyContinue
