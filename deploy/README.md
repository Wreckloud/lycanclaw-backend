# Docker Compose 部署

本目录使用一套 Compose 编排：

- `nginx`：统一入口和前端静态文件。
- `backend`：Spring Boot API 与管理端。
- `waline`：评论和 OAuth 登录。
- `mysql`：Waline 与博客统计共享数据库。
- `ncm-api`：网易云音乐上游。

## 准备

```powershell
cd D:\Portfolio\Website\LycanClaw
pnpm run build

cd D:\Portfolio\Website\LycanClawBackend\deploy
Copy-Item .env.example .env
```

编辑 `.env` 中的域名、数据库密码、Waline JWT、邮件配置和宿主机路径。

IP 地区解析是可选能力。需要地区信息时，准备 ip2region XDB 文件并填写对应路径；未准备时后台仍可显示原始 IP。

## 启停

Windows：

```powershell
cd D:\Portfolio\Website\LycanClawBackend\deploy\scripts
.\up.ps1 -Build
.\down.ps1
```

Linux：

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash deploy.sh --build
docker compose -f ../docker-compose.yml --env-file ../.env down
```

查看容器：

```powershell
docker compose -f ..\docker-compose.yml --env-file ..\.env ps
```

## 路由

- `https://你的域名/`：博客。
- `https://你的域名/api/*`：后端接口。
- `https://你的域名/admin/auth.html`：管理登录。
- `https://你的域名/admin/index.html`：统一管理控制台。
- `https://你的域名/waline/*`：Waline 评论与登录代理。

Waline 必须经过后端代理，才能统一 OAuth 回调、登录入口过滤和站点样式。

## Waline 管理员

先用你的 QQ 或 GitHub 登录一次，再执行：

```powershell
cd D:\Portfolio\Website\LycanClawBackend\deploy\scripts
.\enforce-waline-admin.ps1
```

脚本按 `WALINE_OWNER_EMAIL` 保留唯一管理员。管理端评论操作必须使用 Waline 登录会话；静态管理员令牌不能代替 Waline 评论身份。

## 邮件通知

在 `.env` 中填写：

- `WALINE_SMTP_HOST` 或 `WALINE_SMTP_SERVICE`
- `WALINE_SMTP_PORT`
- `WALINE_SMTP_SECURE`
- `WALINE_SMTP_USER`
- `WALINE_SMTP_PASS`
- `WALINE_AUTHOR_EMAIL`
- `WALINE_SENDER_NAME`
- `WALINE_SENDER_EMAIL`

修改后重启 Waline。管理端只显示配置状态，不读取或展示 SMTP 密码。

## 备份与恢复

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash backup.sh
bash restore.sh /path/to/backups/<timestamp>
```

备份包含：

- 共享 MySQL 数据库。
- 后端 `data/` 持久化目录。
- 当次 Compose 环境快照。

## 本地数据库

不使用 Compose 时，可执行：

```sql
SOURCE D:/Portfolio/Website/LycanClawBackend/deploy/sql/local-test-init.sql;
```

Spring JPA 本地默认使用 `ddl-auto=update`。生产环境可改为 `validate`，并由 SQL 迁移维护表结构。
