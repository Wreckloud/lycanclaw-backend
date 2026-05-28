# LycanClaw 后端

这是 LycanClaw 博客后端项目，基于 Spring Boot。

## 技术栈

- Java 17
- Spring Boot 3.4.x
- Maven
- MySQL（后续接入，当前默认不强依赖数据库启动）

## 已初始化内容

- 统一接口前缀：`/api`（不使用 `/v1`）
- 统一响应模型：`ApiResponse`
- 全局异常处理
- 基础健康检查接口：`GET /api/health`
- 业务骨架：
  - 标签：`/api/tags/*`
  - 音乐：`/api/music/*`
  - 日贡献：`/api/contributions/daily`
  - 评论聚合：`/api/comments/*`
  - 阅读统计聚合：`/api/stats/pageview`
  - 推荐：`/api/recommendations`

## 当前已落地的第一个真实 API

`GET /api/contributions/daily`

- 数据口径：`additions + deletions`
- 默认范围：过去 365 天
- 默认统计目录：`docs/thoughts`、`docs/knowledge`
- 数据来源：博客仓库 Git 日志（服务端执行 `git log --numstat`）

可选参数：

- `days`：统计天数（例如 `?days=90`）

## 音乐登录（二维码）已打通

已提供管理员专用接口（需要管理员令牌）：

- `GET /api/music/auth/qr/key`
- `GET /api/music/auth/qr/create?key=...`
- `GET /api/music/auth/qr/check?key=...`
- `GET /api/music/auth/status`
- `POST /api/music/auth/refresh`
- `POST /api/music/auth/logout`

管理页面：

- `GET /admin/index.html`
- `GET /admin/music-login.html`
- `GET /admin/recommendation-admin.html`
- `GET /admin/ops-checks.html`

> 说明：二维码登录是否需要二次确认，取决于网易云 App 安全策略。  
> 后端不绕过验证，只托管你扫码后获得的登录态。

## 管理员登录（Waline 会话）已落地

- `POST /api/admin/auth/waline/exchange`
  - 入参：`{ "walineToken": "..." }`
  - 说明：Waline QQ 登录成功后，用该 token 换取后端管理会话 token
- `GET /api/admin/auth/me`
  - 查询当前管理凭证对应身份（静态 token / 会话 token）
- `POST /api/admin/auth/logout`
  - 注销当前后端管理会话

管理页面入口：

- `GET /admin/auth.html`（先登录）
- `GET /admin/index.html`
- `GET /admin/music-login.html`
- `GET /admin/recommendation-admin.html`
- `GET /admin/ops-checks.html`

## 音乐数据接口（已切后端）

- `GET /api/music/ranking/weekly?limit=20`
- `GET /api/music/track/url?id=歌曲ID&level=jymaster`
- `GET /api/music/track/detail-with-url?id=歌曲ID&level=jymaster`
- `GET /api/music/queue?limit=30`
- `POST /api/music/queue/enqueue`
- `POST /api/music/queue/next`
- `POST /api/music/queue/current`
- `POST /api/music/queue/remove`
- `POST /api/music/queue/clear?keepCurrent=true`

说明：

- 排行榜优先使用当前登录账号；未登录时回退 `lycan.music.fallback-uid`。
- 播放地址会按音质降级尝试（`jymaster -> exhigh -> lossless -> hires -> standard`）。
- 队列接口支持“插队并恢复被打断歌曲”的基础语义（通过入队参数控制）。
- 入队参数支持 `dedupeMode`：
  - `replace`（默认）：替换掉同歌曲旧队列项
  - `skip`：若已在播放或队列中存在则跳过
  - `allow`：允许重复入队

## 推荐阅读接口（已落地）

- `GET /api/recommendations?limit=5&excludePath=/thoughts/xxx.html`
  - 热门分算法：`浏览量 * pageviewWeight + 评论数 * commentWeight`
  - 数据来源：`posts.json + Waline(article/comment)`
- `GET /api/recommendations/admin/config`（管理员）
- `GET /api/recommendations/admin/candidates?limit=200`（管理员）
- `PUT /api/recommendations/admin/config`（管理员）
  - Body: `{ \"manualUrls\": [\"/thoughts/xxx.html\"] }`
  - 管理员手动置顶优先于热门分排序

## 标签接口（已落地）

- `GET /api/tags/thoughts`
  - 返回标签列表与文章计数
- `GET /api/tags/thoughts/filter?tag=反刍日志&page=1&pageSize=10`
  - 返回指定标签下文章列表（服务端分页）

## 评论与阅读统计聚合接口（已落地）

