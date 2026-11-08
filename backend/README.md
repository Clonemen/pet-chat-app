# 宠物聊天 App · 云端后端

基于架构设计的后端实现：账号/宠物档案、实时 IM（WebSocket）、LLM 宠物反应、文生图代理。
零外部依赖即可运行（内存存储 + 本地 SVG 占位），可一键切换真实 AI provider。

## 运行

```bash
cd backend
npm install
npm start          # 默认 http://localhost:3000
```

可选环境变量（不设置则用本地 mock，保证可运行）：

```bash
LLM_PROVIDER=openai   OPENAI_API_KEY=sk-xxx   LLM_MODEL=gpt-4o-mini
IMAGE_PROVIDER=openai OPENAI_API_KEY=sk-xxx
PORT=3000
SMS_DEV=false         # 设为 false 关闭开发期验证码回传（默认 true，仅本地联调用）
```

## 接口契约

### 账号 `/api/auth`
| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | `/register` | `{username,displayName,password}` → `{token,user}` | 否 |
| POST | `/login` | `{username,password}` → `{token,user}` | 否 |
| POST | `/send-code` | `{phone}` 发送短信验证码 → `{ok,devMode,devCode?}` | 否 |
| POST | `/register-phone` | `{phone,code,password,username?,displayName?}` → `{token,user}` | 否 |
| POST | `/login-phone` | `{phone,password}` → `{token,user}` | 否 |
| POST | `/logout` | 销毁会话 | 是 |
| GET  | `/me` | 当前用户（含 phone） | 是 |
| POST | `/friends` | `{friendId}` 加好友 | 是 |
| GET  | `/friends` | 好友列表 | 是 |

### 手机号注册 / 登录说明
- 流程：先 `POST /send-code` 拿到验证码（开发期 `SMS_DEV=true` 时响应里回传 `devCode`，并打印到服务端控制台，便于本地联调；生产务必设为 `SMS_DEV=false` 并替换 `store.sendSmsCode` 为真实短信服务，如阿里云/腾讯云/Twilio）。
- `POST /register-phone` 校验验证码（一次性、5 分钟有效）、手机号唯一、用户名唯一后创建账号；用户名/昵称留空时服务端用手机号兜底。
- 用户表新增 `phone` 字段，随 `/me` 返回；好友列表与扫码查找出于隐私不返回他人手机号。
- Android 端登录页新增「使用手机号」开关：注册时填手机号→获取验证码→填写→设密码即可；登录时填手机号+密码。

### 宠物 `/api/pets`
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/` | 创建宠物（调用文生图），body: `{name,species,personality,trait,style}` → `{pet}` |
| GET  | `/mine` | 我的宠物列表 |
| GET  | `/:id` | 宠物详情 |
| PATCH| `/:id` | 改名/改性格/重生成图 `{name?,personality?,trait?,regenerateImage?}` |

### 实时 IM `/ws?token=...`（WebSocket）
客户端发送：
```json
{ "type": "message", "to": "<userId>", "text": "你好" }
```
服务端投递给接收方（含宠物反应）：
```json
{ "type": "message", "id": "...", "from": "...", "fromName": "...",
  "text": "你好", "createdAt": 123, "reaction": { "action": "HAPPY", "text": "哇！有人给你发消息啦" } }
```
发送方收到回执：
```json
{ "type": "message_ack", "id": "...", "to": "...", "text": "...", "createdAt": 123 }
```

## 数据契约（与 Android 对齐）
- `ActionType`: `IDLE|HAPPY|SURPRISED|THINKING|SAD|GREETING`
- `PersonalityTrait`: `tsundere|genki|calm|tsukkomi|sweet`
- `ImageStyle`: `chibi|realistic|pixel|cyber`
- `PetProfile`: `{id,ownerId,name,species,personality,trait,style,appearancePrompt,imageUrl,createdAt}`
- `Reaction`: `{action,text}`

## 目录
```
backend/
├── package.json
├── data.json            # 运行时自动生成（持久化）
└── src/
    ├── server.js        # 装配
    ├── contracts.js     # 共享契约 + 校验
    ├── store.js         # 内存存储
    ├── auth.js          # 鉴权
    ├── imService.js     # WebSocket IM
    ├── ai/
    │   ├── llmService.js   # 反应生成
    │   └── imageService.js # 文生图
