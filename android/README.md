# 宠物聊天 · Android 原生悬浮宠物层

架构中"客户端"的 Android 原生实现，核心目标：**把电子宠物常驻手机桌面，好友发消息时宠物实时做出动作并弹出气泡**。

后端（Node）契约已定义，本模块直接复用，仅做"标签驱动"动画，不实时生成视频。

## 模块映射（与架构/后端一致）

| 架构模块 | 本工程位置 | 说明 |
|---|---|---|
| 桌面层（悬浮窗） | `floating/FloatingPetService.kt` | `WindowManager.TYPE_APPLICATION_OVERLAY` + 前台服务保活 |
| 桌面层（视图） | `floating/FloatingPetContent.kt` | Compose 宠物：气泡/形象/名字，可拖动 |
| 宠物引擎（动作状态机） | `floating/PetMotion.kt` | `ActionType → 动画`，idle 呼吸，其余瞬时播完回 idle |
| 数据契约 | `model/Contracts.kt` | `ActionType / Reaction / PetProfile`，与后端 `contracts.js` 对齐 |
| 会话存储 | `data/SessionStore.kt` | token / 服务器 / 宠物（SharedPreferences） |
| REST | `data/ApiClient.kt` | 登录、拉取我的宠物（OkHttp + kotlinx.serialization） |
| 聊天层网络 | `im/PetWebSocketClient.kt` | 悬浮宠物的 WS：收消息 → 驱动宠物反应 |
| 聊天层网络 | `im/ChatWebSocketClient.kt` | 聊天线程 WS：发 `{type:"message",to,text}`，收 `message`/`message_ack` |
| 入口/登录 | `MainActivity.kt` | 登录/注册 → 进入聊天主页 |
| 聊天主页 | `ui/ChatListActivity.kt` | 会话列表、加好友、桌面宠物开关、设置入口 |
| 单聊界面 | `ui/ChatActivity.kt` | 历史（REST）+ 实时收发（WS），气泡分左右 |
| 设置页 | `ui/SettingsActivity.kt` | 单独修改服务器地址、退出登录 |
| 我的宠物 | `ui/PetProfileActivity.kt` | 创建/管理宠物：填表建宠（服务端文生图）、改名/改性格、重新生成外观 |

## 编译环境（重要）

本项目已在本机验证可编译。**关键点：本机 Android Studio 自带的 JBR 是 JDK 25，而 AGP 8.5 / Kotlin 1.9.24 不支持 JDK 25（会报 `JavaVersion.parse` 失败）。**

已在 `gradle.properties` 中固定：
```properties
org.gradle.java.home=D:/Android/downloads/jdk21/jdk-21.0.12.1+1
```
即本机已下载并解压的 Temurin JDK 21。这样 **Android Studio（用其 JBR 25 启动 Gradle）也会自动用 JDK 21 编译**，开箱即通过。

- 若换机器或想用其它 JDK：修改上面路径，或在 AS 的 `Settings > Build > Gradle > Gradle JDK` 里手动选 JDK 21。
- 命令行编译（可选）：先装好 JDK 21 并设 `JAVA_HOME` 指向它，再 `./gradlew assembleDebug`。
- 依赖（AGP / Kotlin / Compose / OkHttp / Coil 等）首次构建会自动从 Maven 下载，需联网。

## 运行

1. 用 **Android Studio** 打开 `pet-chat-app/android`，直接 `Build > Make Project`（或点 ▶ Run）。无需手动配 JDK——见上方「编译环境」。
2. 先启动后端：
   ```bash
   cd pet-chat-app/backend && npm install && npm start   # http://localhost:3000
   ```
3. 运行 App，在**登录界面的「服务器地址」输入框**里填写后端地址（见下方说明），然后登录/注册。
4. 登录后进入**聊天主页**：先点「🐾 我的宠物 → 管理」创建你的电子宠物（填名字/物种/性格/风格，提交后服务端生成形象）；然后点「🐾 桌面宠物 → 开启」会申请悬浮窗权限，授权后宠物出现在桌面；点「添加好友」输入对方用户名加好友；点会话进入单聊。
5. 在单聊里发消息，对方（已开宠物）桌面宠物会**即时做出动作 + 弹气泡**；本机也能在聊天页实时看到收发。

### 自动登录（记住登录信息）

