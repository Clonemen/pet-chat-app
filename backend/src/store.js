// 内存数据存储（含轻量 JSON 持久化）。
// 生产可替换为 PostgreSQL / Supabase；此处用 Map 保证零依赖可运行。
// 云端：若设置 DATABASE_URL，则“内存为主、Postgres 为备份”——启动时从 PG 恢复整库快照，
// 每次持久化同时写入 PG 的 kv 表，从而跨重启不丢数据（解决云端临时文件系统清空 JSON 的问题）。
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { initPostgres, loadSnapshot, saveSnapshot, pgEnabled } from './postgres.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_FILE = path.join(__dirname, '..', 'data.json');

const db = {
  users: new Map(),   // id -> { id, username, displayName, phone, passwordHash }
  sessions: new Map(),// token -> userId
  smsCodes: new Map(),// phone -> { code, expires }  验证码（开发期内存态，不持久化）
  pets: new Map(),    // id -> PetProfile
  friends: new Map(), // userId -> Set(friendId)
  messages: [],       // ChatMessage[]
};

const SMS_TTL_MS = 5 * 60 * 1000; // 验证码有效期 5 分钟

function applySnapshot(raw) {
  if (!raw) return;
  db.users = new Map(raw.users || []);
  db.sessions = new Map(raw.sessions || []);
  db.pets = new Map(raw.pets || []);
  db.friends = new Map((raw.friends || []).map(([k, v]) => [k, new Set(v)]));
  db.messages = raw.messages || [];
  console.log(`[store] 载入数据: 用户 ${db.users.size} · 宠物 ${db.pets.size} · 消息 ${db.messages.length}`);
}

function load() {
  try {
    if (fs.existsSync(DATA_FILE)) {
      applySnapshot(JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')));
    }
  } catch (e) {
    console.warn('[store] 加载数据失败，使用空库：', e.message);
  }
}

load();

// 云端持久化：设置 DATABASE_URL 时，优先从 Postgres 恢复整库快照（内存为主、PG 为备份）。
// 未设置或连接失败则回退到本地 JSON 文件，行为不变。
if (process.env.DATABASE_URL) {
  try {
    const ok = await initPostgres();
    if (ok) {
      const snap = await loadSnapshot();
      if (snap) applySnapshot(snap);
    }
  } catch (e) {
    console.warn('[store] Postgres 恢复失败，继续使用本地文件数据:', e.message);
  }
}

const persistTimer = setInterval(persist, 10000);
persistTimer.unref?.();

function hashPassword(pw) {
  return crypto.createHash('sha256').update(pw + '::pet-salt').digest('hex');
}
function newId(prefix) {
  return prefix + '_' + crypto.randomBytes(8).toString('hex');
}

function persist() {
  const snap = {
    users: [...db.users],
    sessions: [...db.sessions],
    pets: [...db.pets],
    friends: [...db.friends].map(([k, v]) => [k, [...v]]),
    messages: db.messages.slice(-500),
  };
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify(snap, null, 2));
  } catch (e) {
    console.warn('[store] 本地持久化失败:', e.message);
  }
  // 云端：同时把快照写入 Postgres（异步、不阻塞接口；失败仅告警）
  if (pgEnabled()) saveSnapshot(snap).catch((e) => console.warn('[store] PG 持久化失败:', e.message));
}

export const store = {
  // ---------- 用户 ----------
  createUser(username, displayName, password, phone) {
    for (const u of db.users.values()) {
      if (u.username === username) return null;
      if (phone && u.phone === phone) return null; // 手机号唯一
    }
    const id = newId('u');
    const user = { id, username, displayName, phone: phone || null, passwordHash: hashPassword(password) };
    db.users.set(id, user);
    db.friends.set(id, new Set());
    persist();
    return user;
  },
  getUserByUsername(username) {
    for (const u of db.users.values()) if (u.username === username) return u;
    return null;
  },
  getUserByPhone(phone) {
    if (!phone) return null;
    for (const u of db.users.values()) if (u.phone === phone) return u;
    return null;
  },
  getUserById(id) { return db.users.get(id) || null; },
  verifyPassword(user, password) { return user && user.passwordHash === hashPassword(password); },

  // ---------- 短信验证码（开发期 mock） ----------
  // 真实环境请替换为阿里云/腾讯云/Twilio 等短信服务，并删除 devCode 回传。
  sendSmsCode(phone) {
    const code = String(crypto.randomInt(100000, 1000000)); // 6 位
    db.smsCodes.set(phone, { code, expires: Date.now() + SMS_TTL_MS });
    return code;
  },
  verifySmsCode(phone, code) {
    const rec = db.smsCodes.get(phone);
    if (!rec) return false;
    if (rec.expires < Date.now()) { db.smsCodes.delete(phone); return false; }
    if (rec.code !== String(code)) return false;
    db.smsCodes.delete(phone); // 一次性使用
    return true;
  },

  // ---------- 会话 ----------
  createSession(userId) {
    const token = crypto.randomBytes(24).toString('hex');
    db.sessions.set(token, userId);
    persist();
    return token;
  },
  getUserIdByToken(token) { return db.sessions.get(token) || null; },
  destroySession(token) { db.sessions.delete(token); },

  // ---------- 宠物 ----------
  createPet(pet) {
    const id = newId('p');
    const full = { ...pet, id, createdAt: Date.now() };
    db.pets.set(id, full);
    persist();
    return full;
  },
  getPet(id) { return db.pets.get(id) || null; },
  listPetsByOwner(ownerId) { return [...db.pets.values()].filter((p) => p.ownerId === ownerId); },
  updatePet(id, patch) {
    const p = db.pets.get(id);
    if (!p) return null;
    Object.assign(p, patch, { id, ownerId: p.ownerId });
    db.pets.set(id, p);
    persist();
    return p;
  },

  // ---------- 好友 ----------
  addFriend(userId, friendId) {
    if (userId === friendId) return false;
    if (!db.users.has(friendId)) return false;
    db.friends.get(userId)?.add(friendId);
    db.friends.get(friendId)?.add(userId);
    persist();
    return true;
  },
  listFriends(userId) { return [...(db.friends.get(userId) || [])]; },
  areFriends(a, b) { return db.friends.get(a)?.has(b) || false; },

  // ---------- 消息 ----------
  saveMessage(msg) {
    db.messages.push(msg);
    if (db.messages.length > 5000) db.messages.shift();
    persist();
    return msg;
  },
  listMessagesBetween(a, b) {
    return db.messages
      .filter((m) => (m.senderId === a && m.receiverId === b) || (m.senderId === b && m.receiverId === a))
      .sort((x, y) => x.createdAt - y.createdAt);
  },
};
