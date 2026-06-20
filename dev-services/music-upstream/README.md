# 网易云音乐上游

本地开发与生产镜像统一使用固定版本 `NeteaseCloudMusicApi 4.32.0`。

```powershell
cd D:\Portfolio\Website\LycanClaw\backend\dev-services\music-upstream
pnpm install --frozen-lockfile
pnpm run start
```

默认监听 `http://127.0.0.1:3000`。生产环境由本目录的 `Dockerfile` 构建，不直接暴露公网端口。
