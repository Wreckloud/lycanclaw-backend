#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
ENV_FILE="${DEPLOY_DIR}/.env"
BACKUP_ROOT="${DEPLOY_DIR}/backups"
TIMESTAMP="$(date +"%Y%m%d-%H%M%S")"
BACKUP_DIR="${BACKUP_ROOT}/${TIMESTAMP}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env 不存在: ${ENV_FILE}"
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

echo "开始导出 Waline MySQL 数据..."
docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick "$MYSQL_DATABASE"' \
  > "${BACKUP_DIR}/waline.sql"

echo "导出后端运行数据..."
docker compose "${COMPOSE_ARGS[@]}" exec -T backend sh -c \
  'if [ -d /app/data ]; then tar -czf - -C /app data; fi' \
  > "${BACKUP_DIR}/backend-data.tar.gz" || true

cp "${ENV_FILE}" "${BACKUP_DIR}/compose.env.snapshot"

cat > "${BACKUP_DIR}/manifest.txt" <<EOF
created_at=${TIMESTAMP}
compose_file=${COMPOSE_FILE}
files=waline.sql,backend-data.tar.gz,compose.env.snapshot
EOF

echo "备份完成: ${BACKUP_DIR}"
