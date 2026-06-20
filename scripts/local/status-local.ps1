[CmdletBinding()]
param()

$ports = @(
    @{ Name = 'MySQL'; Port = 3306 },
    @{ Name = '网易云上游'; Port = 3000 },
    @{ Name = 'Waline'; Port = 8360 },
    @{ Name = '后端'; Port = 8080 },
    @{ Name = '前端'; Port = 5173 }
)

$status = foreach ($item in $ports) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $item.Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    [pscustomobject]@{
        Service = $item.Name
        Port = $item.Port
        Status = if ($listener) { '运行中' } else { '未运行' }
        ProcessId = if ($listener) { $listener.OwningProcess } else { $null }
    }
}

$status | Format-Table -AutoSize
