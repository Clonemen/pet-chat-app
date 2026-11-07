// 账号与好友关系 REST 路由。
import express from 'express';
import { store } from '../store.js';
import { authMiddleware } from '../auth.js';
import {
  validateRegister, validateLogin,
  validateSendCode, validatePhoneRegister, validatePhoneLogin,
} from '../contracts.js';

export const accountRouter = express.Router();

// 开发期是否回传验证码（便于本地联调；生产务必关闭）
const SMS_DEV = (process.env.SMS_DEV || 'true').toLowerCase() !== 'false';

accountRouter.post('/register', (req, res) => {
  const v = validateRegister(req.body);
  if (!v.ok) return res.status(400).json({ error: v.error });
  if (store.getUserByUsername(v.value.username)) {
    return res.status(409).json({ error: '用户名已存在' });
  }
  const user = store.createUser(v.value.username, v.value.displayName, v.value.password);
  const token = store.createSession(user.id);
  res.status(201).json({ token, user: publicUser(user) });
});

accountRouter.post('/login', (req, res) => {
  const v = validateLogin(req.body);
  if (!v.ok) return res.status(400).json({ error: v.error });
  const user = store.getUserByUsername(v.value.username);
  if (!user || !store.verifyPassword(user, v.value.password)) {
    return res.status(401).json({ error: '用户名或密码错误' });
  }
  const token = store.createSession(user.id);
  res.json({ token, user: publicUser(user) });
});

// ---- 手机号注册 / 登录 ----
// 1) 获取短信验证码
accountRouter.post('/send-code', (req, res) => {
  const v = validateSendCode(req.body);
  if (!v.ok) return res.status(400).json({ error: v.error });
  const { phone } = v.value;
  const code = store.sendSmsCode(phone);
  console.log(`📱 [dev] 向 ${phone} 发送验证码: ${code}（${SMS_DEV ? '已回传客户端' : '仅服务端日志'}）`);
  res.json({ ok: true, devMode: SMS_DEV, ...(SMS_DEV ? { devCode: code } : {}) });
});

// 2) 手机号 + 验证码 注册
accountRouter.post('/register-phone', (req, res) => {
  const v = validatePhoneRegister(req.body);
  if (!v.ok) return res.status(400).json({ error: v.error });
  const { phone, code, password, username, displayName } = v.value;
  if (!store.verifySmsCode(phone, code)) {
    return res.status(400).json({ error: '验证码无效或已过期' });
  }
  if (store.getUserByPhone(phone)) {
    return res.status(409).json({ error: '该手机号已注册' });
  }
  if (store.getUserByUsername(username)) {
    return res.status(409).json({ error: '用户名已存在' });
  }
  const user = store.createUser(username, displayName, password, phone);
  const token = store.createSession(user.id);
  res.status(201).json({ token, user: publicUser(user) });
});

// 3) 手机号 + 密码 登录
accountRouter.post('/login-phone', (req, res) => {
  const v = validatePhoneLogin(req.body);
  if (!v.ok) return res.status(400).json({ error: v.error });
  const user = store.getUserByPhone(v.value.phone);
  if (!user || !store.verifyPassword(user, v.value.password)) {
    return res.status(401).json({ error: '手机号或密码错误' });
  }
  const token = store.createSession(user.id);
  res.json({ token, user: publicUser(user) });
});

accountRouter.post('/logout', authMiddleware, (req, res) => {
  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;
  if (token) store.destroySession(token);
  res.json({ ok: true });
});

accountRouter.get('/me', authMiddleware, (req, res) => {
  res.json({ user: req.user });
});

accountRouter.post('/friends', authMiddleware, (req, res) => {
  const friendId = (req.body?.friendId || '').trim();
  if (!friendId) return res.status(400).json({ error: '缺少 friendId' });
  if (!store.getUserById(friendId)) return res.status(404).json({ error: '好友不存在' });
  const ok = store.addFriend(req.user.id, friendId);
  if (!ok) return res.status(400).json({ error: '无法添加该好友（可能已是好友或无效）' });
  res.status(201).json({ ok: true });
});

// 按用户名查找用户（便于聊天 UI 通过用户名添加好友）
accountRouter.get('/users/find', authMiddleware, (req, res) => {
  const username = (req.query.username || '').trim();
  if (!username) return res.status(400).json({ error: '缺少 username' });
  const u = store.getUserByUsername(username);
  if (!u || u.id === req.user.id) return res.status(404).json({ error: '用户不存在' });
  res.json({ id: u.id, username: u.username, displayName: u.displayName });
});

accountRouter.get('/friends', authMiddleware, (req, res) => {
  const list = store.listFriends(req.user.id)
    .map((id) => store.getUserById(id))
    .filter(Boolean)
    .map(publicUser);
  res.json({ friends: list });
});

function publicUser(u) {
  return { id: u.id, username: u.username, displayName: u.displayName, phone: u.phone || null };
}
