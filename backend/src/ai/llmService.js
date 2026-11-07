// LLM 反应服务（可插拔 provider + 确定性 mock 兜底）。
// 输入：好友消息文本 + 宠物人格 + 发送者名
// 输出：{ action: ActionType, text }，供客户端切换预制动画与气泡。
import { ActionType } from '../contracts.js';

// 性格模板：trait -> 针对"收到好友消息"的口吻生成文案
const TEMPLATES = {
  tsundere: [
    (s) => `哼，又是 ${s} 找你…才、才不是想你了呢。`,
    (s) => `${s} 又来啦？…正好有点想说话而已。`,
  ],
  genki: [
    (s) => `哇！${s} 给你发消息啦，快看快看！`,
    (s) => `叮咚～${s} 来串门咯，好开心！`,
  ],
  calm: [
    (s) => `${s} 发来消息了，慢慢看，不急。`,
    (s) => `有新的消息，来自 ${s}。`,
  ],
  tsukkomi: [
    (s) => `${s} 这消息也太随便了吧，快去回。`,
    (s) => `啧，${s} 又来骚扰你了，回个话呗。`,
  ],
  sweet: [
    (s) => `${s} 找你啦～有什么开心事要分享吗？`,
    (s) => `收到 ${s} 的消息，抱抱你～`,
  ],
};

function pickAction(text) {
  if (/[?？]/.test(text)) return ActionType.THINKING;
  if (/[!！]/.test(text) || /(开心|哈哈|哇|喜欢|爱你)/.test(text)) return ActionType.HAPPY;
  if (/(在吗|在不在|hi|hello|你好)/i.test(text)) return ActionType.GREETING;
  if (/(难过|伤心|哭|生气|烦|累)/.test(text)) return ActionType.SAD;
  return ActionType.SURPRISED;
}

function mockReaction({ messageText, pet, senderName }) {
  const s = senderName || '有人';
  const trait = pet?.trait || 'sweet';
  const list = TEMPLATES[trait] || TEMPLATES.sweet;
  const text = list[Math.floor(Math.random() * list.length)](s);
  return { action: pickAction(messageText), text };
}

export async function generateReaction({ messageText, pet, senderName }) {
  const provider = (process.env.LLM_PROVIDER || 'mock').toLowerCase();
  if (provider === 'openai' && process.env.OPENAI_API_KEY) {
    try {
      const sys =
        `你是用户的桌面电子宠物，性格为「${pet?.trait || 'sweet'}」。` +
        `当好友发来消息时，用宠物口吻说一句话，并选择一个动作。` +
        `只能输出 JSON：{"action":"IDLE|HAPPY|SURPRISED|THINKING|SAD|GREETING","text":"..."}。`;
      const r = await fetch('https://api.openai.com/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${process.env.OPENAI_API_KEY}`,
        },
        body: JSON.stringify({
          model: process.env.LLM_MODEL || 'gpt-4o-mini',
          messages: [
            { role: 'system', content: sys },
            { role: 'user', content: `好友「${senderName || '有人'}」发来消息：${messageText}` },
          ],
          response_format: { type: 'json_object' },
          temperature: 0.8,
        }),
      });
      if (!r.ok) throw new Error('llm api ' + r.status);
      const j = await r.json();
      const content = j?.choices?.[0]?.message?.content;
      const parsed = JSON.parse(content);
      const action = ActionType[parsed.action] ? parsed.action : ActionType.SURPRISED;
      return { action, text: String(parsed.text || '').slice(0, 120) };
    } catch (e) {
      console.warn('[llm] openai 失败，降级 mock:', e.message);
    }
  }
  return mockReaction({ messageText, pet, senderName });
}
