// 服务装配：Express REST + WebSocket IM，统一错误处理。
import express from 'express';
import http from 'node:http';
import { accountRouter } from './routes/account.js';
import { petRouter } from './routes/pets.js';
import { messageRouter } from './routes/messages.js';
import { attachIM } from './imService.js';

const app = express();
app.use(express.json());

app.get('/health', (req, res) => res.json({ ok: true, ts: Date.now() }));

app.use('/api/auth', accountRouter);
app.use('/api/pets', petRouter);
app.use('/api/messages', messageRouter);

// 404
app.use((req, res) => res.status(404).json({ error: '接口不存在: ' + req.path }));

// 全局错误处理（放在最后）
// eslint-disable-next-line no-unused-vars
app.use((err, req, res, next) => {
  console.error('[server] 未捕获错误:', err);
  res.status(500).json({ error: '服务器内部错误' });
});

const PORT = process.env.PORT || 3000;
const server = http.createServer(app);
attachIM(server);

server.listen(PORT, () => {
  console.log(`🐾 宠物聊天后端已启动: http://localhost:${PORT}`);
  console.log('   AI provider ->', 'LLM=' + (process.env.LLM_PROVIDER || 'mock'), 'IMAGE=' + (process.env.IMAGE_PROVIDER || 'mock'));
});
