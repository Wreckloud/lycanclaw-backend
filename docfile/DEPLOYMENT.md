# LycanClaw 单服务器部署

目标结构：宿主机 Nginx 提供静态站与 HTTPS，Docker Compose 只运行 Spring Boot、Waline、网易云上游和 MySQL。普通文章更新由前端 GitHub Actions 自动发布，后端只手动更新。

## 1. 安装 Ubuntu 24.04 基础环境

使用云厂商控制台重装 Ubuntu Server 24.04 LTS。首次登录后执行：

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y ca-certificates curl gnupg git nginx certbot python3-certbot-nginx rsync ufw

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"
```

退出 SSH 后重新登录，再验证：

```bash
docker version
docker compose version
```

## 2. 防火墙和 SSH

先确认当前账号已经能用 SSH 密钥登录，再关闭密码登录：

```bash
sudo tee /etc/ssh/sshd_config.d/99-lycanclaw-hardening.conf >/dev/null <<'EOF'
PasswordAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
EOF

sudo sshd -t
sudo systemctl reload ssh

sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw --force enable
sudo ufw status
```

云厂商安全组同样只开放 SSH、80、443。

## 3. 克隆私有后端仓库

在服务器生成只读部署密钥：

```bash
ssh-keygen -t ed25519 -f ~/.ssh/lycanclaw_backend -C lycanclaw-backend-server
cat ~/.ssh/lycanclaw_backend.pub
```

将公钥添加到 GitHub 后端仓库 `Settings → Deploy keys`，不要勾选写权限。然后配置 SSH 别名：

```bash
cat >> ~/.ssh/config <<'EOF'
Host github-lycanclaw-backend
  HostName github.com
  User git
  IdentityFile ~/.ssh/lycanclaw_backend
  IdentitiesOnly yes
EOF
chmod 600 ~/.ssh/config

sudo mkdir -p /opt/lycanclaw
sudo chown "$USER":"$USER" /opt/lycanclaw
git clone git@github-lycanclaw-backend:Wreckloud/lycanclaw-backend.git /opt/lycanclaw/backend
```

## 4. 准备前端发布用户

本机生成一把只供 GitHub Actions 使用的密钥：

```powershell
ssh-keygen -t ed25519 -f $HOME\.ssh\lycanclaw_actions -C lycanclaw-actions
Get-Content $HOME\.ssh\lycanclaw_actions
```

在服务器创建无 sudo 权限的发布用户，把对应 `.pub` 内容写入公钥位置：

```bash
sudo adduser --disabled-password --gecos '' deploy
sudo install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
sudo tee /home/deploy/.ssh/authorized_keys >/dev/null <<'EOF'
粘贴 lycanclaw_actions.pub 内容
EOF
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys

sudo install -d -m 755 /srv/lycanclaw/bin
sudo install -d -m 755 -o deploy -g deploy /srv/lycanclaw/frontend
sudo install -d -m 755 -o deploy -g deploy /srv/lycanclaw/frontend/releases
sudo install -d -m 755 -o deploy -g deploy /srv/lycanclaw/shared
sudo install -m 755 /opt/lycanclaw/backend/deploy/scripts/activate-frontend /srv/lycanclaw/bin/activate-frontend
```

在前端 GitHub 仓库添加 Secrets：

- `DEPLOY_HOST`：服务器公网 IP。
- `DEPLOY_USER`：`deploy`。
- `DEPLOY_SSH_KEY`：`lycanclaw_actions` 私钥完整内容。
- `DEPLOY_KNOWN_HOSTS`：在可信本机执行 `ssh-keyscan -H 服务器IP` 得到的整行内容。

Actions只允许该用户写 `/srv/lycanclaw/frontend` 和 `/srv/lycanclaw/shared`，不能操作 Docker或系统配置。

## 5. 域名与 HTTPS

将 `wreckloud.com` 和 `www.wreckloud.com` 的 A 记录指向服务器公网 IP。先启用 HTTP 配置：

```bash
sudo cp /opt/lycanclaw/backend/deploy/nginx/lycanclaw-http.conf /etc/nginx/sites-available/lycanclaw
sudo ln -sfn /etc/nginx/sites-available/lycanclaw /etc/nginx/sites-enabled/lycanclaw
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

