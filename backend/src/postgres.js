// 可选的 Postgres 持久化层（仅当设置 DATABASE_URL 时启用）。
//
// 设计：内存为主、Postgres 为备份。
//   - 运行时仍使用 store.js 里的内存 Map，所有接口保持同步、零侵入；
//   - 每次 persist() 把整库快照（JSON）写入 PG 的 kv 表（key='state'）；
//   - 启动时从 PG 恢复快照，从而跨进程重启不丢数据（解决云端临时文件系统清空 JSON 的问题）。
//
// 为什么不直接用关系表：可复用现有「整库快照」模型，几乎不改动业务代码与接口；
// 对于单实例的小规模应用足够可靠。若未来需要多实例/复杂查询，再迁移到正经的关系表。

let pool = null;
let enabled = false;

export async function initPostgres() {
  const url = process.env.DATABASE_URL;
  if (!url) return false;
  try {
    const pg = await import('pg'); // 动态导入：本地未设置 DATABASE_URL 时不依赖 pg
    const useSsl = /(^postgres:|sslmode=require)/i.test(url);
    pool = new pg.Pool({
      connectionString: url,
      ssl: useSsl ? { rejectUnauthorized: false } : false,
    });
    const client = await pool.connect();
    await client.query(
      `CREATE TABLE IF NOT EXISTS kv (
         key        TEXT PRIMARY KEY,
         value      JSONB NOT NULL,
         updated_at BIGINT NOT NULL
       )`
    );
    client.release();
    enabled = true;
    console.log('🐘 [store] 已连接 Postgres，启用云端持久化（内存为主，PG 为备份）');
    return true;
  } catch (e) {
    console.warn('[store] Postgres 连接失败，回退到本地 JSON 文件持久化:', e.message);
    pool = null;
    enabled = false;
    return false;
  }
}

export async function loadSnapshot() {
  if (!enabled || !pool) return null;
  try {
    const r = await pool.query(`SELECT value FROM kv WHERE key = 'state'`);
    if (r.rowCount === 0) return null;
    return r.rows[0].value; // jsonb 列，pg 已解析为 JS 对象
  } catch (e) {
    console.warn('[store] 读取 Postgres 快照失败:', e.message);
    return null;
  }
}

export async function saveSnapshot(snap) {
  if (!enabled || !pool) return;
  try {
    await pool.query(
      `INSERT INTO kv (key, value, updated_at)
       VALUES ('state', $1::jsonb, $2)
       ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at`,
      [JSON.stringify(snap), Date.now()]
    );
  } catch (e) {
    console.warn('[store] 写入 Postgres 快照失败:', e.message);
  }
}

export function pgEnabled() {
  return enabled;
}
