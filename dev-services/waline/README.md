# Waline 本地联调

此目录只服务 Windows 本地开发，生产环境使用 Compose 中固定版本的 Waline镜像。

```powershell
cd D:\Portfolio\Website\LycanClaw\backend\dev-services\waline
Copy-Item .env.example .env.local
pnpm install --frozen-lockfile
pnpm run start
```

首次本地使用时，将 `schema.sql` 导入本地 Waline数据库。真实生产评论使用 Waline导出/导入能力管理，不使用本地迁移脚本。

`.env.local`、`node_modules` 和日志均被 Git忽略。
