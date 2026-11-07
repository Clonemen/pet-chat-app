// 实时 IM（WebSocket）服务。
// 好友单聊：消息落库后投递给接收方，并附带来自 LLM 的"宠物反应"，
// 驱动接收方桌面宠物做出动作 + 气泡。
import { WebSocketServer } from 'ws';
import { store } from './store.js';
import { userIdFromToken } from './auth.js';
import { validateMessage } from './contracts.js';
import { generateReaction } from './ai/llmService.js';

// userId -> Set<WebSocket>
const clients = new Map();

function register(userId, ws) {
  if (!clients.has(userId)) clients.set(userId, new Set());
  clients.get(userId).add(ws);
  ws.userId = userId;
}
function unregister(ws) {
  const set = clients.get(ws.userId);
  if (set) {
    set.delete(ws);
    if (set.size === 0) clients.delete(ws.userId);
  }
}
function sendTo(userId, payload) {
  const set = clients.get(userId);
  if (!set) return false;
  const data = JSON.stringify(payload);
  let delivered = false;
  for (const ws of set) {
    if (ws.readyState === ws.OPEN) { ws.send(data); delivered = true; }
  }
  return delivered;
}
function safeSend(ws, obj) {
  try { if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj)); } catch { /* ignore */ }
}

export function attachIM(httpServer) {
  const wss = new WebSocketServer({ server: httpServer, path: '/ws' });

  wss.on('connection', (ws, req) => {
    let userId = null;
    try {
      const url = new URL(req.url, 'http://localhost');
      userId = userIdFromToken(url.searchParams.get('token'));
    } catch { /* ignore */ }
    if (!userId) { ws.close(4001, 'unauthorized'); return; }

    register(userId, ws);
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });

    ws.on('message', async (raw) => {
      let msg;
      try { msg = JSON.parse(raw.toString()); }
      catch { return safeSend(ws, { type: 'error', error: 'JSON 解析失败' }); }

      if (msg.type === 'message') {
        const v = validateMessage(msg);
        if (!v.ok) return safeSend(ws, { type: 'error', error: v.error });
        const { to, text } = v.value;
        const receiver = store.getUserById(to);
        if (!receiver) return safeSend(ws, { type: 'error', error: '接收者不存在' });
        if (!store.areFriends(userId, to)) return safeSend(ws, { type: 'error', error: '对方不是你的好友' });

        const saved = store.saveMessage({
          id: 'm_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
          conversationId: [userId, to].sort().join(':'),
          senderId: userId,
          receiverId: to,
          text,
          createdAt: Date.now(),
        });

        // 接收方宠物反应（异步，不阻塞消息投递）
        let reaction = null;
        try {
          const pet = store.listPetsByOwner(to)[0] || null;
          const sender = store.getUserById(userId);
          reaction = await generateReaction({ messageText: text, pet, senderName: sender?.displayName });
        } catch (e) {
          console.warn('[im] 反应生成失败:', e.message);
        }

        // 投递给接收方（含宠物反应 + 发送方宠物信息，用于"宠物变身"展示）
        const senderPet = store.listPetsByOwner(userId)[0] || null;
        sendTo(to, {
          type: 'message',
          id: saved.id,
          from: userId,
          fromName: store.getUserById(userId)?.displayName,
          text,
          createdAt: saved.createdAt,
          reaction,
          senderPetName: senderPet?.name || null,
          senderPetImage: senderPet?.imageUrl || null,
          senderPetSpecies: senderPet?.species || null,
        });
        // 回执给发送方
        safeSend(ws, { type: 'message_ack', id: saved.id, to, text, createdAt: saved.createdAt });
      } else if (msg.type === 'ping') {
        safeSend(ws, { type: 'pong', t: msg.t });
      } else {
        safeSend(ws, { type: 'error', error: '未知消息类型' });
      }
    });

    ws.on('close', () => unregister(ws));
    ws.on('error', () => unregister(ws));
  });

  // 心跳清理僵死连接
  const interval = setInterval(() => {
    wss.clients.forEach((ws) => {
      if (ws.isAlive === false) return ws.terminate();
      ws.isAlive = false;
      ws.ping();
    });
  }, 30000);
  interval.unref?.();

  console.log('[im] WebSocket 已挂载: /ws');
}
