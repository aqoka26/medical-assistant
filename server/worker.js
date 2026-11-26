// МедАнализ: сервер-посредник для Gemini API (Cloudflare Worker).
// Ключ Gemini хранится только здесь, в секрете GEMINI_API_KEY, и никогда не попадает на телефоны.
//
// Аккаунты: если к воркеру подключена база D1 с именем DB, пользователи входят по логину и паролю,
// и у каждого свой дневной лимит сообщений (переменная DAILY_LIMIT, по умолчанию 30).
// Необязательно: INVITE_CODE — тогда для регистрации нужен этот код.
// Без базы сервер работает как раньше (без входа), чтобы старые версии приложения не сломались.

const GOOGLE = "https://generativelanguage.googleapis.com";
const DEFAULT_DAILY_LIMIT = 30;
const PBKDF2_ITERATIONS = 10000; // keeps hashing inside the free plan's CPU limit

// Only the Gemini calls the app needs are forwarded.
const ALLOWED = [
  { method: "GET", re: /^\/v1beta\/models$/ },
  { method: "POST", re: /^\/v1beta\/models\/[\w.\-]+:streamGenerateContent$/, counted: true },
  { method: "POST", re: /^\/upload\/v1beta\/files$/ },
];

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch (e) {
      return json(500, "Ошибка сервера: " + (e && e.message ? e.message : String(e)));
    }
  },
};

async function handle(request, env) {
  const url = new URL(request.url);

  if (url.pathname === "/" && request.method === "GET") {
    return new Response("MedAnaliz proxy is running" + (env.DB ? " (accounts on)" : ""), { status: 200 });
  }
  if (url.pathname.startsWith("/auth/")) return handleAuth(request, env, url.pathname);

  if (!env.GEMINI_API_KEY) return json(500, "На сервере не задан секрет GEMINI_API_KEY.");

  let user = null;
  if (env.DB) {
    user = await userFromRequest(request, env);
    if (!user) return json(401, "Войдите в аккаунт.", "UNAUTHENTICATED");
    if (user.blocked) return json(403, "Аккаунт заблокирован владельцем приложения.", "BLOCKED");
  } else if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) {
    return json(401, "Неверный токен приложения.");
  }

  // Second step of an upload comes back here as /upload-session/<Google path>?upload_id=...
  const path = url.pathname.startsWith("/upload-session/")
    ? url.pathname.slice("/upload-session".length)
    : url.pathname;
  const rule = ALLOWED.find((r) => r.method === request.method && r.re.test(path));
  if (!rule) return json(404, "Не найдено.");

  if (user && rule.counted) {
    const limit = user.daily_limit || Number(env.DAILY_LIMIT) || DEFAULT_DAILY_LIMIT;
    const used = await usedToday(env, user.id);
    if (used >= limit) {
      return json(429, `Ваш дневной лимит (${limit} сообщений) исчерпан. Он обновится завтра в 03:00 по Москве.`, "USER_DAILY_LIMIT");
    }
  }

  const target = new URL(GOOGLE + path + url.search);
  const headers = new Headers();
  for (const [name, value] of request.headers) {
    const n = name.toLowerCase();
    if (n.startsWith("x-goog-upload") || n === "content-type" || n === "content-length") headers.set(name, value);
  }
  headers.set("x-goog-api-key", env.GEMINI_API_KEY);

  const upstream = await fetch(target, {
    method: request.method,
    headers,
    body: request.method === "GET" ? undefined : request.body,
  });

  if (user && rule.counted && upstream.ok) await countUse(env, user.id);

  // Step one of a file upload returns a Google upload URL; point it back at this proxy.
  const out = new Headers(upstream.headers);
  const uploadUrl = out.get("x-goog-upload-url");
  if (uploadUrl) {
    const u = new URL(uploadUrl);
    out.set("x-goog-upload-url", url.origin + "/upload-session" + u.pathname + u.search);
  }
  return new Response(upstream.body, { status: upstream.status, headers: out });
}

// ---------- accounts ----------

