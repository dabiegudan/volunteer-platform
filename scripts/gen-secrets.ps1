# ==============================================================================
# 强随机密钥生成脚本
# ------------------------------------------------------------------------------
# 作用：为 .env 中的敏感项生成符合安全要求的随机值，并可直接写入 .env。
#
# 用法：
#   .\scripts\gen-secrets.ps1                 # 仅打印生成的密钥
#   .\scripts\gen-secrets.ps1 -Apply          # 生成并写回 .env（覆盖对应项）
#   .\scripts\gen-secrets.ps1 -Apply -WhatIfSecrets   # 预览将写入的内容
#
# 生成项：
#   JWT_SECRET              64 字符（要求 ≥ 32）
#   ENCRYPT_SECRET_KEY      32 字符
#
# 注意：
#   - 数据库默认账号密码为 root/root，MinIO 默认凭据为 minioadmin/minioadmin，
#     二者均不在此脚本中生成；生产环境如需更换请手工编辑 .env（并同步修改
#     MinIO 容器凭据与数据库账号）
#   - 轮换 JWT_SECRET 会使存量 Token 失效
#   - 轮换 ENCRYPT_SECRET_KEY 会导致历史加密数据无法解密，请务必先完成数据迁移
# ==============================================================================

[CmdletBinding()]
param(
    [switch]$Apply,
    [switch]$WhatIfSecrets
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'

# ---- 使用加密安全随机数生成器（RNGCryptoServiceProvider 不可预测）----
function New-SecureSecret {
    param(
        [int]$Length = 32,
        [string]$Alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    )
    $bytes = New-Object byte[] $Length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    -join ($bytes | ForEach-Object { $Alphabet[$_ % $Alphabet.Length] })
}

# ---- 生成 ----
$secrets = [ordered]@{
    'JWT_SECRET'         = New-SecureSecret -Length 64
    'ENCRYPT_SECRET_KEY' = New-SecureSecret -Length 32
}

Write-Host ""
Write-Host "===== 生成的密钥（请妥善保管，勿提交到代码仓库）=====" -ForegroundColor Cyan
foreach ($k in $secrets.Keys) {
    Write-Host ("  {0,-30} = {1}" -f $k, $secrets[$k]) -ForegroundColor DarkGray
}
Write-Host ""

if (-not $Apply) {
    Write-Host "提示：加 -Apply 参数可将上述值写回 .env" -ForegroundColor Yellow
    Write-Host "      .\scripts\gen-secrets.ps1 -Apply" -ForegroundColor Yellow
    return
}

if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Host "[X] 未找到 $envFile" -ForegroundColor Red
    Write-Host "    请先执行：Copy-Item .env.example .env" -ForegroundColor Yellow
    return
}

$lines = Get-Content -LiteralPath $envFile -Encoding UTF8
$updated = 0
$result = foreach ($line in $lines) {
    $matched = $false
    foreach ($k in $secrets.Keys) {
        if ($line -match "^\s*$([regex]::Escape($k))\s*=") {
            $matched = $true
            if ($WhatIfSecrets) {
                Write-Host "  将更新 $k" -ForegroundColor Yellow
            }
            "$k=$($secrets[$k])"
            $updated++
            break
        }
    }
    if (-not $matched) { $line }
}

if ($WhatIfSecrets) {
    Write-Host ""
    Write-Host "[预览模式] 未写入文件。" -ForegroundColor Yellow
    return
}

# 备份原文件，避免误覆盖无法回退
$backup = "$envFile.bak"
Copy-Item -LiteralPath $envFile -Destination $backup -Force

Set-Content -LiteralPath $envFile -Value $result -Encoding UTF8 -NoNewline:$false

Write-Host "[OK] 已更新 $updated 项到 $envFile" -ForegroundColor Green
Write-Host "     原文件已备份为 $backup" -ForegroundColor DarkGray
Write-Host ""
Write-Host "后续步骤：" -ForegroundColor Cyan
Write-Host "  1) 重新加载环境变量：. .\scripts\load-env.ps1" -ForegroundColor Cyan
Write-Host "  2) 容器场景需重建容器（MySQL/MinIO 的 root 口令仅首次建卷生效）：" -ForegroundColor Cyan
Write-Host "     docker compose -f docker-compose.env.yml down -v" -ForegroundColor Cyan
Write-Host "     docker compose -f docker-compose.env.yml up -d --build" -ForegroundColor Cyan
Write-Host "     注意：down -v 会删除数据卷，已有数据将丢失" -ForegroundColor Red
