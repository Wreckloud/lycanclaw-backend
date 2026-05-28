# LycanClaw 首发部署与升级手册

本文是上线前执行清单，按顺序做即可。

## 1. 统一部署入口（Docker Compose）

部署编排文件：`deploy/docker-compose.yml`  
包含 5 个服务：

- `nginx`（统一入口）
- `backend`（Spring Boot）
- `waline`（评论）
- `mysql`（Waline 数据）
- `ncm-api`（网易云上游）

统一路由：

- `https://你的域名/` -> 前端静态站点
- `https://你的域名/api/*` -> 后端
- `https://你的域名/waline/*` -> Waline

## 2. 环境配置模板

- 前端：`D:/Portfolio/Website/LycanClaw/.env.example`
- 后端：`D:/Portfolio/Website/LycanClawBackend/.env.example`
- Waline：`D:/Portfolio/Website/LycanClawBackend/deploy/waline.env.example`
- 统一部署：`D:/Portfolio/Website/LycanClawBackend/deploy/.env.example`

首次部署：

```bash
cd /path/to/LycanClawBackend/deploy
cp .env.example .env
```

编辑 `.env` 后再启动。

## 3. 首发部署步骤

1. 构建前端静态文件：

```bash
cd /path/to/LycanClaw
pnpm install
pnpm run build
```

2. 启动全套服务：

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash deploy.sh --build
```

3. 验证：

- 首页：`https://你的域名/`
- 后端健康：`https://你的域名/api/health`
- Waline：`https://你的域名/waline/`
- 管理端首页：`https://你的域名/admin/index.html`
- 运维检查页：`https://你的域名/admin/ops-checks.html`

管理端建议额外配置：

- `BACKEND_ADMIN_IP_WHITELIST`

## 4. 升级步骤（代码已更新）

```bash
cd /path/to/LycanClaw
pnpm run build

cd /path/to/LycanClawBackend/deploy/scripts
bash backup.sh
bash deploy.sh --build
```

如果仅改了配置、未改镜像，可直接：

```bash
bash deploy.sh
```

## 5. 标准化备份与恢复

备份（MySQL + backend data）：

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash backup.sh
```

恢复：

```bash
bash restore.sh /path/to/LycanClawBackend/deploy/backups/20260521-120000
```

## 6. 管理员与安全建议

- `BACKEND_ADMIN_TOKEN` 使用高强度随机串。
- `WALINE_OWNER_EMAIL` 设为你本人邮箱，并执行：
  - `deploy/scripts/enforce-waline-admin.ps1`
- 生产建议只开放 `80/443`，其余端口走内网。
