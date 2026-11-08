// 精确清理联调阶段写入的测试账号及其关联数据。
// 默认「预览」不修改文件；加 --apply 才写入，且写入前强制备份 data.json。
// 用法：node cleanup-smoketest.mjs <data.json 路径> [--apply]
//
// 只匹配 username === 'smoketest' 或 phone === '13900000001' 的用户，
// 绝不动其他真实用户；连带清理其 session / 宠物 / 好友关系 / 消息。
import fs from 'node:fs';
import path from 'node:path';

const TARGET_USERNAME = 'smoketest';
const TARGET_PHONE = '13900000001';
const dataFile = process.argv[2] || path.join(process.cwd(), 'data.json');
const apply = process.argv.includes('--apply');

if (!fs.existsSync(dataFile)) {
  console.log('未找到 data.json，无需清理:', dataFile);
  process.exit(0);
}

let db;
try {
  db = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
} catch (e) {
  console.error('data.json 解析失败，已中止（未做任何修改）:', e.message);
  process.exit(1);
}

const asArray = (v) => (Array.isArray(v) ? v : []);
const users = asArray(db.users);
const sessions = asArray(db.sessions);
const pets = asArray(db.pets);
const friends = asArray(db.friends);
const messages = asArray(db.messages);

const ids = new Set(
  users
    .filter(([, u]) => u && (u.username === TARGET_USERNAME || u.phone === TARGET_PHONE))
    .map(([id]) => id)
);

if (ids.size === 0) {
  console.log('✅ 未发现测试账号，无需清理。当前用户数:', users.length);
  process.exit(0);
}

const delSessions = sessions.filter(([, uid]) => ids.has(uid));
const delPets = pets.filter(([, p]) => p && ids.has(p.ownerId));
const delFriendRows = friends.filter(([uid]) => ids.has(uid));
const delFriendRefs = friends
  .filter(([uid]) => !ids.has(uid))
  .reduce((n, [, list]) => n + asArray(list).filter((f) => ids.has(f)).length, 0);
const delMessages = messages.filter(
  (m) => m && (ids.has(m.senderId) || ids.has(m.receiverId) || ids.has(m.from) || ids.has(m.to))
);

console.log('===== 将要清理的测试数据 =====');
console.log('  用户       :', ids.size, '→', [...ids].join(', '));
console.log('  会话 token :', delSessions.length);
console.log('  宠物       :', delPets.length);
console.log('  好友关系行 :', delFriendRows.length, '（其他用户关系中的残留引用:', delFriendRefs, '）');
console.log('  消息       :', delMessages.length);
console.log('  现有用户总数:', users.length, '→ 清理后:', users.length - ids.size);

if (!apply) {
  console.log('\n[预览模式] 未修改任何文件。确认无误后加 --apply 才会真正写入。');
  process.exit(0);
}

const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const backup = `${dataFile}.bak.${stamp}`;
fs.copyFileSync(dataFile, backup);
console.log('\n已备份 →', backup);

const notTarget = (id) => !ids.has(id);
db.users = users.filter(([id]) => notTarget(id));
db.sessions = sessions.filter(([, uid]) => notTarget(uid));
db.pets = pets.filter(([, p]) => !(p && ids.has(p.ownerId)));
db.friends = friends
  .filter(([uid]) => notTarget(uid))
  .map(([uid, list]) => [uid, asArray(list).filter(notTarget)]);
db.messages = messages.filter(
  (m) => !(m && (ids.has(m.senderId) || ids.has(m.receiverId) || ids.has(m.from) || ids.has(m.to)))
);

fs.writeFileSync(dataFile, JSON.stringify(db, null, 2));
console.log('✅ 清理完成。剩余用户:', db.users.length);
console.log('   回滚命令: cp', backup, dataFile, '（需先停服务）');
