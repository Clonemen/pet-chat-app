#!/usr/bin/env bash
# ============================================================
#  宠物聊天后端 - 云服务器一键部署（Linux: Ubuntu / Debian / CentOS）
#  作用：装 Node18、拉代码、装依赖、放通防火墙 3000、用 pm2 常驻。
#  用法：
#    bash deploy.sh                 # 自动 git clone 官方仓库并部署
#    REPO_URL=xxx bash deploy.sh    # 用自定义仓库地址（若官方仓库拉取慢）
#    （若已在 backend 目录内，直接部署当前代码）
# ============================================================
set -e
echo "🐾 宠物聊天后端 - 云服务器部署"

# 1) 安装 Node.js 18（若未安装）
if ! command -v node >/dev/null 2>&1; then
  echo "[1/4] 未检测到 Node.js，安装 Node 18 LTS ..."
  if command -v apt-get >/dev/null 2>&1; then
    curl -fsSL https://deb.nodesource.com/setup_18.x | bash -
    apt-get install -y nodejs
  elif command -v yum >/dev/null 2>&1; then
    curl -fsSL https://rpm.nodesource.com/setup_18.x | bash -
    yum install -y nodejs
  else
    echo "无法自动安装 Node，请手动安装 Node 18+ 后重试。"; exit 1
  fi
else
  echo "[1/4] 已安装 Node: $(node -v)"
fi

# 2) 获取代码
if [ -f src/server.js ]; then
  echo "[2/4] 当前目录已是后端代码，直接使用。"
else
  REPO="${REPO_URL:-https://github.com/Clonemen/pet-chat-app.git}"
  echo "[2/4] 克隆仓库: $REPO"
  rm -rf petchat-tmp
  git clone "$REPO" petchat-tmp
  cd petchat-tmp/backend
fi

# 3) 安装依赖
echo "[3/4] 安装依赖 ..."
npm install --omit=dev 2>/dev/null || npm install

# 4) 放通防火墙 3000（本地防火墙；云厂商安全组另需在控制台放行）
echo "[4/4] 放通本地防火墙端口 3000 ..."
if command -v ufw >/dev/null 2>&1; then
  ufw allow 3000/tcp || true
elif command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --permanent --add-port=3000/tcp && firewall-cmd --reload || true
fi

# 5) pm2 常驻
echo "启动服务（pm2 常驻）..."
npm install -g pm2 2>/dev/null || true
pm2 start src/server.js --name petchat
pm2 save
pm2 startup 2>/dev/null || echo "（pm2 开机自启需手动执行其输出的命令，或忽略）"

echo ""
echo "✅ 部署完成！"
echo "   1) 务必在云厂商控制台【安全组】放行 TCP 3000（入站）。"
echo "   2) 手机 App 服务器地址填: http://<云服务器公网IP>:3000"
echo "   3) 查看日志: pm2 logs petchat   重启: pm2 restart petchat"
