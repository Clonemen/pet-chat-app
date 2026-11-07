// 聊天记录与最近会话 REST 路由（架构中"聊天层"的历史补给）。
// 仅好友之间可拉取，避免越权读取。
import express from 'express';
import { store } from '../store.js';
import { authMiddleware } from '../auth.js';

export const messageRouter = express.Router();

// 与某好友的聊天记录：GET /api/messages?with=<friendId>
messageRouter.get('/', authMiddleware, (req, res) => {
  const withId = (req.query.with || '').trim();
  if (!withId) return res.status(400).json({ error: '缺少 with 参数' });
  if (!store.getUserById(withId)) return res.status(404).json({ error: '对方不存在' });
  if (!store.areFriends(req.user.id, withId)) {
    return res.status(403).json({ error: '对方不是你的好友' });
  }
  const list = store.listMessagesBetween(req.user.id, withId).map((m) => ({
    id: m.id,
    from: m.senderId,
    text: m.text,
    createdAt: m.createdAt,
  }));
  res.json({ messages: list });
});

// 最近会话列表：GET /api/messages/conversations
messageRouter.get('/conversations', authMiddleware, (req, res) => {
  const friends = store.listFriends(req.user.id);
  const convs = friends
    .map((fid) => {
      const msgs = store.listMessagesBetween(req.user.id, fid);
      const last = msgs[msgs.length - 1] || null;
      const u = store.getUserById(fid);
      return {
        friendId: fid,
        friendName: u ? u.displayName : '(已注销)',
        lastMessage: last ? last.text : '',
        lastAt: last ? last.createdAt : 0,
      };
    })
    .sort((a, b) => b.lastAt - a.lastAt);
  res.json({ conversations: convs });
});
