// 一次性脚本：以 visit_tester(布丁) 身份给 usb_pet_demo 发消息，验证"好友来访变身"
const WebSocket = require('ws');
const BASE = 'http://localhost:3000';

async function main() {
  // 1. 登录拿 token
  const res = await fetch(BASE + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'visit_tester', password: 'visit123456' }),
  });
  const data = await res.json();
  if (!res.ok || !data.token) {
    console.error('登录失败:', res.status, JSON.stringify(data));
    process.exit(1);
  }
  console.log('visit_tester 登录成功, userId =', data.user?.id || data.userId);

  // 2. 连 WS
  const ws = new WebSocket('ws://localhost:3000/ws?token=' + data.token);
  const targetId = 'u_4de9fc38bb3548d3'; // usb_pet_demo

  ws.on('open', () => {
    console.log('WS 已连接，发送消息...');
    ws.send(JSON.stringify({
      type: 'message',
      to: targetId,
      text: process.argv[2] || '你好呀！我是布丁🐶，来看看你家胖橘～',
    }));
  });

  ws.on('message', (raw) => {
    const msg = JSON.parse(raw.toString());
    console.log('收到:', JSON.stringify(msg));
    if (msg.type === 'message_ack' || msg.type === 'error') {
      ws.close();
      process.exit(msg.type === 'message_ack' ? 0 : 1);
    }
  });

  ws.on('error', (e) => { console.error('WS 错误:', e.message); process.exit(1); });
  setTimeout(() => { console.error('超时'); process.exit(1); }, 8000);
}

main().catch((e) => { console.error(e); process.exit(1); });
