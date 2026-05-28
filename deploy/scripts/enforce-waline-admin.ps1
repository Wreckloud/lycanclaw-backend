$ErrorActionPreference = 'Stop'

$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $deployDir '..\.env'
$composeFile = Join-Path $deployDir '..\docker-compose.yml'

if (-not (Test-Path $envFile)) {
  Write-Host ".env 不存在: $envFile"
  exit 1
}

# 读取 .env 为键值对
$envMap = @{}
Get-Content $envFile | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $idx = $line.IndexOf('=')
  if ($idx -lt 1) { return }
  $k = $line.Substring(0, $idx).Trim()
  $v = $line.Substring($idx + 1).Trim()
  $envMap[$k] = $v
}

$owner = $envMap['WALINE_OWNER_EMAIL']
if (-not $owner) {
  Write-Host "请先在 .env 设置 WALINE_OWNER_EMAIL"
  exit 1
}

$db = $envMap['WALINE_DB_NAME']
$user = $envMap['WALINE_DB_USER']
$pass = $envMap['WALINE_DB_PASSWORD']

if (-not $db -or -not $user -or -not $pass) {
  Write-Host "请先在 .env 设置 WALINE_DB_NAME / WALINE_DB_USER / WALINE_DB_PASSWORD"
  exit 1
}

$sql = @"
UPDATE wl_Users
SET type = CASE WHEN email = '$owner' THEN 'administrator' ELSE 'user' END;
SELECT id, email, type FROM wl_Users ORDER BY id;
"@

$composeArgs = @('-f', $composeFile, '--env-file', $envFile)

# 在 mysql 容器里执行 SQL
$cmd = "mysql -u$user -p$pass $db -e \"$($sql.Replace('"','\"'))\""
docker compose @composeArgs exec -T mysql sh -lc $cmd

Write-Host "已强制设置管理员白名单: $owner"
