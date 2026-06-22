#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "用法: ./restore.sh <备份目录>"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
ENV_FILE="${DEPLOY_DIR}/.env"
BACKUP_DIR="$(cd "$1" && pwd)"
SQL_FILE="${BACKUP_DIR}/database.sql"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env 不存在: ${ENV_FILE}"
  exit 1
fi
if [[ ! -s "${SQL_FILE}" ]]; then
  echo "数据库备份不存在或为空: ${SQL_FILE}"
  exit 1
fi
COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

echo "停止写入服务并启动 MySQL..."
docker compose "${COMPOSE_ARGS[@]}" stop backend waline 2>/dev/null || true
docker compose "${COMPOSE_ARGS[@]}" up -d mysql

until docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'mysqladmin ping -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent'; do
  sleep 2
done

echo "恢复共享数据库..."
docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' < "${SQL_FILE}"

echo "重新启动服务..."
docker compose "${COMPOSE_ARGS[@]}" up -d waline ncm-api backend
echo "恢复完成: ${BACKUP_DIR}"
