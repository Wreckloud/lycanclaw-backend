#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${IP2REGION_TARGET_DIR:-${DEPLOY_DIR}/data/ip2region}"
ENV_FILE="${DEPLOY_DIR}/.env"
VERSION="${IP2REGION_VERSION:-v3.9.0}"
GITHUB_RAW_BASE="https://raw.githubusercontent.com/lionsoul2014/ip2region/${VERSION}/data"
DOWNLOAD_MAX_TIME="${IP2REGION_DOWNLOAD_MAX_TIME:-1800}"
FORCE_ENV="false"
WITH_IPV6="false"

V4_FILENAME="ip2region_v4.xdb"
V6_FILENAME="ip2region_v6.xdb"
V4_CONTAINER_PATH="/data/ip2region/${V4_FILENAME}"
V6_CONTAINER_PATH="/data/ip2region/${V6_FILENAME}"

for arg in "$@"; do
  case "${arg}" in
    --force-env)
      FORCE_ENV="true"
      ;;
    --with-ipv6)
      WITH_IPV6="true"
      ;;
    -h|--help)
      echo "用法：bash install-ip2region.sh [--force-env] [--with-ipv6]"
      echo "默认只安装 IPv4；IPv6 数据库较大且个人博客通常不需要。"
      exit 0
      ;;
    *)
      echo "未知参数：${arg}"
      exit 1
      ;;
  esac
done

download_xdb() {
  local filename="$1"
  local target="${TARGET_DIR}/${filename}"
  local tmp_target="${target}.tmp"
  local url="${GITHUB_RAW_BASE}/${filename}"

  if [[ -s "${target}" ]]; then
    echo "已存在 ${filename}，跳过下载。"
    return
  fi

  echo "下载 ${filename}: ${url}"
  curl -fL -C - --retry 8 --retry-all-errors --connect-timeout 15 --max-time "${DOWNLOAD_MAX_TIME}" -o "${tmp_target}" "${url}"

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
    if [[ -n "${current_value}" && "${FORCE_ENV}" != "true" ]]; then
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

ensure_env_value "LYCAN_IP2REGION_V4_XDB_PATH" "${V4_CONTAINER_PATH}"
if [[ "${WITH_IPV6}" == "true" ]]; then
  download_xdb "${V6_FILENAME}"
  ensure_env_value "LYCAN_IP2REGION_V6_XDB_PATH" "${V6_CONTAINER_PATH}"
else
  ensure_env_value "LYCAN_IP2REGION_V6_XDB_PATH" ""
fi

echo "IP 地区库已准备完成："
ls -lh "${TARGET_DIR}/${V4_FILENAME}"
if [[ "${WITH_IPV6}" == "true" ]]; then
  ls -lh "${TARGET_DIR}/${V6_FILENAME}"
fi
echo ""
echo "Docker 容器内路径："
echo "  LYCAN_IP2REGION_V4_XDB_PATH=${V4_CONTAINER_PATH}"
if [[ "${WITH_IPV6}" == "true" ]]; then
  echo "  LYCAN_IP2REGION_V6_XDB_PATH=${V6_CONTAINER_PATH}"
else
  echo "  LYCAN_IP2REGION_V6_XDB_PATH="
fi
echo ""
echo "下一步执行：docker compose --env-file .env up -d --force-recreate backend"
