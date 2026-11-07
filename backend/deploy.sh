#!/usr/bin/env bash
# ============================================================
#  宠物聊天后端 - 云服务器一键部署（国内网络优化版）
#  作用：装 Node18+、拉代码、装依赖(国内镜像)、放通防火墙 3000、常驻并自检。
#  用法：
#    bash deploy.sh                 # 自动 git clone 官方仓库并部署
#    REPO_URL=xxx bash deploy.sh    # 用自定义仓库/镜像地址（GitHub 拉不动时）
#    （若已在 backend 目录内，直接部署当前代码）
#  注意：云厂商控制台【安全组】仍需手动放行 TCP 3000（脚本只能改服务器本机防火墙）
# ============================================================
set -e
echo "🐾 宠物聊天后端 - 云服务器部署"

# 0) 非 root 时自动使用 sudo（云服务器常给 admin/ubuntu 普通用户）
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  if sudo -n true 2>/dev/null; then
    SUDO="sudo -n"
  else
    echo "⚠️ 当前非 root 且 sudo 需要密码；若安装步骤失败，请用 sudo -i 切到 root 后重跑。"
    SUDO="sudo"
  fi
fi

# 1) 安装 Node.js >=18（优先系统 apt 源，国内最快；失败再试 NodeSource）
if ! command -v node >/dev/null 2>&1; then
  echo "[1/5] 未检测到 Node.js，开始安装 ..."
  if command -v apt-get >/dev/null 2>&1; then
    $SUDO apt-get update -qq
    $SUDO apt-get install -y nodejs npm || {
      echo "apt 源安装失败，改用 NodeSource ..."
      curl -fsSL https://deb.nodesource.com/setup_18.x | $SUDO -E bash -
      $SUDO apt-get install -y nodejs
    }
  elif command -v yum >/dev/null 2>&1; then
    curl -fsSL https://rpm.nodesource.com/setup_18.x | $SUDO -E bash -
    $SUDO yum install -y nodejs
  else
    echo "无法自动安装 Node，请手动安装 Node 18+ 后重试。"; exit 1
  fi
else
  echo "[1/5] 已安装 Node: $(node -v)"
fi

# 2) 获取代码（GitHub 拉取限时，失败给出 scp 兜底提示）
if [ -f src/server.js ]; then
  echo "[2/5] 当前目录已是后端代码，直接使用。"
  APP="$(pwd)"
else
  REPO="${REPO_URL:-https://github.com/Clonemen/pet-chat-app.git}"
  echo "[2/5] 克隆仓库: $REPO（限时 90 秒）"
  if ! timeout 90 git clone --depth 1 "$REPO" "$HOME/pet-chat-app"; then
    echo "❌ 代码拉取失败（国内服务器访问 GitHub 常见）。"
    echo "   兜底：在本机把 backend 目录传上来，然后进入该目录重跑本脚本："
    echo "   scp -r ./backend <用户>@<公网IP>:~/pet-chat-app/"
    exit 1
  fi
  APP="$HOME/pet-chat-app/backend"
fi
cd "$APP"

# 3) 安装依赖（国内 npm 镜像，避免 registry.npmjs.org 超时）
echo "[3/5] 安装依赖（国内镜像加速）..."
npm install --omit=dev --no-audit --no-fund \
  --registry=https://registry.npmmirror.com 2>/dev/null \
  || npm install --omit=dev --no-audit --no-fund

# 4) 放通服务器本机防火墙 3000（云厂商安全组另需在控制台放行）
echo "[4/5] 放通本机防火墙端口 3000 ..."
if command -v ufw >/dev/null 2>&1; then
  $SUDO ufw allow 3000/tcp >/dev/null 2>&1 || true
elif command -v firewall-cmd >/dev/null 2>&1; then
  $SUDO firewall-cmd --permanent --add-port=3000/tcp >/dev/null 2>&1 || true
  $SUDO firewall-cmd --reload >/dev/null 2>&1 || true
fi

# 5) 常驻启动：优先 pm2；装不上则 nohup 兜底，确保服务先跑起来
echo "[5/5] 启动服务 ..."
pkill -f "node src/server.js" 2>/dev/null || true
if npm install -g pm2 >/dev/null 2>&1 && command -v pm2 >/dev/null 2>&1; then
  pm2 start src/server.js --name petchat
  pm2 save >/dev/null 2>&1 || true
  pm2 startup >/dev/null 2>&1 || true
  echo "   已用 pm2 常驻（日志: pm2 logs petchat）"
else
  nohup node src/server.js > "$HOME/petchat.log" 2>&1 &
  echo $! > "$HOME/petchat.pid"
  echo "   pm2 不可用，已用 nohup 启动（日志: $HOME/petchat.log）"
fi

# 6) 本机自检
sleep 3
echo ""
echo "===== 本机自检 /health ====="
if curl -sS -m 5 http://localhost:3000/health; then
  echo ""
  echo "✅ 后端已在本机 3000 端口正常响应。"
else
  echo "❌ 本机 health 仍不通，查看日志："
  tail -20 "$HOME/petchat.log" 2>/dev/null || true
fi

echo ""
echo "⚠️ 最后一步（必须）：到云厂商控制台【安全组/防火墙】放行 TCP 3000 入站，来源 0.0.0.0/0。"
echo "   完成后手机 App 填：http://<公网IP>:3000"