async function handleAuth(request, env, path) {
  if (!env.DB) return json(501, "Аккаунты не включены на сервере (нет базы DB).", "NO_ACCOUNTS");
  await ensureSchema(env);

  if (path === "/auth/me" && request.method === "GET") {
    const user = await userFromRequest(request, env);
    if (!user) return json(401, "Войдите в аккаунт.", "UNAUTHENTICATED");
    return ok({ login: user.login, usedToday: await usedToday(env, user.id), dailyLimit: limitFor(user, env) });
  }
  if (path === "/auth/logout" && request.method === "POST") {
    const token = bearer(request);
    if (token) await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(await sha256(token)).run();
    return ok({ ok: true });
  }

  if (request.method !== "POST" || (path !== "/auth/register" && path !== "/auth/login")) return json(404, "Не найдено.");
  let body;
  try {
    body = await request.json();
  } catch {
    return json(400, "Неверный запрос.");
  }
  const login = String(body.login || "").trim().toLowerCase();
  const password = String(body.password || "");
  if (!/^[a-z0-9_.@\-]{3,40}$/.test(login)) {
    return json(400, "Логин: от 3 до 40 символов, латинские буквы, цифры, точка, _, -, @.");
  }
  if (password.length < 6) return json(400, "Пароль должен быть не короче 6 символов.");

  if (path === "/auth/register") {
    if (env.INVITE_CODE && String(body.invite || "").trim() !== env.INVITE_CODE) {
      return json(403, "Неверный код приглашения.", "BAD_INVITE");
    }
    const exists = await env.DB.prepare("SELECT id FROM users WHERE login = ?").bind(login).first();
    if (exists) return json(409, "Такой логин уже занят.", "LOGIN_TAKEN");
    const salt = randomHex(16);
    const hash = await hashPassword(password, salt);
    await env.DB.prepare("INSERT INTO users (login, pass_hash, salt, created_at) VALUES (?, ?, ?, ?)")
      .bind(login, hash, salt, Date.now()).run();
  }

  const user = await env.DB.prepare("SELECT * FROM users WHERE login = ?").bind(login).first();
  if (!user || (await hashPassword(password, user.salt)) !== user.pass_hash) {
    return json(401, "Неверный логин или пароль.", "BAD_CREDENTIALS");
  }
  if (user.blocked) return json(403, "Аккаунт заблокирован владельцем приложения.", "BLOCKED");

  const token = randomHex(32);
  await env.DB.prepare("INSERT INTO sessions (token_hash, user_id, created_at) VALUES (?, ?, ?)")
    .bind(await sha256(token), user.id, Date.now()).run();
  return ok({ token, login: user.login, dailyLimit: limitFor(user, env), usedToday: await usedToday(env, user.id) });
}

async function ensureSchema(env) {
  await env.DB.batch([
    env.DB.prepare(`CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT, login TEXT UNIQUE NOT NULL, pass_hash TEXT NOT NULL,
      salt TEXT NOT NULL, created_at INTEGER NOT NULL, blocked INTEGER NOT NULL DEFAULT 0, daily_limit INTEGER)`),
    env.DB.prepare(`CREATE TABLE IF NOT EXISTS sessions (
      token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at INTEGER NOT NULL)`),
    env.DB.prepare(`CREATE TABLE IF NOT EXISTS usage (
      user_id INTEGER NOT NULL, day TEXT NOT NULL, count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (user_id, day))`),
  ]);
}

async function userFromRequest(request, env) {
  const token = bearer(request);
  if (!token) return null;
  return env.DB.prepare(
    "SELECT users.* FROM sessions JOIN users ON users.id = sessions.user_id WHERE sessions.token_hash = ?"
  ).bind(await sha256(token)).first();
}

function limitFor(user, env) {
  return user.daily_limit || Number(env.DAILY_LIMIT) || DEFAULT_DAILY_LIMIT;
}

function today() {
  // Day boundary at 00:00 UTC (03:00 Moscow).
  return new Date().toISOString().slice(0, 10);
}

async function usedToday(env, userId) {
  const row = await env.DB.prepare("SELECT count FROM usage WHERE user_id = ? AND day = ?").bind(userId, today()).first();
  return row ? row.count : 0;
}

async function countUse(env, userId) {
  await env.DB.prepare(
    "INSERT INTO usage (user_id, day, count) VALUES (?, ?, 1) ON CONFLICT(user_id, day) DO UPDATE SET count = count + 1"
  ).bind(userId, today()).run();
}

function bearer(request) {
  const h = request.headers.get("authorization") || "";
  return h.startsWith("Bearer ") ? h.slice(7).trim() : null;
}

async function hashPassword(password, saltHex) {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: hexToBytes(saltHex), iterations: PBKDF2_ITERATIONS },
    key,
    256
  );
  return bytesToHex(new Uint8Array(bits));
}

async function sha256(text) {
  return bytesToHex(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text))));
}

function randomHex(n) {
  return bytesToHex(crypto.getRandomValues(new Uint8Array(n)));
}

function bytesToHex(bytes) {
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
  return out;
}

function ok(data) {
  return new Response(JSON.stringify(data), { status: 200, headers: { "content-type": "application/json; charset=utf-8" } });
}

function json(status, message, code) {
  return new Response(JSON.stringify({ error: { code: status, message, status: code || undefined } }), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
