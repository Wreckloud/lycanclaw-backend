# LycanClaw 首发部署清单

## 1. 构建前端

```bash
cd /path/to/LycanClaw
pnpm install
pnpm run build
```

## 2. 准备环境变量

```bash
cd /path/to/LycanClawBackend/deploy
cp .env.example .env
```

至少修改：

- `DOMAIN`
- `MYSQL_ROOT_PASSWORD`
- `WALINE_DB_PASSWORD`
- `WALINE_JWT_TOKEN`
- `WALINE_OWNER_EMAIL`
- SMTP 配置
- 前端产物、博客仓库和 `posts.json` 的宿主机路径

`BACKEND_ADMIN_TOKEN` 是可选应急凭证。日常管理建议使用 Waline QQ/GitHub 登录。

## 3. 启动

```bash
cd /path/to/LycanClawBackend/deploy/scripts
bash deploy.sh --build
```

统一路由：

- `/`：VitePress 静态站点
- `/api/*`：Spring Boot API
- `/admin/*`：后端内置管理端
- `/waline/*`：经后端代理的 Waline 评论和登录

验证：

- `https://你的域名/`
- `https://你的域名/admin/auth.html`
- `docker compose -f ../docker-compose.yml --env-file ../.env ps`

## 4. 升级

```bash
cd /path/to/LycanClaw
pnpm run build

cd /path/to/LycanClawBackend/deploy/scripts
bash backup.sh
bash deploy.sh --build
```

仅修改环境变量时可执行 `bash deploy.sh`。

## 5. 备份与恢复

```bash
bash backup.sh
bash restore.sh /path/to/backups/<timestamp>
```

备份包含共享 MySQL 数据库、后端 `data/` 目录和部署环境快照。

## 6. 上线检查

- 服务器只公开 `80/443`。
- Nginx 已转发真实来源 IP，后端容器已开启代理头信任。
- MySQL、Waline、网易云上游和后端容器状态正常。
- 管理端可以完成 Waline 登录。
- 推荐热度可手动更新，最新评论和文章统计可读取。
- `backend_data` 与 MySQL 已完成首次备份。
