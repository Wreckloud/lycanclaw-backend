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

- `GET /admin/music-login.html`

> 说明：二维码登录是否需要二次确认，取决于网易云 App 安全策略。  
> 后端不绕过验证，只托管你扫码后获得的登录态。

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

## 启动

```bash
mvn spring-boot:run
```

默认地址：

- `http://localhost:8080`

## 关键配置

`src/main/resources/application.yml`：

- `lycan.analytics.repo-path`：博客仓库路径
- `lycan.analytics.scope`：统计目录（逗号分隔）
- `lycan.analytics.days`：默认统计天数
- `lycan.cors.allowed-origins`：允许跨域来源
- `lycan.security.admin-token`：音乐登录管理员令牌（必须改成强随机值）
- `lycan.security.auth-rate-limit-per-minute`：登录接口限流阈值
- `lycan.security.music-rate-limit-per-minute`：公开音乐接口限流阈值
- `lycan.music.upstream.base-url`：api-enhanced 服务地址
- `lycan.music.fallback-uid`：未登录时排行榜回退账号
- `lycan.music.preferred-level`：默认音质级别

> 当前阶段默认关闭了数据源自动装配，便于先推进无状态 API。  
> 开始接入数据库时再恢复 `spring.datasource` 与 `spring.jpa` 配置。

## 分阶段需求（与你当前规划对齐）

### P0（先做）

1. 热力图后端化（已开始并打通 API）
2. 音乐数据后端化（周听歌榜、队列接口）- 进行中
3. 前端逐步移除静态 JSON 生成依赖

### P1（随后）

1. 随想标签聚合和筛选 API
2. 推荐算法 API
3. 评论增强能力（自维护 Waline 周边能力）

### P2（后续）

1. 画作/图库能力
2. 管理端写入接口
3. 监控与安全加固