sudo certbot --nginx -d wreckloud.com -d www.wreckloud.com
sudo certbot renew --dry-run
```

证书成功后覆盖为正式配置：

```bash
sudo cp /opt/lycanclaw/backend/deploy/nginx/lycanclaw.conf /etc/nginx/sites-available/lycanclaw
sudo nginx -t
sudo systemctl reload nginx
```

正式配置统一跳转到 `https://wreckloud.com`，并且覆盖客户端伪造的 `X-Forwarded-For`。

确认生效的 Nginx 配置里 `/waline/` 代理到后端 `127.0.0.1:8080`，不要代理到 Waline 容器端口。Waline 统一由后端同源代理转发，以保留评论昵称校验、OAuth 界面过滤和阅读量写入拦截。

在线对战依赖 WebSocket，正式配置中必须存在独立的 `location = /api/game/ws`，并包含 `Upgrade` 与 `Connection "upgrade"` 代理头。后端更新后如果线上提示在线对战不可用，先检查宿主机 Nginx 是否仍在使用旧配置：

```bash
sudo nginx -T | grep -nA12 'location = /api/game/ws'
sudo nginx -T | grep -nA8 'location /waline/'
```

如果看不到这两个配置块，重新应用仓库里的正式 Nginx 配置：

```bash
sudo cp /opt/lycanclaw/backend/deploy/nginx/lycanclaw.conf /etc/nginx/sites-available/lycanclaw
sudo nginx -t
sudo systemctl reload nginx
```

## 6. 启动后端

```bash
cd /opt/lycanclaw/backend/deploy
cp .env.example .env
chmod 600 .env
nano .env
```

至少更换所有 `change-me-*` 值，并填写这些必需项：

- `MUSIC_RANKING_OWNER_UID`：网易云公开歌单/排行所属用户 ID，后端启动时会强制校验。
- `BACKEND_ADMIN_QQ_WHITELIST`：允许进入后台的 Waline QQ 身份。
- `WALINE_AUTHOR_EMAIL`：作者通知邮箱，也用于管理员维护脚本识别唯一 Waline 管理员。
- SMTP 相关配置：需要邮件通知时填写；暂时不用邮件时可先保留占位，但后台会显示通知不可用。

`BACKEND_ADMIN_TOKEN` 是应急静态管理员令牌；日常使用 Waline 登录后台，可以留空。然后：

```bash
cd /opt/lycanclaw/backend/deploy/scripts
bash deploy.sh --build
curl --fail http://127.0.0.1:8080/actuator/health
```

前端尚未第一次发布时，后端会提示共享 JSON 暂缺，但仍可启动。前端 `master` 首次 Actions成功后会自动补齐。

Waline 1.41.1 使用的 MySQL 客户端不支持 MySQL 8.4 默认的 `caching_sha2_password` 认证。首次部署或看到 `ER_NOT_SUPPORTED_AUTH_MODE` 时，执行一次认证协议修复：

```bash
cd /opt/lycanclaw/backend/deploy
docker compose --env-file .env up -d --force-recreate mysql
cd scripts
bash fix-waline-mysql-auth.sh
cd ..
docker compose --env-file .env up -d --force-recreate waline backend
```

## 7. 前端首次发布

前端开发分支验证完成后合并到 `master` 并推送。GitHub Actions会：

1. 执行类型检查、Lint、主题检查和生产构建。
2. 上传到 `/srv/lycanclaw/frontend/releases/<commit>`。
3. 原子切换 `current` 软链接。
4. 同步 `posts.json` 和 `knowledge-stats.json` 给后端。
5. 只保留最近三个前端版本。

Netlify继续构建 `https://lycanclaw.netlify.app/`，并通过 `VITE_BACKEND_API_BASE` 使用国内后端。

## 8. 导入真实 Waline 数据

