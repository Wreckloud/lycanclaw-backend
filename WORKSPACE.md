# LycanClaw 工作区

这个目录集中保存博客前端、后端和本地上游服务，各代码仓库仍保持独立 Git 历史。

## 目录

- `../frontend`：VitePress 博客和 Obsidian 内容仓库。
- `.`：Spring Boot API、管理控制台和部署配置仓库。
- `dev-services/music-upstream`：网易云音乐 API 本地上游。
- `dev-services/waline`：Waline 本地评论服务。
- `scripts/local`：本地统一启停脚本。
- `runtime`：本地日志、进程状态和旧运行数据，不属于源码。
- `archive`：暂存未完成或不再直接使用的历史目录。

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

脚本会读取 `backend/dev-services/waline/.env.local` 的本地 MySQL 密码供 Waline 和后端联调，不会把密码写入日志或进程状态文件。
