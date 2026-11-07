@echo off
REM ============================================================
REM  宠物聊天后端 - 局域网 / 闲置电脑 一键启动（Windows）
REM  用途：把这台电脑当作服务器，手机通过局域网 IP 访问。
REM  默认开发模式（不设 SMS_DEV 即回传验证码到 App 弹 Toast）。
REM  数据保存在 backend/data.json（重启不丢，别手删）。
REM ============================================================
cd /d %~dp0

REM 1) 检测 Node.js
where node >nul 2>nul
if errorlevel 1 (
  echo [错误] 未检测到 Node.js，请先安装 LTS 版本： https://nodejs.org
  pause
  exit /b 1
)

REM 2) 首次运行自动安装依赖
if not exist node_modules (
  echo [初始化] 首次运行，安装依赖（约 1-2 分钟，请耐心等待）...
  call npm install
  if errorlevel 1 (
    echo [错误] 依赖安装失败，请检查网络后重试。
    pause
    exit /b 1
  )
)

REM 3) 启动（前台运行，关闭窗口即停止服务）
echo.
echo 🐾 后端启动中... 启动成功后日志会显示监听端口（默认 3000）。
echo    关闭此窗口会停止服务。
echo    如需 24 小时常驻 / 开机自启，请用 pm2（见下方说明或 README）。
echo.
node src/server.js
pause
