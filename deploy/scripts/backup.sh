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

echo "导出共享 MySQL 数据库..."
docker compose "${COMPOSE_ARGS[@]}" exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers "$MYSQL_DATABASE"' \
  > "${BACKUP_DIR}/database.sql"

echo "导出后端运行配置..."
docker compose "${COMPOSE_ARGS[@]}" exec -T backend \
  tar -czf - -C /app/data . > "${BACKUP_DIR}/backend-data.tar.gz"

test -s "${BACKUP_DIR}/database.sql"
gzip -t "${BACKUP_DIR}/backend-data.tar.gz"

cat > "${BACKUP_DIR}/manifest.txt" <<EOF
created_at=${TIMESTAMP}
files=database.sql,backend-data.tar.gz
EOF

find "${BACKUP_ROOT}" -mindepth 1 -maxdepth 1 -type d -mtime +14 -exec rm -rf -- {} +
echo "备份完成: ${BACKUP_DIR}"