└── routes/
    ├── account.js
    └── pets.js
```

## 云端部署（推荐 · 7×24 稳定）

把后端部署到云平台后，服务不再依赖本机，可长期稳定运行；手机 App 直接连云地址即可，不受本地电脑开关机/网络影响。

> 关键：云端容器的临时文件系统会在重启后被清空，**不能靠 `data.json` 持久化**。本后端已内置可选的 Postgres 持久化——只要设置 `DATABASE_URL`（平台 Postgres 插件会自动注入），就会“内存为主、Postgres 为备份”：启动从 PG 恢复整库快照，每次写入同时落 PG，用户/宠物/消息跨重启不丢。本地不设置 `DATABASE_URL` 时仍用 `data.json`，行为不变。

### 方式一：Render（最简单，自动建库）

1. 把 `pet-chat-app` 作为 Git 仓库推到 GitHub。
2. Render 控制台 → **New** → **Blueprint** → 关联该仓库（根目录需包含 `render.yaml`）。
3. `render.yaml` 已声明：一个 `free` 档 Web 服务（构建 `npm install`、启动 `npm start`、健康检查 `/health`）+ 一个 `free` 档 Postgres（`DATABASE_URL` 自动注入，`SMS_DEV=false`）。
4. 部署完成后得到 https 地址（如 `https://pet-chat-backend.onrender.com`）。

### 方式二：Railway

1. 安装并登录 Railway CLI：`npm i -g @railway/cli && railway login`。
2. `cd backend && railway init && railway link`（或网页新建项目）。
3. 在 Railway 项目里 **Add Plugin → Postgres**（自动注入 `DATABASE_URL`）。
4. `railway up` 部署；`railway.json` 已配置构建/启动/健康检查。
5. 部署完成后在 Railway 控制台拿到域名（如 `https://pet-chat-backend.up.railway.app`）。

### 方式三：国内云服务器（阿里云 / 腾讯云轻量应用服务器）

适合国内网络、追求 24h 稳定在线、且希望数据本地可控的场景。云服务器（VPS，非临时容器）磁盘是持久的，**直接用 `data.json` 即可持久化，无需 Postgres**。

1. 购买一台轻量应用服务器（Ubuntu / Debian 镜像，1 核 1G 起步够用），记下**公网 IP**。
2. 在云厂商控制台【安全组 / 防火墙】放行 **TCP 3000 入站**（若后续用 Nginx 反代 + 域名，则放行 80/443）。
3. SSH 登录服务器，执行一键部署脚本（自动装 Node18、拉代码、装依赖、放通本地防火墙、用 pm2 常驻）：
   ```bash
   curl -fsSL https://raw.githubusercontent.com/Clonemen/pet-chat-app/master/backend/deploy.sh | bash
   # 或 git clone 后手动： bash backend/deploy.sh
   ```
   若 GitHub 拉取慢，可用 `REPO_URL=你的镜像地址 bash deploy.sh`。
4. 看到 `🐾 宠物聊天后端已启动` 即成功。日志：`pm2 logs petchat`，重启：`pm2 restart petchat`。
5. 手机 App 服务器地址填 `http://<公网IP>:3000`（http 明文已获 Android 放行）。

> 数据：VPS 磁盘持久，用户/宠物/消息存 `backend/data.json`，重启不丢。若日后需要多实例或自动备份，再按上文启用 Postgres（设置 `DATABASE_URL`）。
> 进阶（可选）：用 Nginx 反代 + 域名 + 免费证书，把 `http://IP:3000` 升级为 `https://你的域名`，更安全且规避部分网络对裸 IP:端口 的限制。

### 部署后必做

