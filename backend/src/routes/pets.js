// 宠物档案 REST 路由。创建时调用文生图服务生成外观。
import express from 'express';
import { store } from '../store.js';
import { authMiddleware } from '../auth.js';
import { validatePetCreate } from '../contracts.js';
import { generateImage } from '../ai/imageService.js';

export const petRouter = express.Router();

// 创建宠物：先生成外观图（可插拔 provider），再落库
petRouter.post('/', authMiddleware, async (req, res) => {
  const v = validatePetCreate(req.body);
  if (!v.ok) return res.status(400).json({ error: v.error });
  try {
    const img = await generateImage({ prompt: v.value.appearancePrompt, style: v.value.style });
    const pet = store.createPet({
      ownerId: req.user.id,
      name: v.value.name,
      species: v.value.species,
      personality: v.value.personality,
      trait: v.value.trait,
      style: v.value.style,
      appearancePrompt: v.value.appearancePrompt,
      imageUrl: img.imageUrl,
    });
    res.status(201).json({ pet });
  } catch (e) {
    console.error('[pets] 创建失败:', e);
    res.status(500).json({ error: '创建宠物失败：' + e.message });
  }
});

petRouter.get('/mine', authMiddleware, (req, res) => {
  res.json({ pets: store.listPetsByOwner(req.user.id) });
});

petRouter.get('/:id', authMiddleware, (req, res) => {
  const pet = store.getPet(req.params.id);
  if (!pet) return res.status(404).json({ error: '宠物不存在' });
  if (pet.ownerId !== req.user.id) return res.status(403).json({ error: '无权访问' });
  res.json({ pet });
});

petRouter.patch('/:id', authMiddleware, async (req, res) => {
  const pet = store.getPet(req.params.id);
  if (!pet) return res.status(404).json({ error: '宠物不存在' });
  if (pet.ownerId !== req.user.id) return res.status(403).json({ error: '无权修改' });

  const patch = {};
  if (typeof req.body?.name === 'string') patch.name = req.body.name.slice(0, 20);
  if (typeof req.body?.personality === 'string') patch.personality = req.body.personality;
  if (typeof req.body?.trait === 'string') patch.trait = req.body.trait;

  // 可选：重新生成外观图
  if (req.body?.regenerateImage) {
    try {
      const img = await generateImage({ prompt: pet.appearancePrompt, style: pet.style });
      patch.imageUrl = img.imageUrl;
    } catch (e) {
      console.warn('[pets] 重新生成图失败，保留原图:', e.message);
    }
  }
  const updated = store.updatePet(pet.id, patch);
  res.json({ pet: updated });
});
