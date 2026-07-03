# LycanClaw Backend

LycanClaw 个人博客后端，基于 Spring Boot 3、MySQL、Waline 和网易云音乐上游服务。

## 技术栈

- Java 17
- Spring Boot 3.4
- Spring Data JPA
- Flyway
- MySQL 8
- Waline
- Maven

## 模块职责

- `analytics`：访问、阅读进度、催更、访客身份和音乐收听统计。
- `comment`：最新评论聚合与管理端 Waline 评论管理。
- `music`：网易云登录、歌曲数据、播放地址和播放流。
- `recommendation`：文章候选、手动推荐和离线热度聚合。
- `tag`：管理端读取 VitePress 索引并检查标签状态。
- `admin`：统一管理控制台、管理会话和运维摘要。
- `waline`：Waline HTTP 网关与同源登录代理。
- `game`：九宫叠阵在线房间、WebSocket 消息和服务端规则裁判。
- `common`：统一响应、错误码、时间、IP 解析和限流。

前台标签筛选仍由 VitePress 静态数据完成，后端标签模块只服务管理端检查。

## 主要入口

- 博客管理登录：`http://127.0.0.1:8080/admin/auth.html`
- 统一管理控制台：`http://127.0.0.1:8080/admin/index.html`
- Swagger UI：`http://127.0.0.1:8080/swagger-ui.html`
- Actuator 健康检查：`http://127.0.0.1:8080/actuator/health`

管理端主流程使用 Waline QQ/GitHub 登录换取后端内存会话，并同时校验 Waline 管理员角色与 QQ 白名单。静态管理员令牌仅作为可选应急入口，默认不配置。

## 主要 API

### 前台

- `GET /api/recommendations`：手动推荐优先，数据库热度快照补齐。
- `GET /api/comments/recent`：最新评论摘要。
- `GET|POST /api/stats/pageview`：文章阅读量。
- `POST /api/analytics/visit/start|end`：访问和有效停留结算。
- `POST /api/analytics/identity/waline`：关联已验证的 Waline 身份。
- `POST /api/encouragement/settle`：首页催更批量结算。
- `POST /api/music/analytics/settle`：音乐收听会话结算。
- `/api/music/ranking|track|flow/*`：听歌排行、歌曲详情、歌词和会话播放流。
- `POST /api/game/rooms` 与 `/api/game/ws`：九宫叠阵在线房间创建和 WebSocket 对战。

音乐播放默认先请求公开地址，公开地址不可用时才使用管理员扫码得到的网易云 Cookie 回退。Cookie 只保存在后端内存，不写入数据库或文件；后端重启后需要在管理端重新扫码。所有访客共享同一个登录回退能力，但登录播放地址有全局限流。

### 管理端

- `/api/admin/auth/*`：Waline 身份交换、当前身份和退出。
- `/api/admin/analytics/*`：文章、访客、主题、催更和音乐洞察。
- `/api/admin/comments/*`：评论查询、审核、回复、置顶和删除。
- `/api/admin/waline/export|import`：标准 Waline JSON 导出和覆盖导入。
- `/api/recommendations/admin/*`：推荐候选与手动顺序。
- `/api/admin/governance/article-metrics/sync`：异步同步文章指标快照。
- `/api/admin/console/summary`：管理控制台总览。

除登录交换接口外，管理接口均由 `X-Lycan-Admin-Token` 鉴权。

## 数据存储

MySQL 当前保存：

- 推荐热度快照。
- 页面访问、有效停留时间和阅读进度。
- 匿名访客与已验证 Waline 身份的关联。
- 首页催更结算记录。
- 音乐收听会话。
- Waline 自身评论数据。

手动推荐规则、文章指标快照和访问统计都保存在 MySQL。生产只额外挂载前端构建生成的 `posts.json` 与 `knowledge-stats.json` 供后端只读使用。

## 本地启动

1. 创建空数据库：

```sql
CREATE DATABASE lycanclaw_local_test CHARACTER SET utf8mb4;
```

2. 复制并填写本地 Waline环境变量：

```powershell
Copy-Item dev-services\waline\.env.example dev-services\waline\.env.local
```

3. 在工作区根目录执行统一启动脚本；Flyway会自动创建后端表：

```powershell
.\backend\scripts\local\start-local.ps1
```

默认端口为 `8080`。前端开发环境默认连接 `http://127.0.0.1:8080`，生产环境默认使用同源 `/api` 和 `/waline`。

## 关键配置

- `LYCAN_DB_*`：MySQL 连接。
- `LYCAN_SECURITY_ADMIN_TOKEN`：可选静态管理员令牌。
- `LYCAN_CONTENT_POSTS_JSON_PATH`：VitePress 文章索引。
- `LYCAN_CONTENT_KNOWLEDGE_STATS_JSON_PATH`：知识笔记统计索引。
- `LYCAN_MUSIC_UPSTREAM_BASE_URL`：网易云 API 上游。
- `LYCAN_MUSIC_RANKING_OWNER_UID`：随机流和听歌榜来源用户 UID。
- `LYCAN_MUSIC_PREFERRED_LEVEL`：默认目标音质。
- `LYCAN_WALINE_BASE_URL`：Waline 服务地址。
- `LYCAN_WALINE_PUBLIC_URL`：后端请求 Waline 时携带的可信站点来源。
- `LYCAN_GAME_MESSAGE_RATE_LIMIT_PER_MINUTE`：在线对战 WebSocket 单 IP 消息限流。
- `LYCAN_IP2REGION_V4_XDB_PATH`、`LYCAN_IP2REGION_V6_XDB_PATH`：可选 IP 地区数据库。

完整默认值和注释见 `../src/main/resources/application.yml`。

## 验证

```powershell
mvn test
```

Docker Compose、Nginx、HTTPS、自动前端发布、备份和恢复说明见 `DEPLOYMENT.md`。

本地目录和联调说明见 `LOCAL_DEVELOPMENT.md`。

完整工作区位于 `D:\Portfolio\Website\LycanClaw`，本仓库是其中的 `backend` 子目录。
