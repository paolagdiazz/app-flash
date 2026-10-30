// Ejecuta backend/worker.js en local, sobre SQLite (el motor de D1), para probarlo sin cuenta en la nube.
// Uso: node backend/test/local_server.mjs [puerto]
// Es una pequeña imitación de la API de D1: no sustituye a una prueba final contra Cloudflare.
import { createServer } from "node:http";
import { DatabaseSync } from "node:sqlite";
import worker from "../worker.js";

const sqlite = new DatabaseSync(":memory:");

class Statement {
  constructor(sql, params = []) {
    this.sql = sql;
    this.params = params;
  }
  bind(...params) {
    return new Statement(this.sql, params);
  }
  async all() {
    return { results: sqlite.prepare(this.sql).all(...this.params) };
  }
  async first() {
    return sqlite.prepare(this.sql).get(...this.params) ?? null;
  }
  async run() {
    return { meta: { changes: Number(sqlite.prepare(this.sql).run(...this.params).changes) } };
  }
}

const DB = {
  prepare: (sql) => new Statement(sql),
  batch: async (statements) => {
    sqlite.exec("BEGIN");
    try {
      const out = [];
      for (const s of statements) out.push(await s.run());
      sqlite.exec("COMMIT");
      return out;
    } catch (e) {
      sqlite.exec("ROLLBACK");
      throw e;
    }
  },
};

const port = Number(process.argv[2] ?? 54330);
createServer(async (req, res) => {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  const body = Buffer.concat(chunks);
  const response = await worker.fetch(
    new Request(`http://localhost${req.url}`, { method: req.method, body: req.method === "POST" ? body : undefined }),
    { DB },
  );
  res.writeHead(response.status, Object.fromEntries(response.headers));
  res.end(Buffer.from(await response.arrayBuffer()));
}).listen(port, "127.0.0.1", () => console.log(`Worker local en http://127.0.0.1:${port}`));
