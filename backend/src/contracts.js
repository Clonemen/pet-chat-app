// 共享数据契约与校验（后端）。
// 这些结构需与 Android 端 Kotlin data class 一一对应。
// 所有对外 JSON 字段名保持小驼峰，便于两端复用。

// 宠物动作类型：客户端据此切换预制动画
export const ActionType = Object.freeze({
  IDLE: 'IDLE',
  HAPPY: 'HAPPY',
  SURPRISED: 'SURPRISED',
  THINKING: 'THINKING',
  SAD: 'SAD',
  GREETING: 'GREETING',
});
export const ALL_ACTIONS = Object.values(ActionType);

// 宠物性格特质
export const PersonalityTrait = Object.freeze({
  TSUNDERE: 'tsundere', // 傲娇
  GENKI: 'genki',       // 元气
  CALM: 'calm',         // 沉稳
  TSUKKOMI: 'tsukkomi', // 毒舌
  SWEET: 'sweet',       // 软萌
});

// 外观生成风格
export const ImageStyle = Object.freeze({
  CHIBI: 'chibi',
  REALISTIC: 'realistic',
  PIXEL: 'pixel',
  CYBER: 'cyber',
});

// ---- 入参校验：返回 { ok, error } 或 { ok, value } ----
export function validateRegister(body) {
  const username = (body?.username || '').trim();
  const displayName = (body?.displayName || username).trim();
  if (username.length < 3) return { ok: false, error: 'username 至少 3 个字符' };
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return { ok: false, error: 'username 仅允许字母/数字/下划线' };
  const password = body?.password || '';
  if (password.length < 6) return { ok: false, error: 'password 至少 6 位' };
  return { ok: true, value: { username, displayName, password } };
}

export function validateLogin(body) {
  const username = (body?.username || '').trim();
  const password = (body?.password || '');
  if (!username || !password) return { ok: false, error: '缺少 username/password' };
  return { ok: true, value: { username, password } };
}

// 中国大陆手机号
export function validatePhone(phone) {
  const p = (phone || '').trim();
  if (!/^1[3-9]\d{9}$/.test(p)) return { ok: false, error: '手机号格式不正确（应为 11 位大陆手机号）' };
  return { ok: true, value: { phone: p } };
}

// 发送短信验证码
export function validateSendCode(body) {
  const pv = validatePhone(body?.phone);
  if (!pv.ok) return pv;
  return { ok: true, value: { phone: pv.value.phone } };
}

// 手机号注册：phone + code + password（+ 可选 username/displayName）
export function validatePhoneRegister(body) {
  const pv = validatePhone(body?.phone);
  if (!pv.ok) return pv;
  const code = (body?.code || '').trim();
  if (!/^\d{6}$/.test(code)) return { ok: false, error: '验证码为 6 位数字' };
  const password = body?.password || '';
  if (password.length < 6) return { ok: false, error: '密码至少 6 位' };
  const username = (body?.username || '').trim() || pv.value.phone; // 未填则用手机号作登录名
  if (!/^[a-zA-Z0-9_]{3,}$/.test(username)) return { ok: false, error: '用户名需 3 位以上字母/数字/下划线' };
  const displayName = (body?.displayName || '').trim() || pv.value.phone;
  return { ok: true, value: { phone: pv.value.phone, code, password, username, displayName } };
}

// 手机号 + 密码登录
export function validatePhoneLogin(body) {
  const pv = validatePhone(body?.phone);
  if (!pv.ok) return pv;
  const password = body?.password || '';
  if (!password) return { ok: false, error: '缺少 password' };
  return { ok: true, value: { phone: pv.value.phone, password } };
}

export function validatePetCreate(body) {
  const name = (body?.name || '').trim();
  if (name.length < 1 || name.length > 20) return { ok: false, error: '宠物名需 1-20 字' };
  const species = (body?.species || 'cat').trim() || 'cat';
  const personality = (body?.personality || '').trim();
  const trait = (body?.trait && PersonalityTrait[body.trait.toUpperCase()])
    ? body.trait.toLowerCase() : 'sweet';
  const style = (body?.style && ImageStyle[body.style.toUpperCase()])
    ? body.style.toLowerCase() : 'chibi';
  const appearancePrompt = (body?.appearancePrompt || `a ${style} style ${species} named ${name}`).trim();
  return { ok: true, value: { name, species, personality, trait, style, appearancePrompt } };
}

export function validateMessage(body) {
  const to = (body?.to || '').trim();
  const text = (body?.text || '').trim();
  if (!to) return { ok: false, error: '缺少接收者 to' };
  if (!text) return { ok: false, error: '消息内容不能为空' };
  if (text.length > 2000) return { ok: false, error: '消息过长（上限 2000 字）' };
  return { ok: true, value: { to, text } };
}

export function validateImageRequest(body) {
  const prompt = (body?.prompt || '').trim();
  if (!prompt) return { ok: false, error: '缺少 prompt' };
  const style = (body?.style && ImageStyle[body.style.toUpperCase()])
    ? body.style.toLowerCase() : 'chibi';
  return { ok: true, value: { prompt, style } };
}
