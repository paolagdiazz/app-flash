// BlueWay: avisos compartidos entre móviles. Cloudflare Worker con base de datos D1 enlazada como "DB".
//
// Las tablas se crean solas en la primera petición, así que basta con pegar este fichero en el Worker.
//
// Modelo de seguridad:
//  * Solo existen estas cuatro operaciones; la base de datos no es accesible desde fuera.
//  * device_id es un identificador aleatorio de la instalación: no hay cuenta ni datos personales,
//    y nunca se devuelve. Se puede falsificar, así que los límites por dispositivo frenan abusos
//    casuales, no ataques dirigidos. Por eso también hay un límite global de altas.

const HOURS = {
  WORKS: 168, EVENT: 6, TRAFFIC: 2, NOISE_EXTREME: 2, NOISE_HIGH: 2,
  NOISE_MODERATE: 2, NOISE_NORMAL: 2, NOISE_LOW: 2, GENERAL: 3,
};
const HOUR_MS = 3_600_000;
const DUPLICATE_METERS = 120;
const MAX_DESCRIPTION = 120;
const MAX_BODY_BYTES = 2000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const SCHEMA = [
  `CREATE TABLE IF NOT EXISTS reports (
     id TEXT PRIMARY KEY, device_id TEXT NOT NULL, type TEXT NOT NULL,
     lat REAL NOT NULL, lon REAL NOT NULL, description TEXT NOT NULL,
     created_ms INTEGER NOT NULL, expires_ms INTEGER NOT NULL,
     confirmations INTEGER NOT NULL, rejections INTEGER NOT NULL)`,
  `CREATE INDEX IF NOT EXISTS reports_expires ON reports (expires_ms)`,
  `CREATE INDEX IF NOT EXISTS reports_device ON reports (device_id, created_ms)`,
  `CREATE TABLE IF NOT EXISTS votes (
     report_id TEXT NOT NULL, device_id TEXT NOT NULL, positive INTEGER NOT NULL,
     PRIMARY KEY (report_id, device_id))`,
];

class ApiError extends Error {}

let schemaReady = false;

async function ensureSchema(db) {
  if (schemaReady) return;
  await db.batch(SCHEMA.map((sql) => db.prepare(sql)));
  schemaReady = true;
}

function meters(lat1, lon1, lat2, lon2) {
  const kx = 111320 * Math.cos(((lat1 + lat2) / 2) * (Math.PI / 180));
  return Math.hypot((lat1 - lat2) * 110574, (lon1 - lon2) * kx);
}

function requireUuid(value, code) {
  if (typeof value !== "string" || !UUID.test(value)) throw new ApiError(code);
  return value.toLowerCase();
}

async function changes(statement) {
  return (await statement.run()).meta.changes;
}

async function listReports(db, args) {
  const device = requireUuid(args.p_device, "invalid_device");
  const { results } = await db.prepare(
    `SELECT r.id, r.type, r.lat, r.lon, r.description, r.created_ms, r.expires_ms,
            r.confirmations, r.rejections, r.device_id = ?1 AS mine,
            EXISTS (SELECT 1 FROM votes v WHERE v.report_id = r.id AND v.device_id = ?1) AS voted
     FROM reports r WHERE r.expires_ms > ?2 ORDER BY r.created_ms DESC LIMIT 500`,
  ).bind(device, Date.now() - 24 * HOUR_MS).all();
  return results.map((r) => ({ ...r, mine: !!r.mine, voted: !!r.voted }));
}

