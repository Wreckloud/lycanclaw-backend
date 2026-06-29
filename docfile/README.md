# LycanClaw

LycanClaw 是我的个人内容站，用来长期整理技术笔记、项目记录、随笔文章和个人创作。

最初基于 hexo 搭建，在我进一步学习之后又改用 VitePress 写了一些vue的组件，这次应该算得上第三次升级了。博客的核心目标仍然让我保持继续使用 Markdown 写作，并通过 Git 自动发布到线上。这次我打算真正让博客拥有后端，而非纯静态，逐步补齐部署、评论、阅读统计和媒体展示等能力。

## 项目定位

这个站点主要承担几类内容：

- 技术学习笔记
- 项目开发记录
- 个人随笔与长期思考
- 画作、设定和创作内容展示

## 当前技术栈

前端与内容构建：

- VitePress
- Markdown
- 自定义主题组件
- 构建前数据生成脚本

当前动态能力：

- 评论（游客可评论，Waline 负责评论与可选登录，后端负责安全代理和管理）
- 阅读量统计（后端聚合到 Waline）

## 评论与身份规则

- 阅读文章不需要登录；发表评论只要求填写称谓，邮箱和个人网站选填。
- QQ、GitHub 登录由 Waline 提供，用于头像和身份增强，不是评论前置条件。
- 管理后台复用 Waline 登录页，但必须同时满足 Waline 管理员角色与 QQ 白名单。
- 匿名访问统计使用 visitorId 的稳定摘要，只用于后台统计，不作为登录或权限凭证。
- 评论头像优先使用 QQ、OAuth 或 Libravatar，缺失或加载失败时才使用网站默认头像。

## Theme 架构规范

### 目标

- 在不改变现有功能的前提下，降低维护成本
- 明确组件职责边界（layout、content、section、common、utils）
- 非必要不新增全局组件注册

### 目录约定

- `docs/.vitepress/theme/components/`
  - `MyLayout.vue`：页面级布局编排
  - `PostList.vue`、`PostTitle.vue`、`Comment.vue`、`DataPanel.vue`：核心内容组件
  - `home/`：首页专用区块
  - `about/`：关于页专用区块
  - `common/`：跨页面复用组件
- `docs/.vitepress/theme/utils/`
  - 运行时服务：`audioService`、`audioManager`
  - 音乐数据服务：`musicApi`（网易云接口聚合与解析）
  - 运行时配置：`runtimePolicy`（统一读取后端入口与运行时注入）
  - 音乐 UI 工具：`audioUi`（时间格式化与进度计算）
  - API 封装：`commentApi`、`pageViewApi`
  - API 响应解析：`apiResponseParsers`
  - 内容数据层：`contentData`
  - 首页统计计算：`homeAnalytics`
  - 纯工具：`contentMetrics`
  - 主题令牌：`themePalette`（热力图、互动组件配色常量）
- `docs/.vitepress/theme/setup/`
  - `registerGlobalComponents`：集中管理全局组件与运行时注入
  - `runtimeEffects`：路由副作用、预加载与首页高度同步
  - `useImageZoom`：图片缩放初始化与路由切换重挂载
- `docs/.vitepress/theme/styles/`
  - 全局样式与修复样式

### 开发规则

- 页面专用组件优先局部 import，不默认全局注册
- 仅当 Markdown 直接使用时才做全局组件注册
- 重复逻辑统一提取到 `utils/`，避免复制粘贴
- 网络请求统一放在 `utils/*Api.ts` 内封装
- 动画和 DOM 生命周期逻辑放在组件内部 `onMounted/onBeforeUnmount`

## 代码规范约定（已执行）

### 文件与语言规范

