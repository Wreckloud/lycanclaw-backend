#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "用法: ./restore.sh <备份目录>"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
ENV_FILE="${DEPLOY_DIR}/.env"
BACKUP_DIR="$1"
SQL_FILE="${BACKUP_DIR}/waline.sql"
DATA_ARCHIVE="${BACKUP_DIR}/backend-data.tar.gz"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env 不存在: ${ENV_FILE}"
  exit 1
fi

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "未找到 SQL 备份: ${SQL_FILE}"
  exit 1
fi

COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

echo "确保依赖容器已启动..."
docker compose "${COMPOSE_ARGS[@]}" up -d mysql backend waline

echo "恢复 Waline MySQL 数据..."
cat "${SQL_FILE}" | docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

if [[ -s "${DATA_ARCHIVE}" ]]; then
  echo "恢复后端运行数据..."
  cat "${DATA_ARCHIVE}" | docker compose "${COMPOSE_ARGS[@]}" exec -T backend sh -c \
    'tar -xzf - -C /'
fi

echo "重启 backend 与 waline..."
docker compose "${COMPOSE_ARGS[@]}" restart backend waline

echo "恢复完成: ${BACKUP_DIR}"
