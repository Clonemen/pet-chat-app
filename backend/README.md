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

### 部署后必做

- **App 服务器地址**：把 Android 登录页「高级选项」里的服务器地址改为云地址（如 `https://pet-chat-backend.onrender.com`）；或把 `MainActivity.kt` 的默认 `serverBase` 改成该地址后重新打包，这样无需每次手动填。
- **生产短信**：`SMS_DEV` 在云端已设为 `false`，请先把 `src/store.js` 的 `sendSmsCode` 替换为真实短信服务（阿里云/腾讯云/Twilio 等），并删除 `devCode` 回传，否则注册会发不出验证码。
- **AI provider**：默认 `LLM_PROVIDER=mock` / `IMAGE_PROVIDER=mock`（离线占位）。要真实 AI 反应/文生图，设置 `OPENAI_API_KEY` 并把 provider 改为 `openai`。

### 环境变量总览

| 变量 | 说明 | 本地默认 | 云端建议 |
|---|---|---|---|
| `PORT` | 监听端口 | 3000 | 平台自动注入 |
| `DATABASE_URL` | 设置后启用 Postgres 持久化 | 不设置（用 data.json） | Postgres 插件自动注入 |
| `SMS_DEV` | 开发期验证码回传（true 时方便联调） | true | **false** |
| `LLM_PROVIDER` / `IMAGE_PROVIDER` | mock \| openai | mock | 按需 |
| `OPENAI_API_KEY` | 真实 AI 时必填 | 空 | 你的密钥 |
