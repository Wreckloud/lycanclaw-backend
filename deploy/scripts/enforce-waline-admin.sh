#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
ENV_FILE="${DEPLOY_DIR}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env 不存在: ${ENV_FILE}"
  exit 1
fi

OWNER_EMAIL="$(grep -E '^WALINE_AUTHOR_EMAIL=' "${ENV_FILE}" | tail -n 1 | cut -d= -f2-)"
if [[ -z "${OWNER_EMAIL}" ]]; then
  echo "请先在 .env 设置 WALINE_AUTHOR_EMAIL"
  exit 1
fi
ADMIN_QQ_WHITELIST="$(grep -E '^BACKEND_ADMIN_QQ_WHITELIST=' "${ENV_FILE}" | tail -n 1 | cut -d= -f2- | tr -d '\r')"
if [[ -z "${ADMIN_QQ_WHITELIST}" ]]; then
  echo "请先在 .env 设置 BACKEND_ADMIN_QQ_WHITELIST"
  exit 1
fi

OWNER_HEX="$(printf '%s' "${OWNER_EMAIL}" | od -An -tx1 | tr -d ' \n')"
WHITELIST_HEX="$(printf '%s' "${ADMIN_QQ_WHITELIST}" | od -An -tx1 | tr -d ' \n')"
COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

cat <<SQL | docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
SET @owner_email = CONVERT(0x${OWNER_HEX} USING utf8mb4);
SET @admin_qq_whitelist = CONVERT(0x${WHITELIST_HEX} USING utf8mb4);
UPDATE wl_Users
SET type = CASE
  WHEN email = @owner_email THEN 'administrator'
  WHEN qq IS NOT NULL AND FIND_IN_SET(qq, REPLACE(@admin_qq_whitelist, ' ', '')) > 0 THEN 'administrator'
  ELSE 'user'
END;
SELECT id, email, display_name, type, qq FROM wl_Users ORDER BY id;
SQL

echo "已按邮箱与 QQ 白名单刷新 Waline 管理员: ${OWNER_EMAIL} / ${ADMIN_QQ_WHITELIST}"
