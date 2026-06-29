# LycanClaw 本地开发

工作区集中保存博客前端、后端和本地上游服务，前后端仓库仍保持独立 Git 历史。

## 目录

- `frontend`：VitePress 博客和 Obsidian 内容仓库。
- `backend`：Spring Boot API、管理控制台和部署配置仓库。
- `backend/dev-services/music-upstream`：网易云音乐 API 本地上游。
- `backend/dev-services/waline`：Waline 本地评论服务。
- `backend/scripts/local`：本地统一启停脚本。
- `runtime`：脚本按需创建的本地日志和进程状态，不属于源码。

工作区根目录的 `runtime` 只保存本地日志和进程状态，不属于源码。

## 本地启动

先启动本机 MySQL，然后在工作区根目录执行：

```powershell
.\backend\scripts\local\start-local.ps1
```

服务入口：

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:8080`
- 管理端：`http://127.0.0.1:8080/admin/auth.html`
- 网易云上游：`http://127.0.0.1:3000`
- Waline：`http://127.0.0.1:8360`

停止由脚本启动的进程：

```powershell
.\backend\scripts\local\stop-local.ps1
```

查看状态：

```powershell
.\backend\scripts\local\status-local.ps1
```

脚本会读取 `backend/.env.local` 的后端配置，以及 `backend/dev-services/waline/.env.local` 的本地 Waline 配置，不会把密码写入日志或进程状态文件。

## 首次准备

后端：

```powershell
Copy-Item .\backend\.env.example .\backend\.env.local
```

至少填写 `LYCAN_MUSIC_RANKING_OWNER_UID`；其他非敏感默认值可以保持示例配置。

Waline：

```powershell
Copy-Item .\backend\dev-services\waline\.env.example .\backend\dev-services\waline\.env.local
pnpm --dir .\backend\dev-services\waline install --frozen-lockfile
```

首次使用时，将 `backend/dev-services/waline/schema.sql` 导入本地 Waline 数据库。真实生产评论只通过 Waline 导出和导入管理。

网易云音乐上游：

```powershell
pnpm --dir .\backend\dev-services\music-upstream install --frozen-lockfile
```

本地与生产统一使用固定版本 `NeteaseCloudMusicApi 4.32.0`。`.env.local`、`node_modules`、日志和 `runtime` 均不进入 Git。
