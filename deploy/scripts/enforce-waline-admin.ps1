$ErrorActionPreference = 'Stop'

$deployDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$envFile = Join-Path $deployDir '.env'
$composeFile = Join-Path $deployDir 'docker-compose.yml'

if (-not (Test-Path -LiteralPath $envFile)) {
  throw ".env 不存在: $envFile"
}

$envMap = @{}
Get-Content -LiteralPath $envFile | ForEach-Object {
  $line = $_.Trim()
  if (-not $line -or $line.StartsWith('#')) { return }
  $index = $line.IndexOf('=')
  if ($index -lt 1) { return }
  $envMap[$line.Substring(0, $index).Trim()] = $line.Substring($index + 1).Trim()
}

$owner = $envMap['WALINE_AUTHOR_EMAIL']
if (-not $owner) {
  throw '请先在 .env 设置 WALINE_AUTHOR_EMAIL'
}
$adminQqWhitelist = $envMap['BACKEND_ADMIN_QQ_WHITELIST']
if (-not $adminQqWhitelist) {
  throw '请先在 .env 设置 BACKEND_ADMIN_QQ_WHITELIST'
}

$ownerHex = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($owner))
$whitelistHex = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($adminQqWhitelist))
$sql = @"
SET @owner_email = CONVERT(0x$ownerHex USING utf8mb4);
SET @admin_qq_whitelist = CONVERT(0x$whitelistHex USING utf8mb4);
UPDATE wl_Users
SET type = CASE
  WHEN email = @owner_email THEN 'administrator'
  WHEN qq IS NOT NULL AND FIND_IN_SET(qq, REPLACE(@admin_qq_whitelist, ' ', '')) > 0 THEN 'administrator'
  ELSE 'user'
END;
SELECT id, email, display_name, type, qq FROM wl_Users ORDER BY id;
"@

$composeArgs = @('-f', $composeFile, '--env-file', $envFile)
$sql | docker compose @composeArgs exec -T mysql sh -c 'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'

if ($LASTEXITCODE -ne 0) {
  throw 'Waline 管理员设置失败'
}

Write-Host "已按邮箱与 QQ 白名单刷新 Waline 管理员: $owner / $adminQqWhitelist"
