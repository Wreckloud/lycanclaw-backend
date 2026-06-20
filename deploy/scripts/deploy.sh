#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.yml"
ENV_FILE="${DEPLOY_DIR}/.env"
ENV_EXAMPLE="${DEPLOY_DIR}/.env.example"

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${ENV_EXAMPLE}" "${ENV_FILE}"
  echo "已创建 ${ENV_FILE}，请先编辑后重试。"
  exit 1
fi

CONTENT_DATA_HOST_PATH="$(grep -E '^CONTENT_DATA_HOST_PATH=' "${ENV_FILE}" | tail -n 1 | cut -d= -f2-)"
if [[ -z "${CONTENT_DATA_HOST_PATH}" ]]; then
  echo "请在 ${ENV_FILE} 设置 CONTENT_DATA_HOST_PATH。"
  exit 1
fi
for filename in posts.json knowledge-stats.json; do
  if [[ ! -f "${CONTENT_DATA_HOST_PATH}/${filename}" ]]; then
    echo "警告：前端尚未发布，共享数据暂缺: ${CONTENT_DATA_HOST_PATH}/${filename}"
  fi
done

COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

if [[ "${1:-}" == "--build" ]]; then
  docker compose "${COMPOSE_ARGS[@]}" up -d --build
else
  docker compose "${COMPOSE_ARGS[@]}" up -d
fi

docker compose "${COMPOSE_ARGS[@]}" ps

for _ in {1..30}; do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "后端健康检查通过。"
    exit 0
  fi
  sleep 2
done

echo "后端健康检查失败，请查看 docker compose logs backend。"
exit 1
