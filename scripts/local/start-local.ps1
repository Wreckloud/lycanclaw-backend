[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$workspaceRoot = Split-Path -Parent $backendRoot
$runtimeRoot = Join-Path $workspaceRoot 'runtime'
$logRoot = Join-Path $runtimeRoot 'logs'
$statePath = Join-Path $runtimeRoot 'local-services.json'

New-Item -ItemType Directory -Path $logRoot -Force | Out-Null

function Test-Port {
    param([int]$Port)

    return [bool](Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Wait-Port {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port -Port $Port) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '^\s*([^=]+)=(.*)$') {
            continue
        }
        $key = $matches[1].Trim()
        $value = $matches[2].Trim().Trim('"').Trim("'")
        $values[$key] = $value
    }
    return $values
}

function Resolve-CommandPath {
    param([string]$Name)

    $command = Get-Command $Name -ErrorAction Stop
    return $command.Source
}

function Start-LocalService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [int]$TimeoutSeconds = 60
    )

    if (Test-Port -Port $Port) {
        Write-Host "$Name 已在端口 $Port 运行，跳过启动。"
        return [pscustomobject]@{
            name = $Name
            port = $Port
            startedByScript = $false
            processId = $null
        }
    }

    $stdout = Join-Path $logRoot "$Name.out.log"
    $stderr = Join-Path $logRoot "$Name.err.log"
    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    if (-not (Wait-Port -Port $Port -TimeoutSeconds $TimeoutSeconds)) {
        Write-Warning "$Name 未在预期时间内监听端口 $Port，请查看 $stderr"
    } else {
        Write-Host "$Name 已启动：http://127.0.0.1:$Port"
    }

    return [pscustomobject]@{
        name = $Name
        port = $Port
        startedByScript = $true
        processId = $process.Id
    }
}

if (-not (Test-Port -Port 3306)) {
    throw '未检测到本地 MySQL（3306）。请先启动 MySQL80 服务。'
}

$pnpm = Resolve-CommandPath -Name 'pnpm.cmd'
$maven = Resolve-CommandPath -Name 'mvn.cmd'
$walineDirectory = Join-Path $backendRoot 'dev-services\waline'
$walineEnv = Read-DotEnv -Path (Join-Path $walineDirectory '.env.local')
$backendEnv = Read-DotEnv -Path (Join-Path $backendRoot '.env.local')

$previousEnvironment = @{}
$backendEnvironment = @{
    LYCAN_CONTENT_KNOWLEDGE_STATS_JSON_PATH = (Join-Path $workspaceRoot 'frontend\docs\public\knowledge-stats.json').Replace('\', '/')
    LYCAN_CONTENT_POSTS_JSON_PATH = (Join-Path $workspaceRoot 'frontend\docs\public\posts.json').Replace('\', '/')
}

foreach ($entry in $backendEnv.GetEnumerator()) {
    if (-not [string]::IsNullOrWhiteSpace($entry.Value)) {
        $backendEnvironment[$entry.Key] = $entry.Value
    }
}

if (-not [string]::IsNullOrWhiteSpace($walineEnv['MYSQL_PASSWORD']) -and
    [string]::IsNullOrWhiteSpace($env:LYCAN_DB_PASSWORD)) {
    $backendEnvironment['LYCAN_DB_PASSWORD'] = $walineEnv['MYSQL_PASSWORD']
}

$walineToBackendKeys = @(
    'SMTP_HOST',
    'SMTP_SERVICE',
    'SMTP_USER',
    'SMTP_PASS',
    'AUTHOR_EMAIL',
    'COMMENT_AUDIT',
    'MAIL_SUBJECT',
    'MAIL_TEMPLATE',
    'MAIL_SUBJECT_ADMIN',
    'MAIL_TEMPLATE_ADMIN'
)
foreach ($key in $walineToBackendKeys) {
    $value = $walineEnv[$key]
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        $backendEnvironment[('WALINE_' + $key)] = $value
    }
}

foreach ($entry in $backendEnvironment.GetEnumerator()) {
    $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}

try {
    $services = @()
    $services += Start-LocalService `
        -Name 'music-upstream' `
        -Port 3000 `
        -FilePath $pnpm `
        -ArgumentList @('run', 'start') `
        -WorkingDirectory (Join-Path $backendRoot 'dev-services\music-upstream')

    $services += Start-LocalService `
        -Name 'waline' `
        -Port 8360 `
        -FilePath $pnpm `
        -ArgumentList @('run', 'start') `
        -WorkingDirectory $walineDirectory

    $services += Start-LocalService `
        -Name 'backend' `
        -Port 8080 `
        -FilePath $maven `
        -ArgumentList @('spring-boot:run') `
        -WorkingDirectory $backendRoot `
        -TimeoutSeconds 120

    $services += Start-LocalService `
        -Name 'frontend' `
        -Port 5173 `
        -FilePath $pnpm `
        -ArgumentList @('run', 'dev', '--host', '127.0.0.1') `
        -WorkingDirectory (Join-Path $workspaceRoot 'frontend') `
        -TimeoutSeconds 120

    [pscustomobject]@{
        startedAt = (Get-Date).ToString('o')
        services = $services
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath -Encoding UTF8
} finally {
    foreach ($entry in $previousEnvironment.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

Write-Host ''
Write-Host '本地入口：'
Write-Host '  博客    http://127.0.0.1:5173'
Write-Host '  管理端  http://127.0.0.1:8080/admin/auth.html'
Write-Host '  后端    http://127.0.0.1:8080/actuator/health'
Write-Host '  Waline  http://127.0.0.1:8360'
