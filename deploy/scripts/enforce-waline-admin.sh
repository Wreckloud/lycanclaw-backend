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

OWNER_HEX="$(printf '%s' "${OWNER_EMAIL}" | od -An -tx1 | tr -d ' \n')"
COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

cat <<SQL | docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"'
SET @owner_email = CONVERT(0x${OWNER_HEX} USING utf8mb4);
UPDATE wl_Users
SET type = CASE WHEN email = @owner_email THEN 'administrator' ELSE 'user' END;
SELECT id, email, type FROM wl_Users ORDER BY id;
SQL

echo "已保留唯一 Waline 管理员: ${OWNER_EMAIL}"
