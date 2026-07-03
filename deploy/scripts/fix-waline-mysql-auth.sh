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

read_env_value() {
  local key="$1"
  grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 | cut -d= -f2- | tr -d '\r'
}

WALINE_DB_USER="$(read_env_value WALINE_DB_USER)"
WALINE_DB_PASSWORD="$(read_env_value WALINE_DB_PASSWORD)"

if [[ -z "${WALINE_DB_USER:-}" || -z "${WALINE_DB_PASSWORD:-}" ]]; then
  echo "请先在 .env 设置 WALINE_DB_USER 与 WALINE_DB_PASSWORD"
  exit 1
fi

USER_HEX="$(printf '%s' "${WALINE_DB_USER}" | od -An -tx1 | tr -d ' \n')"
PASSWORD_HEX="$(printf '%s' "${WALINE_DB_PASSWORD}" | od -An -tx1 | tr -d ' \n')"
COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

for _ in {1..30}; do
  if docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
    'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent'; then
    break
  fi
  sleep 2
done

cat <<SQL | docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysql -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD"'
SET @waline_user = CONVERT(0x${USER_HEX} USING utf8mb4);
SET @waline_password = CONVERT(0x${PASSWORD_HEX} USING utf8mb4);
SET @statement = CONCAT(
  'ALTER USER ',
  QUOTE(@waline_user),
  '@''%'' IDENTIFIED WITH mysql_native_password BY ',
  QUOTE(@waline_password)
);
PREPARE stmt FROM @statement;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
FLUSH PRIVILEGES;
SELECT user, host, plugin FROM mysql.user WHERE user = @waline_user;
SQL

echo "已切换 Waline MySQL 用户认证协议: ${WALINE_DB_USER}"