- **App 服务器地址**：把 Android 登录页「高级选项」里的服务器地址改为云地址（如 `https://pet-chat-backend.onrender.com`）；或把 `MainActivity.kt` 的默认 `serverBase` 改成该地址后重新打包，这样无需每次手动填。
- **生产短信**：`SMS_DEV` 代码默认 `true`（验证码回传客户端，仅适合联调）。正式上线前先把 `src/store.js` 的 `sendSmsCode` 换成真实短信服务（阿里云/腾讯云/Twilio 等），再设 `SMS_DEV=false` 并去掉 `devCode` 回传，否则任何人都能看到验证码。
- **AI provider**：默认 `LLM_PROVIDER=mock` / `IMAGE_PROVIDER=mock`（离线占位）。要真实 AI 反应/文生图，设置 `OPENAI_API_KEY` 并把 provider 改为 `openai`。

### 环境变量总览

| 变量 | 说明 | 本地默认 | 云端建议 |
|---|---|---|---|
| `PORT` | 监听端口 | 3000 | 平台自动注入 |
| `DATABASE_URL` | 设置后启用 Postgres 持久化 | 不设置（用 data.json） | Postgres 插件自动注入 |
| `SMS_DEV` | 开发期验证码回传（true 时方便联调） | true | **false** |
| `LLM_PROVIDER` / `IMAGE_PROVIDER` | mock \| openai | mock | 按需 |
| `OPENAI_API_KEY` | 真实 AI 时必填 | 空 | 你的密钥 |
| `NODE_ENV` | 运行环境标记 | 空 | production |

## 运维收尾（VPS 部署后）

`deploy.sh` 为快速验证会用 `nohup` 启动，SSH 断开或服务器重启后不会自动拉起。生产使用建议依次执行以下三项。

### 1. 常驻加固（systemd：开机自启 + 崩溃自动重启）

```bash
cd ~/pet-chat-app && git pull
bash backend/ops/systemd-setup.sh              # 加固 + 打印测试账号清理预览（不删）
CLEANUP=1 bash backend/ops/systemd-setup.sh   # 确认预览后再真正清理
```

脚本会先停服释放 3000 端口、备份 `data.json`，再写入 `petchat.service` 并自检 `/health`。

```bash
systemctl status petchat          # 状态
journalctl -u petchat -f          # 日志
sudo systemctl restart petchat    # 重启
```

> 清理脚本默认**只预览**；`--apply` 才写入，且写入前强制备份。只按 `username=smoketest` 或 `phone=13900000001` 精确匹配，连带清掉其 session / 宠物 / 好友关系 / 消息，不会触碰其他真实用户。

### 2. Nginx + 域名 + HTTPS（可选）

```bash
DOMAIN=pet.example.com bash backend/ops/nginx-https-setup.sh
```

**前置条件**：域名 A 记录已指向公网 IP 且生效；云控制台安全组放行 **TCP 80 / 443**；后端在 `127.0.0.1:3000` 正常。脚本会装 nginx + certbot，配置反代并正确转发 WebSocket Upgrade（`/ws` 的 `wss` 握手），申请证书并开启自动续期。

配好后 App「设置 → 服务器地址」填 `https://你的域名`（**不要带 `:3000`**）。App 内部用 `serverBase.replaceFirst("^http", "ws")` 推导 WebSocket 地址，`https` 会自动变成 `wss`，实时聊天不受影响，Android 无需改代码。

确认全部流量走 HTTPS 后，可在安全组关闭 TCP 3000 入站，减少裸端口暴露。

### 3. 上线前清单

- [ ] 接真实短信服务，设 `SMS_DEV=false`，去掉 `devCode` 回传
- [ ] 清理联调测试账号
- [ ] systemd 常驻已启用（`systemctl is-enabled petchat` 应返回 `enabled`）
- [ ] 定期备份 `backend/data.json`
- [ ] 如需真实 AI 反应 / 文生图，配置 `OPENAI_API_KEY` 与 provider
