#!/usr/bin/env bash
# ============================================================
#  把 nohup 临时进程升级为 systemd 常驻服务（开机自启 + 崩溃自动重启）
#  可选在停机窗口内清理联调测试账号：CLEANUP=1 bash systemd-setup.sh
#
#  用法：
#    bash systemd-setup.sh              # 只加固，打印清理预览（不删数据）
#    CLEANUP=1 bash systemd-setup.sh    # 加固 + 备份并清理测试账号
#
#  前提：代码已在 ~/pet-chat-app/backend，且 Node 已安装。
# ============================================================
set -e

APP="${APP:-$HOME/pet-chat-app/backend}"
SERVICE="petchat"
RUN_USER="$(id -un)"
NODE_BIN="$(command -v node)"
DATA="$APP/data.json"
CLEANUP="${CLEANUP:-0}"

if [ ! -f "$APP/src/server.js" ]; then
  echo "❌ 未找到后端代码: $APP（可用 APP=/path/to/backend 指定）"; exit 1
fi
if [ -z "$NODE_BIN" ]; then echo "❌ 未找到 node"; exit 1; fi

# 非 root 时用 sudo
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
  SUDO="sudo"
  echo "== 0/6 检查 sudo 权限 =="
  sudo -n true 2>/dev/null && echo "   sudo 免密可用" || echo "   sudo 可能需要输入密码（按提示输入即可）"
fi

echo "== 1/6 停止现有进程（释放 3000 端口）=="
$SUDO systemctl stop "$SERVICE" 2>/dev/null || true
pkill -f "node src/server.js" 2>/dev/null || true
if [ -f "$HOME/petchat.pid" ]; then
  kill "$(cat "$HOME/petchat.pid")" 2>/dev/null || true
  rm -f "$HOME/petchat.pid"
fi
for i in $(seq 1 20); do
  if ! (ss -ltn 2>/dev/null | grep -q ':3000 '); then break; fi
  sleep 0.5
done
if ss -ltn 2>/dev/null | grep -q ':3000 '; then
  echo "❌ 3000 端口仍被占用，请先手动排查（ss -ltnp | grep 3000）"; exit 1
fi
echo "   ✅ 端口已释放"

echo "== 2/6 备份数据 =="
if [ -f "$DATA" ]; then
  BAK="$DATA.bak.$(date +%Y%m%d-%H%M%S)"
  cp -a "$DATA" "$BAK"
  echo "   ✅ 已备份 → $BAK"
else
  echo "   （暂无 data.json，跳过备份）"
fi

echo "== 3/6 清理测试账号 =="
CLEAN_SCRIPT="$APP/ops/cleanup-smoketest.mjs"
if [ -f "$CLEAN_SCRIPT" ]; then
  if [ "$CLEANUP" = "1" ]; then
    node "$CLEAN_SCRIPT" "$DATA" --apply
  else
    node "$CLEAN_SCRIPT" "$DATA"     # 仅预览，不修改
    echo "   ℹ️  以上为预览，未删除。要真正清理请执行: CLEANUP=1 bash $0"
  fi
else
  echo "   ⚠️ 未找到 ops/cleanup-smoketest.mjs，跳过清理（可稍后单独执行）"
fi

echo "== 4/6 写入 systemd unit =="
UNIT="/etc/systemd/system/$SERVICE.service"
$SUDO tee "$UNIT" > /dev/null <<EOF
[Unit]
Description=PetChat Backend (Node/Express + WebSocket IM)
After=network.target

[Service]
Type=simple
User=$RUN_USER
WorkingDirectory=$APP
Environment=NODE_ENV=production
Environment=PORT=3000
# 正式上线接好短信服务后，改成 SMS_DEV=false：
# Environment=SMS_DEV=false
ExecStart=$NODE_BIN src/server.js
Restart=always
RestartSec=3
StandardOutput=append:$HOME/petchat.log
StandardError=append:$HOME/petchat.log

[Install]
WantedBy=multi-user.target
EOF
echo "   ✅ 已写入 $UNIT"

echo "== 5/6 启用并启动服务 =="
$SUDO systemctl daemon-reload
$SUDO systemctl enable "$SERVICE" >/dev/null 2>&1
$SUDO systemctl restart "$SERVICE"
sleep 2

echo "== 6/6 自检 =="
if systemctl is-active --quiet "$SERVICE"; then
  echo "   ✅ 服务状态: active（开机自启已开启）"
else
  echo "   ❌ 服务未启动，日志："; $SUDO journalctl -u "$SERVICE" -n 30 --no-pager; exit 1
fi
if curl -sS -m 5 http://localhost:3000/health; then
  echo ""
  echo "   ✅ /health 正常"
else
  echo "   ❌ /health 不通"; exit 1
fi

echo ""
echo "==================== 完成 ===================="
echo "  查看状态 : systemctl status $SERVICE"
echo "  查看日志 : journalctl -u $SERVICE -f   或   tail -f $HOME/petchat.log"
echo "  重启服务 : sudo systemctl restart $SERVICE"
echo "  停止服务 : sudo systemctl stop $SERVICE"
echo "  回滚备份 : sudo systemctl stop $SERVICE && cp <备份文件> $DATA && sudo systemctl start $SERVICE"
echo ""
echo "⚠️ 服务器重启后服务会自动拉起；如不再需要裸端口，可在云控制台安全组关闭 3000（配好 Nginx/HTTPS 后）。"
