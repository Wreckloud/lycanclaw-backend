[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$workspaceRoot = Split-Path -Parent $backendRoot
$statePath = Join-Path $workspaceRoot 'runtime\local-services.json'

function Stop-ProcessTree {
    param([int]$ProcessId)

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }
    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
}

if (-not (Test-Path -LiteralPath $statePath)) {
    Write-Host '未找到本地服务状态文件，没有可停止的脚本进程。'
    exit 0
}

$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($service in $state.services) {
    if (-not $service.startedByScript) {
        continue
    }

    $listener = Get-NetTCPConnection -State Listen -LocalPort $service.port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($listener) {
        Stop-ProcessTree -ProcessId $listener.OwningProcess
        Write-Host "已停止 $($service.name)（端口 $($service.port)）。"
    }
}

Remove-Item -LiteralPath $statePath -Force