- `docs/.vitepress/theme/utils/*` 统一使用 TypeScript（`.ts`）
- `docs/.vitepress/theme/components/*` 新增组件默认使用 `<script setup lang="ts">`
- `docs/.vitepress/scripts/*` 保持 `.js`（直接由 Node 执行，避免额外编译链）
- 配置文件保持 `.mjs`（如 `docs/.vitepress/config.mjs`）
- 本地模块导入统一使用无后缀路径（例如 `../utils/time`），不写 `.js/.ts`

### 注释规范

- 只写“为什么”，不写“做了什么”
- 必须注释的场景：
  - 跨浏览器兼容分支（如旧版触摸能力判断）
  - 业务阈值策略（如时间显示阈值、分页窗口规则）
  - 副作用时序（如自动折叠、播放状态联动）
- 禁止冗余注释（变量名已表达清楚时不再重复）

### 风格规范

- 命名：函数 `camelCase`，常量 `UPPER_SNAKE_CASE`，类型 `PascalCase`
- 网络返回值先做 parser，再进入组件渲染
- 组件层只保留展示与交互，数据聚合放 `utils/`，挂载副作用放 `setup/`
- 组件层禁止直接 `fetch`，统一通过 `utils/*Api.ts` 或 `utils/contentData.ts`
- 时间逻辑只走 `time.ts + timeDisplayPolicy.ts`，禁止组件内重复手写日期解析
- 调试日志统一走 `utils/logger.ts`，避免业务代码直接散落 `console.log`

### CSS 规范（新增）

- 全局设计令牌统一维护在 `docs/.vitepress/theme/styles/var.css`（颜色、动效、数字字体、宽度）
- 全局行为层样式统一维护在 `docs/.vitepress/theme/styles/index.css`（页面级布局、动画、可交互行为）
- 组件内只保留“局部结构样式”，避免写全局 reset 和重复动画
- 涉及数字跳动或计时显示，统一使用 `tabular-nums + 固定宽度（ch）`，避免位移抖动
- `transition` 统一改为动效令牌（`--lc-motion-*`），不再在组件里散写 `0.2s/0.3s/ease`
- 常用中性色与强调色改为语义变量（`--lc-c-*`），减少硬编码颜色

### 当前基线

- 当前构建可通过（VitePress `1.6.3`）
- 已清理一批失效脚本和未引用文件
- Theme `utils/` 已全部迁移到 `.ts`，并统一类型导出
- 组件脚本已迁移到 `22/22` 为 TypeScript
- 首页推荐与页脚文案请求已下沉到 `utils/recommendedApi.ts` 与 `utils/siteApi.ts`（推荐已切到后端 `/api/recommendations`）
- 随想页 Tag 列表与筛选已切到后端 `/api/tags/*`，前端不再本地聚合 tags
- 文章字数/阅读时长逻辑已收敛到 `utils/contentMetrics.ts`
- 首页数据读取、筛选、统计已从组件内联逻辑收敛到 `contentData + homeAnalytics`
- 音乐相关组件统一调用 `musicApi`，后端负责播放流与网易云地址解析，浏览器只维护实际播放状态
- 评论与阅读统计查询已统一走后端网关；Waline 仅负责评论交互与登录
- `.gitignore` 已覆盖 Obsidian 元数据目录，避免误提交本地笔记配置
- 页脚计时器秒数显示已改为两位数滚动并固定宽度，消除 `08 -> 09` 等位移抖动

### 下一步清理方向

- 持续补充 ESLint 规则，当前已启用：组件层禁 `fetch`、限制 `console`、禁止 `any`
- 继续收敛 `siteApi` 等剩余接口到同一后端网关
- 逐步收敛首页组件中的动画触发器与 `setTimeout` 调度逻辑

## 后端接入准备（本次整理）

### 接口分层约定

- 组件层：只调用 `utils/*Api.ts`，不直接请求网络
- API 层：只处理请求参数、响应解析、缓存策略
- 配置层：统一通过 `runtimePolicy.ts` 读取环境变量/运行时配置
- 解析层：`apiResponseParsers.ts` 负责兼容第三方与自建返回结构差异