- 登录/注册成功后，本地会记住 `token`、账号、密码（SharedPreferences）。**第二次启动 App 时不再显示登录页**，而是后台静默校验 token：有效则直接进入聊天主页；token 失效且有记住的账号密码则自动重新登录；都不行才退回登录页并预填账号。
- 在「设置」页点**退出登录**，或**切换服务器地址**，都会清空本地登录信息（含 token），下次需重新登录。

### 本次新增：聊天界面 + 设置页 + 后端接口

- **设置页（SettingsActivity）**：服务器地址不再只能在登录页填。在聊天主页点「设置」可单独修改，保存后若地址变化会清空本地 token（token 与服务器绑定）并退回登录。也支持退出登录。
- **聊天主页（ChatListActivity）**：`GET /api/messages/conversations` 拉会话；「添加好友」先 `GET /api/auth/users/find?username=` 查 id，再 `POST /api/auth/friends` 添加。桌面宠物开关调用 `FloatingPetService.start/stop`，并受 `SYSTEM_ALERT_WINDOW` 权限保护。
- **单聊（ChatActivity）**：进入时 `GET /api/messages?with=<id>` 拉历史；用独立的 `ChatWebSocketClient` 发/收消息（发 `{type:"message",to,text}`，收 `message`/`message_ack`），与悬浮宠物的 WS **解耦为两个消费者**。
- **后端新增**：`GET /api/auth/users/find?username=`（仅好友体系内、排除自己），用于聊天 UI 按用户名加好友。
- **我的宠物（PetProfileActivity）**：补上此前缺失的建宠闭环。无宠物时填表提交 `POST /api/pets`（服务端调用文生图生成外观），返回后存入 `SessionStore`；已有宠物可改名字/性格特质（`PATCH /api/pets/:id`），或一键「重新生成外观」（后端重新文生图）。形象预览复用 Coil 的 `AsyncImage`，兼容 `data:image/svg+xml` 占位与远程图。聊天主页新增「🐾 我的宠物 → 管理」入口。

## 服务器地址怎么设置（重要）

服务器地址**不需要改代码**，在 App 启动后的登录界面顶部「服务器地址」输入框填写即可，填一次会持久化（存在 `SessionStore` 的 `SharedPreferences`，下次自动带上）。不同场景的取值：

| 运行场景 | 服务器地址填什么 | 说明 |
|---|---|---|
| Android 模拟器 → 本机后端 | `http://10.0.2.2:3000` | 模拟器把 `10.0.2.2` 当作宿主机；这是 App 默认值 |
| 真机 → 同一局域网电脑后端 | `http://192.168.x.x:3000` | 填电脑在局域网的实际 IPv4（命令行 `ipconfig` 查），且电脑防火墙放行 3000 端口 |
| 后端已部署到公网/云 | `https://你的域名` 或 `http://公网IP:端口` | 后端需支持 HTTPS 或明文 HTTP，CORS 已默认放行 |

要点：
- 地址**不要带结尾斜杠**（代码会自动 `removeSuffix("/")`）；建议带 `http://` 或 `https://` 协议头。
- 后端端口默认 `3000`，改了的话这里同步改。
- 连不上时界面会 Toast 提示「操作失败：…」，多为地址填错、后端没起、或模拟器/真机网络不通。

## 关键权限与注意事项

- **`SYSTEM_ALERT_WINDOW`**：特殊权限，无法在运行时弹窗直接授予，必须跳转 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 由用户手动开。已在 `MainActivity` 处理。
- **前台服务**：Android 8+ 悬浮窗后台保活依赖前台服务 + 通知；本服务用 `specialUse` 类型（无更贴切标准类型）。
- **国产 ROM**：小米/华为/OPPO 对后台悬浮窗与自启动有限制，需引导用户在系统设置里允许"后台弹出""自启动"。
- **Android 13+ 通知**：如未授予 `POST_NOTIFICATIONS`，前台通知可能不显示，但服务仍运行；正式版应在启动时申请该权限。
- **Compose 在 Service 中**：`ComposeView.setContent` 在 Service 上下文可用（`LaunchedEffect`/`InfiniteTransition` 由视图帧时钟驱动）。若面向极旧 API 出现生命周期相关异常，可额外 `ViewTreeLifecycleOwner.set(view, owner)`。

## 暂未包含（后续阶段）

- 真实文生图/LLM：后端默认 mock（SVG 占位 + 模板文案）；设 `LLM_PROVIDER/IMAGE_PROVIDER=openai` 即接真模型。
- iOS Widget 降级版（P2）。
- 宠物外观在创建后不可改物种/风格（后端约束），仅名字/性格/特质与外观图可改。
