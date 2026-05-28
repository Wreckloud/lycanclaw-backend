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

COMPOSE_ARGS=(-f "${COMPOSE_FILE}" --env-file "${ENV_FILE}")

if [[ "${1:-}" == "--build" ]]; then
  docker compose "${COMPOSE_ARGS[@]}" up -d --build
else
  docker compose "${COMPOSE_ARGS[@]}" up -d
fi

docker compose "${COMPOSE_ARGS[@]}" ps