服务器数据库首次启动为空。不要迁移本地测试数据库。先使用 Waline QQ 或 GitHub 登录，并确保该账号邮箱与 `WALINE_AUTHOR_EMAIL` 一致，然后执行：

```bash
cd /opt/lycanclaw/backend/deploy/scripts
bash enforce-waline-admin.sh
```

进入 `https://wreckloud.com/admin/`，使用“初始化导入 JSON”上传标准 Waline version 1 备份。该接口只接受空数据库；导入前会完整校验，失败时清理本次写入，成功后立即同步文章指标。

确认真实评论、计数和管理员身份后再开放评论入口。评论允许游客提交，只要求称谓；邮箱和个人网站均为选填，登录仅用于增强身份和管理后台。

## 9. 后端手动更新

```bash
cd /opt/lycanclaw/backend
git pull --ff-only
cd deploy/scripts
bash backup.sh
bash deploy.sh --build
curl --fail http://127.0.0.1:8080/actuator/health
```

普通文章更新不执行这些命令，也不重启后端。

如果这次后端更新包含 `deploy/nginx` 下的配置变化，还需要同步宿主机 Nginx：

```bash
sudo cp /opt/lycanclaw/backend/deploy/nginx/lycanclaw.conf /etc/nginx/sites-available/lycanclaw
sudo nginx -t
sudo systemctl reload nginx
```

## 10. 自动备份与恢复

安装每日 03:30 备份任务：

```bash
sudo cp /opt/lycanclaw/backend/deploy/systemd/lycanclaw-backup.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lycanclaw-backup.timer
systemctl list-timers lycanclaw-backup.timer
```

当前备份范围是共享 MySQL 数据库，包含 Waline 评论、计数、用户、后台统计、推荐规则和文章指标快照；不包含 `.env`。前端 `posts.json`、`knowledge-stats.json` 和静态产物可由 GitHub Actions 重新发布生成。备份保留14天。手动备份和恢复：

```bash
cd /opt/lycanclaw/backend/deploy/scripts
bash backup.sh
bash restore.sh /opt/lycanclaw/backend/deploy/backups/<时间戳>
```

定期下载到 Windows 本机：

```powershell
scp -r 用户名@服务器IP:/opt/lycanclaw/backend/deploy/backups/<时间戳> D:\Backups\LycanClaw\
```

## 上线检查

```bash
curl -I https://wreckloud.com/
curl https://wreckloud.com/api/recommendations
curl https://wreckloud.com/api/game/rooms
curl https://wreckloud.com/waline/article?path=%2F
curl https://wreckloud.com/actuator/health  # 应为 404，健康端点不对公网开放
curl -i --http1.1 \
  -H 'Connection: Upgrade' \
  -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==' \
  https://wreckloud.com/api/game/ws
docker compose -f /opt/lycanclaw/backend/deploy/docker-compose.yml --env-file /opt/lycanclaw/backend/deploy/.env ps
```

WebSocket 检查应返回 `101 Switching Protocols`。如果返回 `400 Can "Upgrade" only to "WebSocket".`，说明请求已经到达后端，但宿主机 Nginx 没有把 WebSocket Upgrade 头正确转发，重新应用正式 Nginx 配置。

如果 `/api/comments/recent` 返回 Waline 403，优先检查 `SECURE_DOMAINS` 是否使用不带协议的域名，例如 `wreckloud.com,www.wreckloud.com,lycanclaw.netlify.app`，并确认后端容器包含 `LYCAN_WALINE_PUBLIC_URL=https://wreckloud.com`。不要长期关闭 `SECURE_DOMAINS`。

如果 `/waline/api/comment` 返回 `ER_NOT_SUPPORTED_AUTH_MODE`，说明 Waline 能访问 MySQL，但数据库用户仍是 MySQL 8.4 默认认证协议。执行 `deploy/scripts/fix-waline-mysql-auth.sh` 后重建 Waline 容器。

最后手动检查管理端登录、评论、音乐、推荐、阅读量、在线对战房间、Netlify备用站和一次备份恢复演练。