### 可替换配置入口

- 环境变量仅保留：`VITE_BACKEND_API_BASE`
- `window.__LYCAN_CONFIG`（运行时注入）
  - `backendApiBase`

### 后端迁移建议顺序

1. 统一网关：后端提供 `/api/recommendations` 与 `/api/music`，贡献热力图使用构建期静态 JSON
2. 评论发布继续走 Waline，评论/阅读统计统一走后端 `/api/*`
3. 前端仅修改 `runtimePolicy` 与各 `*Api` 文件，不动组件

## 时间显示规范

为避免各组件重复手写日期解析，时间显示统一由 `docs/.vitepress/theme/utils/time.ts` 负责。

### 场景规则

- 文章详情元信息：显示绝对时间到秒（`YYYY年MM月DD日 HH:mm:ss`）
- 文章列表页：显示绝对日期（`YYYY年MM月DD日`）
- 首页推荐：显示简短日期（`MM月DD日`）
- 近期动态：`1-6天` 显示 `X天前`，`7-13天` 显示 `1周前`，更久显示绝对日期
- 最新评论：`1-6天` 显示 `X天前`，之后按 `X周前` 递进，超过 1 个月后显示绝对日期
- 任何跨年的绝对日期都必须显示年份（例如 `2025年07月08日`）

### 统一工具

- `parseDateInput(input)`：统一解析 frontmatter 与 API 返回的日期
- `formatDateCn(input, { withYear, withTime })`：绝对时间格式化
- `formatMonthDayCn(input)`：简短日期格式化
- `formatRelativeTimeCn(input, options)`：相对时间格式化
  - 支持 `maxRelativeWeeks`，用于限制最多显示到几周前（例如近期动态只显示 `1周前`）
- `timeDisplayPolicy.ts`：统一管理“近期动态/最新评论”等时间展示策略，组件只调用封装方法

### 代码约束

- 组件内不再新增手写 `formatDate`、`new Date(string)` 解析逻辑
- 日期排序前先调用 `parseDateInput`，避免跨浏览器解析差异

## 随想 Tag 筛选

- 随想页顶部提供标签筛选（含“全部”）
- 筛选状态通过 URL 查询参数 `?tag=` 持久化，可分享直达
- 首页近期动态标签点击可跳转到随想页并自动带上筛选参数

## 维护检查

- `pnpm run build`：验证数据生成 + VitePress 构建完整链路
- `pnpm run lint`：验证主题层代码规范（禁组件直连 fetch / 禁新增 any / 限制 console）
- `pnpm run check:theme`：验证主题架构约束（theme 不新增 JS、组件强制 TS、组件禁直连 fetch）
- `pnpm run generate-data`：生成文章、知识统计和 Git 日贡献静态数据

## Git 忽略约定

- 已忽略 Obsidian 元数据目录：`.obsidian/`、`**/.obsidian/`、`.trash/`
- 构建产物与缓存不入库：`docs/.vitepress/cache/`、`docs/.vitepress/dist/`

## 运行时接口配置

- 环境变量只保留：`VITE_BACKEND_API_BASE`
- Waline 地址由后端入口推导：生产走 `${VITE_BACKEND_API_BASE}/waline`，本地 `127.0.0.1:8080` 自动回退到 `127.0.0.1:8360`
- 也可通过运行时配置覆盖（页面注入 `window.__LYCAN_CONFIG`）：
  - `backendApiBase`

## 发布流程

- 双击 `update-blog.bat`：生成数据、提交并推送当前分支。
- 开发分支推送只执行 GitHub Actions 检查，不发布正式站。
- `master` 推送通过检查后自动发布到国内服务器。
- Netlify继续从 `master` 构建备用站，并固定访问 `https://wreckloud.com` 后端。
- GitHub Actions所需 Secrets 与服务器目录配置见后端仓库 `doc/DEPLOYMENT.md`。
