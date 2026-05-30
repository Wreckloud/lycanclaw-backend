# Docker Compose + Nginx 一键部署（LycanClaw）

本目录提供可直接落地的部署方案，包含：

- `nginx`：统一入口（静态站点 + `/api` + `/waline`）
- `backend`：Spring Boot 后端
- `waline`：评论服务
- `mysql`：Waline 数据库
- `ncm-api`：网易云 API 上游服务

## 1. 前置准备

1. 本机已安装 Docker Desktop（含 `docker compose`）
2. 前端先构建一次静态产物：

```powershell
cd D:\Portfolio\Website\LycanClaw
pnpm run build
```

3. 编辑部署配置：

```powershell
cd D:\Portfolio\Website\LycanClawBackend\deploy
Copy-Item .env.example .env
```

重点改这些：

- `DOMAIN`
- `MYSQL_ROOT_PASSWORD`
- `WALINE_DB_PASSWORD`
- `WALINE_JWT_TOKEN`
- `BACKEND_ADMIN_TOKEN`
- `WALINE_OWNER_EMAIL`
- 三个宿主机路径：`BLOG_REPO_HOST_PATH`、`POSTS_JSON_HOST_PATH`、`FRONTEND_DIST_HOST_PATH`

## 2. 一键启动

Linux/macOS：

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash deploy.sh --build
```

Windows PowerShell：

```powershell
cd D:\Portfolio\Website\LycanClawBackend\deploy\scripts
.\up.ps1 -Build
```

查看状态：

```powershell
docker compose -f ..\docker-compose.yml --env-file ..\.env ps
```

停止：

```powershell
.\down.ps1
```

Linux/macOS 停止：

```bash
docker compose -f ../docker-compose.yml --env-file ../.env down
```

## 3. 路由约定（统一域名）

- `https://你的域名/` -> VitePress 静态站点
- `https://你的域名/api/*` -> 后端 API
- `https://你的域名/waline/*` -> Waline（评论 + 阅读统计）
- `https://你的域名/admin/*` -> 后端内置管理端（操作请求需管理员令牌）

这样前端只需要同域名请求，减少跨域和环境切换复杂度。
其中评论统计与阅读统计已经走 `/api/*` 聚合接口；评论发布仍通过 `/waline` 组件路径。

## 4. 评论增强（Waline 自维护 + 只有你是管理员）

### 4.1 建议配置

- `LOGIN=force`（已在 compose 中设置）
- `WALINE_COMMENT_AUDIT=true/false` 按你需求选择
- 建议用 Waline 的 QQ 登录能力做管理员识别（只保留你自己的账号为 administrator）

### 4.2 把管理员强制白名单为你自己

先用你的账号登录一次 Waline（让用户记录入库），再执行：

```powershell
cd D:\Portfolio\Website\LycanClawBackend\deploy\scripts
.\enforce-waline-admin.ps1
```

该脚本会把 `WALINE_OWNER_EMAIL` 设为 `administrator`，其他人降为 `user`。

> 注意：如果你换了主邮箱，重新跑一次脚本即可。

## 4.3 运维检查页面

部署后可访问：

- `/admin/index.html`
- `/admin/ops-checks.html`

用于查看：

- Waline/NCM 上游状态
- 推荐手动配置更新时间
- posts.json 挂载是否生效
- 常见错误与处理建议

## 4.4 管理端与二次保护

管理入口：

- `/admin/index.html`

当前默认策略：

- 管理接口使用管理员令牌 + 会话令牌鉴权
- 管理接口分钟级限流
- 管理接口访问日志

## 5. HTTPS

当前模板默认暴露 `80` 端口。生产建议加 HTTPS（Cloudflare 或证书反代层）。

## 6. 数据备份与恢复（标准化）

备份：

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash backup.sh
```

恢复：

```bash
bash restore.sh /path/to/LycanClawBackend/deploy/backups/<timestamp>
```

备份内容包含：

- Waline MySQL 数据（`waline.sql`）
- 后端运行目录（`backend-data.tar.gz`，含推荐手动配置等）
- 当次 `compose` 环境快照（`compose.env.snapshot`）

## 6.1 本地测试数据库初始化（可选）

如果你准备把“文章索引 / 推荐管理 / 评论快照 / 阅读统计”逐步迁到本地 MySQL，可先执行：

```sql
SOURCE D:/Portfolio/Website/LycanClawBackend/deploy/sql/local-test-init.sql;
```

脚本会创建数据库 `lycanclaw_local_test` 及基础表结构，供本地联调使用。

## 7. 常见问题

### Q1：`Waline 统计转发到后端统一域名` 是什么意思？

有两种做法：

1. **Waline 直连路径**：前端请求 `https://域名/waline/*`（评论发布/登录）。
2. **后端聚合路径**：前端请求 `https://域名/api/*`（最新评论、评论数、阅读量）。

第二种的好处：

- 前端只认一个 API 域名
- 可以在后端统一鉴权、限流、日志、缓存
- 后续替换 Waline 存储实现时，前端几乎不用改

你现在已经具备 `/api/comments/*` 和 `/api/stats/pageview*` 聚合接口，可直接用于生产切换。
