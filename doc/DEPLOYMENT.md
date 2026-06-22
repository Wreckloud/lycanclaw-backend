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

## 6. 启动后端

```bash
cd /opt/lycanclaw/backend/deploy
cp .env.example .env
chmod 600 .env
nano .env
```

至少更换所有 `change-me-*` 值、`BACKEND_ADMIN_QQ_WHITELIST`、管理员邮箱和 SMTP 配置。然后：

```bash
cd /opt/lycanclaw/backend/deploy/scripts
bash deploy.sh --build
curl --fail http://127.0.0.1:8080/actuator/health
```

前端尚未第一次发布时，后端会提示共享 JSON 暂缺，但仍可启动。前端 `master` 首次 Actions成功后会自动补齐。

## 7. 前端首次发布

前端开发分支验证完成后合并到 `master` 并推送。GitHub Actions会：

1. 执行类型检查、Lint、主题检查和生产构建。
2. 上传到 `/srv/lycanclaw/frontend/releases/<commit>`。
3. 原子切换 `current` 软链接。
4. 同步 `posts.json` 和 `knowledge-stats.json` 给后端。
5. 只保留最近三个前端版本。

Netlify继续构建 `https://lycanclaw.netlify.app/`，并通过 `VITE_BACKEND_API_BASE` 使用国内后端。

## 8. 导入真实 Waline 数据

服务器数据库首次启动为空。不要迁移本地测试数据库。登录 Waline 后使用其导入功能上传单独保存的真实 JSON，再执行：

```bash
cd /opt/lycanclaw/backend/deploy/scripts
bash enforce-waline-admin.sh
```

确认真实评论、计数和管理员身份后再开放评论入口。

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

## 10. 自动备份与恢复

安装每日 03:30 备份任务：

```bash
sudo cp /opt/lycanclaw/backend/deploy/systemd/lycanclaw-backup.* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lycanclaw-backup.timer
systemctl list-timers lycanclaw-backup.timer
```

备份保留14天，不包含 `.env`。手动备份和恢复：

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
curl https://wreckloud.com/waline/article?path=%2F
curl https://wreckloud.com/actuator/health  # 应为 404，健康端点不对公网开放
docker compose -f /opt/lycanclaw/backend/deploy/docker-compose.yml --env-file /opt/lycanclaw/backend/deploy/.env ps
```

同时检查管理端登录、评论、音乐、推荐、阅读量、Netlify备用站和一次备份恢复演练。