async function createReport(db, args) {
  const device = requireUuid(args.p_device, "invalid_device");
  const type = args.p_type;
  if (typeof type !== "string" || !Object.hasOwn(HOURS, type)) throw new ApiError("invalid_type");
  if (!Number.isFinite(args.p_lat) || !Number.isFinite(args.p_lon)) throw new ApiError("out_of_area");
  // Ubicación aproximada (unos 11 m), dentro de la zona con datos de la app: Málaga capital.
  const lat = Math.round(args.p_lat * 1e4) / 1e4;
  const lon = Math.round(args.p_lon * 1e4) / 1e4;
  if (lat < 36.685 || lat > 36.755 || lon < -4.515 || lon > -4.345) throw new ApiError("out_of_area");
  const raw = typeof args.p_description === "string" ? args.p_description : "";
  const description = raw.replace(/[\u0000-\u001f\u007f]+/g, " ").trim().slice(0, MAX_DESCRIPTION);

  const now = Date.now();
  const recent = await db.prepare(
    `SELECT COUNT(*) AS n FROM reports WHERE device_id = ? AND created_ms > ?`,
  ).bind(device, now - 10 * 60_000).first();
  if (recent.n >= 5) throw new ApiError("rate_limited");
  const global = await db.prepare(`SELECT COUNT(*) AS n FROM reports WHERE created_ms > ?`).bind(now - 60_000).first();
  if (global.n >= 60) throw new ApiError("busy");

  const { results: sameType } = await db.prepare(
    `SELECT id, device_id, lat, lon FROM reports
     WHERE type = ? AND expires_ms > ? AND NOT (rejections >= 2 AND rejections > confirmations)
     ORDER BY created_ms DESC`,
  ).bind(type, now).all();
  const existing = sameType.find((r) => meters(r.lat, r.lon, lat, lon) <= DUPLICATE_METERS);
  if (existing) {
    // La misma persona no puede reforzar su propio aviso ni hacerlo dos veces.
    if (existing.device_id !== device) {
      const added = await changes(db.prepare(
        `INSERT INTO votes (report_id, device_id, positive) VALUES (?, ?, 1) ON CONFLICT DO NOTHING`,
      ).bind(existing.id, device));
      if (added) await db.prepare(`UPDATE reports SET confirmations = confirmations + 1 WHERE id = ?`).bind(existing.id).run();
    }
    return [{ id: existing.id, merged: true }];
  }

  // Limpieza de avisos caducados hace más de una semana: no se conservan indefinidamente.
  const old = now - 7 * 24 * HOUR_MS;
  const id = crypto.randomUUID();
  await db.batch([
    db.prepare(`DELETE FROM votes WHERE report_id IN (SELECT id FROM reports WHERE expires_ms < ?)`).bind(old),
    db.prepare(`DELETE FROM reports WHERE expires_ms < ?`).bind(old),
    db.prepare(
      `INSERT INTO reports (id, device_id, type, lat, lon, description, created_ms, expires_ms, confirmations, rejections)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 0)`,
    ).bind(id, device, type, lat, lon, description, now, now + HOURS[type] * HOUR_MS),
  ]);
  return [{ id, merged: false }];
}

async function voteReport(db, args) {
  const device = requireUuid(args.p_device, "invalid_device");
  const id = requireUuid(args.p_id, "not_found");
  if (typeof args.p_still_there !== "boolean") throw new ApiError("invalid_vote");
  const now = Date.now();
  const report = await db.prepare(
    `SELECT device_id, type, expires_ms FROM reports WHERE id = ? AND expires_ms > ?`,
  ).bind(id, now).first();
  if (!report) throw new ApiError("not_found");
  if (report.device_id === device) throw new ApiError("own_report");
  const added = await changes(db.prepare(
    `INSERT INTO votes (report_id, device_id, positive) VALUES (?, ?, ?) ON CONFLICT DO NOTHING`,
  ).bind(id, device, args.p_still_there ? 1 : 0));
  if (!added) throw new ApiError("already_voted");
  if (args.p_still_there) {
    // Confirmar alarga media hora la vida del aviso, sin superar la de su tipo.
    const expires = Math.max(report.expires_ms, Math.min(now + HOURS[report.type] * HOUR_MS, report.expires_ms + 30 * 60_000));
    await db.prepare(`UPDATE reports SET confirmations = confirmations + 1, expires_ms = ? WHERE id = ?`).bind(expires, id).run();
  } else {
    await db.prepare(`UPDATE reports SET rejections = rejections + 1 WHERE id = ?`).bind(id).run();
  }
  return null;
}

async function deleteReport(db, args) {
  const device = requireUuid(args.p_device, "invalid_device");
  const id = requireUuid(args.p_id, "not_found");
  const removed = await changes(db.prepare(`DELETE FROM reports WHERE id = ? AND device_id = ?`).bind(id, device));
  if (!removed) throw new ApiError("not_found");
  await db.prepare(`DELETE FROM votes WHERE report_id = ?`).bind(id).run();
  return null;
}

const FUNCTIONS = {
  list_reports: listReports,
  create_report: createReport,
  vote_report: voteReport,
  delete_report: deleteReport,
};

function json(status, body) {
  return new Response(body === null ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "X-Content-Type-Options": "nosniff" },
  });
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    const name = pathname.startsWith("/rpc/") ? pathname.slice(5) : "";
    if (request.method !== "POST" || !Object.hasOwn(FUNCTIONS, name)) return json(404, { message: "not_found" });
    if (!env.DB) return json(500, { message: "db_not_bound" });

    const text = await request.text();
    if (text.length > MAX_BODY_BYTES) return json(413, { message: "too_large" });
    let args;
    try {
      args = JSON.parse(text);
    } catch {
      return json(400, { message: "invalid_json" });
    }
    if (args === null || typeof args !== "object") return json(400, { message: "invalid_json" });

    try {
      await ensureSchema(env.DB);
      const result = await FUNCTIONS[name](env.DB, args);
      return result === null ? json(204, null) : json(200, result);
    } catch (e) {
      if (e instanceof ApiError) return json(400, { message: e.message });
      console.error(e);
      return json(500, { message: "server_error" });
    }
  },
};
