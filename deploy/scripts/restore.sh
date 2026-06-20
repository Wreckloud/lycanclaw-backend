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
DATA_ARCHIVE="${BACKUP_DIR}/backend-data.tar.gz"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env 不存在: ${ENV_FILE}"
  exit 1
fi
if [[ ! -s "${SQL_FILE}" ]]; then
  echo "数据库备份不存在或为空: ${SQL_FILE}"
  exit 1
fi
if [[ ! -f "${DATA_ARCHIVE}" ]]; then
  echo "后端数据备份不存在: ${DATA_ARCHIVE}"
  exit 1
fi

gzip -t "${DATA_ARCHIVE}"
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

echo "恢复后端运行配置..."
docker run --rm -i -v lycanclaw_backend_data:/restore alpine:3.20 sh -c \
  'find /restore -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +; tar -xzf - -C /restore' \
  < "${DATA_ARCHIVE}"

echo "重新启动服务..."
docker compose "${COMPOSE_ARGS[@]}" up -d waline ncm-api backend
echo "恢复完成: ${BACKUP_DIR}"