- `GET /api/comments/recent?limit=5`
- `GET /api/comments/count?path=/thoughts/xxx.html`
- `GET /api/stats/pageview?path=/thoughts/xxx.html`
- `POST /api/stats/pageview`
  - Body: `{ \"path\": \"/thoughts/xxx.html\" }`

说明：

- 以上接口由后端转发到 Waline，前端不再直接请求 Waline 的统计查询接口。
- 评论发布与登录态仍由 Waline 前端组件负责（`/waline` 路由）。
- 管理员身份建议直接在 Waline 侧维护（QQ 登录后仅你的账号设为 administrator）。

## 运维检查项接口（管理员）

- `GET /api/admin/ops/checks`
  - 返回服务状态、同步状态、常见错误提示

## 内容治理接口（管理员）

- `POST /api/admin/governance/recommendations/rebuild`
  - 手动触发推荐缓存重算
- `POST /api/admin/governance/tags/refresh`
  - 手动刷新标签缓存
- `GET /api/admin/governance/sync-status`
  - 返回红黄绿同步状态与缓存状态

说明：

- 管理端接口统一使用 `X-Lycan-Admin-Token` 鉴权；
- 支持双模式：admin 静态 token（应急）+ Waline 交换会话 token（主流程）；
- 管理端启用分钟级限流与访问日志（IP / URI / method / 结果）；
- 已取消 IP 白名单机制，避免换网/代理误锁后台；
- 公共 API 默认记录访问日志（访客 IP / URI / User-Agent），用于后续访客分析与风控回放。

## 启动

```bash
mvn spring-boot:run
```

默认地址：

- `http://localhost:8080`

部署方案：

- `deploy/README.md`（Docker Compose + Nginx 一键部署）
- `deploy.md`（首发部署 + 升级 + 备份恢复清单）

接口文档地址（已接入）：

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Knife4j UI: `http://localhost:8080/doc.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 关键配置

`src/main/resources/application.yml`：

- `lycan.analytics.repo-path`：博客仓库路径
- `lycan.analytics.scope`：统计目录（逗号分隔）
- `lycan.analytics.days`：默认统计天数
- `lycan.cors.allowed-origins`：允许跨域来源
- `lycan.security.admin-token`：静态管理员令牌（应急保底）
- `lycan.security.admin-qq-whitelist`：可换取后端会话的 QQ 白名单（逗号分隔）
- `lycan.security.admin-require-waline-administrator`：是否强制 Waline 角色为 administrator
- `lycan.security.admin-session-ttl-seconds`：后端会话有效期（秒）
- `lycan.security.admin-session-max-size`：内存会话最大数量
- `lycan.security.auth-rate-limit-per-minute`：登录接口限流阈值
- `lycan.security.music-rate-limit-per-minute`：公开音乐接口限流阈值
- `lycan.security.admin-auth-log-enabled`：管理端鉴权访问日志开关
- `lycan.security.public-access-log-enabled`：公共 API 访问日志开关
- `lycan.music.upstream.base-url`：api-enhanced 服务地址
- `lycan.music.fallback-uid`：未登录时排行榜回退账号
- `lycan.music.preferred-level`：默认音质级别
- `lycan.recommendation.posts-json-path`：前端 posts.json 路径
- `lycan.recommendation.manual-config-path`：手动推荐持久化文件路径
- `lycan.recommendation.score.*`：热门分权重配置
- `lycan.waline.base-url`：Waline 服务地址（评论与阅读统计聚合）
- `lycan.tag.posts-json-path`：标签聚合读取的 posts.json 路径
- `lycan.tag.cache-seconds`：标签聚合缓存秒数

环境变量模板：

- `D:/Portfolio/Website/LycanClawBackend/.env.example`（后端）
- `D:/Portfolio/Website/LycanClawBackend/deploy/waline.env.example`（Waline）
- `D:/Portfolio/Website/LycanClaw/.env.example`（前端，单一 `VITE_BACKEND_API_BASE`）

> 当前阶段默认关闭了数据源自动装配，便于先推进无状态 API。  
> 开始接入数据库时再恢复 `spring.datasource` 与 `spring.jpa` 配置。

## 分阶段需求（与你当前规划对齐）

### P0（先做）

1. 热力图后端化（已开始并打通 API）
2. 音乐数据后端化（周听歌榜、队列接口）- 已完成基础版
3. 推荐、标签、评论统计接口后端化 - 已完成基础版

### P1（随后）

1. 首页筛选与统计模块进一步统一到后端 API
2. 推荐算法 API
3. 评论增强能力（自维护 Waline 周边能力）

### P2（后续）

1. 画作/图库能力
2. 管理端写入接口
3. 监控与安全加固
