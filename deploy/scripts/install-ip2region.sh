#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${IP2REGION_TARGET_DIR:-${DEPLOY_DIR}/data/ip2region}"
ENV_FILE="${DEPLOY_DIR}/.env"
VERSION="${IP2REGION_VERSION:-v3.9.0}"
GITHUB_RAW_BASE="https://raw.githubusercontent.com/lionsoul2014/ip2region/${VERSION}/data"
FORCE_ENV="${1:-}"

V4_FILENAME="ip2region_v4.xdb"
V6_FILENAME="ip2region_v6.xdb"
V4_CONTAINER_PATH="/data/ip2region/${V4_FILENAME}"
V6_CONTAINER_PATH="/data/ip2region/${V6_FILENAME}"

download_xdb() {
  local filename="$1"
  local target="${TARGET_DIR}/${filename}"
  local tmp_target="${target}.tmp"
  local url="${GITHUB_RAW_BASE}/${filename}"

  echo "下载 ${filename}: ${url}"
  rm -f "${tmp_target}"
  curl -fL --retry 3 --connect-timeout 10 --max-time 180 -o "${tmp_target}" "${url}"

  if [[ ! -s "${tmp_target}" ]]; then
    rm -f "${tmp_target}"
    echo "下载失败：${filename} 为空文件。"
    return 1
  fi

  mv "${tmp_target}" "${target}"
  chmod 0644 "${target}"
}

ensure_env_value() {
  local key="$1"
  local value="$2"

  if [[ ! -f "${ENV_FILE}" ]]; then
    return
  fi

  if grep -qE "^${key}=" "${ENV_FILE}"; then
    local current_value
    current_value="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 | cut -d= -f2-)"
    if [[ -n "${current_value}" && "${FORCE_ENV}" != "--force-env" ]]; then
      return
    fi
    sed -i "s#^${key}=.*#${key}=${value}#" "${ENV_FILE}"
    return
  fi

  if ! grep -q "IP 地区库" "${ENV_FILE}"; then
    echo "" >> "${ENV_FILE}"
    echo "# IP 地区库，由 deploy/scripts/install-ip2region.sh 准备。" >> "${ENV_FILE}"
  fi

  {
    echo "${key}=${value}"
  } >> "${ENV_FILE}"
}

mkdir -p "${TARGET_DIR}"

download_xdb "${V4_FILENAME}"
download_xdb "${V6_FILENAME}"

ensure_env_value "LYCAN_IP2REGION_V4_XDB_PATH" "${V4_CONTAINER_PATH}"
ensure_env_value "LYCAN_IP2REGION_V6_XDB_PATH" "${V6_CONTAINER_PATH}"

echo "IP 地区库已准备完成："
ls -lh "${TARGET_DIR}/${V4_FILENAME}" "${TARGET_DIR}/${V6_FILENAME}"
echo ""
echo "Docker 容器内路径："
echo "  LYCAN_IP2REGION_V4_XDB_PATH=${V4_CONTAINER_PATH}"
echo "  LYCAN_IP2REGION_V6_XDB_PATH=${V6_CONTAINER_PATH}"
echo ""
echo "下一步执行：docker compose --env-file .env up -d --force-recreate backend"
