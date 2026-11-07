// 简单 token 鉴权中间件 + WS 鉴权辅助。
// 生产建议替换为 JWT / Supabase Auth；此处用内存会话令牌，零依赖可运行。
import { store } from './store.js';

export function authMiddleware(req, res, next) {
  try {
    const header = req.headers['authorization'] || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : null;
    if (!token) return res.status(401).json({ error: '未提供认证令牌' });
    const userId = store.getUserIdByToken(token);
    if (!userId) return res.status(401).json({ error: '令牌无效或已过期' });
    const user = store.getUserById(userId);
    if (!user) return res.status(401).json({ error: '用户不存在' });
    req.user = { id: user.id, username: user.username, displayName: user.displayName, phone: user.phone || null };
    next();
  } catch (e) {
    console.error('[auth] 异常:', e);
    res.status(500).json({ error: '鉴权异常' });
  }
}

// 供 WebSocket 升级握手使用
export function userIdFromToken(token) {
  if (!token) return null;
  return store.getUserIdByToken(token) || null;
}
