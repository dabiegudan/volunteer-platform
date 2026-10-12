# ==============================================================================
# 环境变量加载脚本（Windows PowerShell）
# ------------------------------------------------------------------------------
# 作用：把项目根目录 .env 中的变量导入【当前 PowerShell 会话】的环境变量，
#       使本机直接运行 Spring Boot 时也能读到同一套配置。
#
# 为什么需要这个脚本：
#   Docker Compose 会自动读取 .env 并注入容器；而 Spring Boot【不读取 .env】，
#   只读操作系统环境变量。本机开发时二者之间需要这座"桥"。
#
# 用法（注意开头的点和空格，表示在当前会话执行，否则变量不会保留）：
#   . .\scripts\load-env.ps1
#
# 之后即可正常启动后端：
#   .\mvnw.cmd -pl volunteer-backend/volunteer-server -am spring-boot:run
#
# 可选参数：
#   -EnvFile <路径>   指定其他 env 文件，默认 <项目根>/.env
#   -Local            本机模式：自动把容器内主机名改写为 localhost
#                     （mysql -> localhost，redis -> localhost，minio -> localhost:9090）
#   -Quiet            只加载，不打印明细
# ==============================================================================

[CmdletBinding()]
param(
    [string]$EnvFile,
    [switch]$Local,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

# ---- 定位项目根目录（本脚本位于 <root>/scripts/ 下）----
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) {
    $EnvFile = Join-Path $projectRoot '.env'
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
    Write-Host "[X] 未找到环境变量文件：$EnvFile" -ForegroundColor Red
    Write-Host "    请先创建：Copy-Item .env.example .env" -ForegroundColor Yellow
    return
}

$loaded = 0
$skipped = 0
$localOverrides = @()

# ---- 逐行解析 ----
Get-Content -LiteralPath $EnvFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()

    # 跳过空行与注释
    if ($line -eq '' -or $line.StartsWith('#')) { return }

    $idx = $line.IndexOf('=')
    if ($idx -lt 1) { return }

    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim()

    # 去掉成对引号（.env 中值可能被引号包裹）
    if ($value.Length -ge 2) {
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }

    # 本机模式：把容器内主机名改写为 localhost
    if ($Local) {
        switch ($key) {
            'SPRING_DATASOURCE_URL' {
                $original = $value
                $value = $value -replace '//mysql:', '//localhost:'
                $value = $value -replace '//host\.docker\.internal:', '//localhost:'
                if ($value -ne $original) { $localOverrides += "SPRING_DATASOURCE_URL -> localhost" }
            }
            'REDIS_HOST' {
                if ($value -eq 'redis') { $value = 'localhost'; $localOverrides += 'REDIS_HOST -> localhost' }
            }
            'MINIO_ENDPOINT' {
                $original = $value
                $value = $value -replace '//minio:9000', '//localhost:9090'
                if ($value -ne $original) { $localOverrides += 'MINIO_ENDPOINT -> http://localhost:9090' }
            }
        }
    }

    Set-Item -Path "Env:$key" -Value $value
    $loaded++

    if (-not $Quiet) {
        # 敏感项只显示"已设置"，不打印值
        if ($key -match 'PASSWORD|SECRET|API_KEY|ACCESS_KEY') {
            Write-Host ("  {0,-48} = <已设置，值不外显>" -f $key) -ForegroundColor DarkGray
        } else {
            Write-Host ("  {0,-48} = {1}" -f $key, $value) -ForegroundColor DarkGray
        }
    }
}

Write-Host ""
Write-Host "[OK] 已从 $EnvFile 加载 $loaded 个环境变量到当前会话" -ForegroundColor Green

if ($localOverrides.Count -gt 0) {
    Write-Host "[本机模式] 已应用以下地址改写：" -ForegroundColor Cyan
    $localOverrides | ForEach-Object { Write-Host "  - $_" -ForegroundColor Cyan }
}

if (-not $Quiet) {
    Write-Host ""
    Write-Host "提示：变量仅在【当前 PowerShell 会话】有效，关闭窗口即失效。" -ForegroundColor Yellow
    Write-Host "     新开窗口需重新执行：. .\scripts\load-env.ps1" -ForegroundColor Yellow
}
