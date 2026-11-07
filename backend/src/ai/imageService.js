// 文生图服务（可插拔 provider + 本地 SVG 占位兜底）。
// 真实接入：设置 env IMAGE_PROVIDER=openai 与 OPENAI_API_KEY。
import { ImageStyle } from '../contracts.js';

const STYLE_BG = {
  chibi: '#ffe3ec',
  realistic: '#dfe7ef',
  pixel: '#1b1f2a',
  cyber: '#0b1020',
};

function escapeXml(s) {
  return String(s).replace(/[<>&'"]/g, (c) => (
    { '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' }[c]
  ));
}

function svgPlaceholder(prompt, style) {
  const bg = STYLE_BG[style] || '#ffe3ec';
  const label = (prompt || 'pet').slice(0, 18);
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256">` +
    `<rect width="256" height="256" rx="24" fill="${bg}"/>` +
    `<text x="128" y="120" font-size="80" text-anchor="middle">🐾</text>` +
    `<text x="128" y="180" font-size="16" fill="#333" text-anchor="middle" font-family="sans-serif">${escapeXml(label)}</text>` +
    `<text x="128" y="204" font-size="12" fill="#888" text-anchor="middle" font-family="sans-serif">[${style}] mock</text>` +
    `</svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}

export async function generateImage({ prompt, style }) {
  const provider = (process.env.IMAGE_PROVIDER || 'mock').toLowerCase();
  if (provider === 'openai' && process.env.OPENAI_API_KEY) {
    try {
      const r = await fetch('https://api.openai.com/v1/images/generations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${process.env.OPENAI_API_KEY}`,
        },
        body: JSON.stringify({
          model: 'gpt-image-1',
          prompt: `${prompt}, ${style} style, transparent background, cute pet avatar`,
          size: '256x256',
          n: 1,
        }),
      });
      if (!r.ok) throw new Error('image api ' + r.status);
      const j = await r.json();
      const b64 = j?.data?.[0]?.b64_json;
      if (b64) return { imageUrl: 'data:image/png;base64,' + b64 };
    } catch (e) {
      console.warn('[image] openai 失败，降级 mock:', e.message);
    }
  }
  return { imageUrl: svgPlaceholder(prompt, style) };
}
