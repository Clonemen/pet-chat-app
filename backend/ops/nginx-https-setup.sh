#!/usr/bin/env bash
# ============================================================
#  Nginx 反向代理 + Let's Encrypt 免费 HTTPS（含 WebSocket wss 转发）
#
#  用法：
#    DOMAIN=pet.example.com bash nginx-https-setup.sh
#    bash nginx-https-setup.sh pet.example.com
#
#  ⚠️ 前置条件（缺一不可，否则证书签发会失败）：
#    1) 域名已添加 A 记录指向本机公网 IP（47.107.227.28），并已生效；
#    2) 云厂商控制台【安全组/防火墙】放行 TCP 80 和 443 入站（来源 0.0.0.0/0）；
#    3) 后端已在 127.0.0.1:3000 运行（curl http://localhost:3000/health 应返回 ok）。
#
#  certbot 会自动改写站点配置加上 443 与 HTTP→HTTPS 跳转，并设置自动续期。
# ============================================================
set -e

DOMAIN="${DOMAIN:-$1}"
PUBLIC_IP="${PUBLIC_IP:-47.107.227.28}"
EMAIL="${EMAIL:-}"

if [ -z "$DOMAIN" ]; then
  echo "❌ 请提供域名：DOMAIN=pet.example.com bash $0"; exit 1
fi
if [ "$(id -u)" -ne 0 ]; then SUDO="sudo"; else SUDO=""; fi

echo "== 1/7 检查前置条件 =="
if ! curl -sS -m 5 http://localhost:3000/health >/dev/null; then
  echo "❌ 后端未在 3000 端口响应，请先部署/启动后端"; exit 1
fi
echo "   ✅ 后端 127.0.0.1:3000 正常"

echo "   DNS 解析检查（应指向 $PUBLIC_IP）："
RESOLVED="$(getent ahostsv4 "$DOMAIN" 2>/dev/null | awk '{print $1}' | head -1)"
if [ -z "$RESOLVED" ]; then
  echo "   ⚠️ 本机暂时解析不到 $DOMAIN —— 确认 A 记录已生效后再继续，否则证书会签发失败"
else
  echo "   $DOMAIN → $RESOLVED"
  if [ "$RESOLVED" != "$PUBLIC_IP" ]; then
    echo "   ⚠️ 解析结果与 $PUBLIC_IP 不一致，请核对 DNS"
  fi
fi

echo "== 2/7 安装 nginx 与 certbot =="
if ! command -v nginx >/dev/null 2>&1 || ! command -v certbot >/dev/null 2>&1; then
  $SUDO apt-get update -qq
  $SUDO DEBIAN_FRONTEND=noninteractive apt-get install -y nginx certbot python3-certbot-nginx >/dev/null
fi
echo "   ✅ nginx $(nginx -v 2>&1 | sed 's/.*\///') , certbot $(certbot --version 2>&1 | awk '{print $2}')"

echo "== 3/7 配置 WebSocket Upgrade 映射 =="
# map 必须位于 http 上下文；certbot 只改 server 块，不会动这个文件。
$SUDO tee /etc/nginx/conf.d/petchat-upgrade.conf > /dev/null <<'EOF'
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
EOF

echo "== 4/7 写入站点反向代理（先只监听 80，供证书校验）=="
$SUDO tee "/etc/nginx/sites-available/petchat" > /dev/null <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    # 证书校验目录
    location /.well-known/acme-challenge/ { root /var/www/html; }

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;

        # 关键：让 /ws 的 Upgrade 握手穿透，App 的 wss 才能连通
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection \$connection_upgrade;

        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # WebSocket 长连接，避免 60s 被断开
        proxy_read_timeout 86400s;
        proxy_send_timeout 86400s;
    }
}
EOF
$SUDO ln -sf /etc/nginx/sites-available/petchat /etc/nginx/sites-enabled/petchat
# Ubuntu 默认站点会抢占 80，若存在则移除，避免 server_name 冲突
if [ -L /etc/nginx/sites-enabled/default ]; then $SUDO rm -f /etc/nginx/sites-enabled/default; fi
$SUDO nginx -t
$SUDO systemctl reload nginx
echo "   ✅ 反代配置生效"

echo "== 5/7 申请 HTTPS 证书（certbot 自动配置 443 与跳转）=="
CERTBOT_ARGS="--nginx -d $DOMAIN --non-interactive --agree-tos --redirect"
if [ -n "$EMAIL" ]; then
  CERTBOT_ARGS="$CERTBOT_ARGS -m $EMAIL"
else
  CERTBOT_ARGS="$CERTBOT_ARGS --register-unsafely-without-email"
fi
# shellcheck disable=SC2086
$SUDO certbot $CERTBOT_ARGS

echo "== 6/7 确认自动续期 =="
$SUDO systemctl enable --now certbot.timer >/dev/null 2>&1 || true
$SUDO certbot renew --dry-run 2>&1 | tail -4

echo "== 7/7 自检 =="
echo "--- HTTPS /health ---"
curl -sS -m 10 "https://$DOMAIN/health" || { echo "❌ HTTPS 不通"; exit 1; }
echo ""
echo "--- HTTP 是否自动跳转 ---"
curl -sS -m 10 -o /dev/null -w "   HTTP %{http_code} → %{redirect_url}\n" "http://$DOMAIN/health"

echo ""
echo "==================== 完成 ===================="
echo "  ✅ 正式地址: https://$DOMAIN"
echo "  App「设置 → 服务器地址」填: https://$DOMAIN   （不要带 :3000）"
echo "  WebSocket 会自动变成 wss://$DOMAIN/ws，实时聊天保持可用。"
echo ""
echo "  配置文件 : /etc/nginx/sites-available/petchat"
echo "  重载配置 : sudo nginx -t && sudo systemctl reload nginx"
echo "  证书续期 : sudo certbot renew   （certbot.timer 已自动开启）"
echo ""
echo "⚠️ 后续可选项：确认 App 全部改走 https 域名后，在云控制台安全组【关闭 TCP 3000】入站，"
echo "   后端就只通过 Nginx 暴露，减少裸端口风险。改 App 默认地址需重新打包 APK。"
