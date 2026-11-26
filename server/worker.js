// МедАнализ: сервер-посредник для Gemini API (Cloudflare Worker).
// Ключ Gemini хранится только здесь, в секрете GEMINI_API_KEY, и никогда не попадает на телефоны.
// Приложение подписывает запросы токеном APP_TOKEN, чтобы чужие программы не тратили ваши лимиты.

const GOOGLE = "https://generativelanguage.googleapis.com";

// Only the calls the app needs are forwarded.
const ALLOWED = [
  { method: "GET", re: /^\/v1beta\/models$/ },
  { method: "POST", re: /^\/v1beta\/models\/[\w.\-]+:streamGenerateContent$/ },
  { method: "POST", re: /^\/upload\/v1beta\/files$/ },
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/" && request.method === "GET") {
      return new Response("MedAnaliz proxy is running", { status: 200 });
    }
    if (!env.GEMINI_API_KEY) {
      return json(500, "На сервере не задан секрет GEMINI_API_KEY.");
    }
    if (env.APP_TOKEN && request.headers.get("x-app-token") !== env.APP_TOKEN) {
      return json(401, "Неверный токен приложения.");
    }
    // Second step of an upload comes back here as /upload-session/<Google path>?upload_id=...
    const path = url.pathname.startsWith("/upload-session/")
      ? url.pathname.slice("/upload-session".length)
      : url.pathname;
    const allowed = ALLOWED.some((r) => r.method === request.method && r.re.test(path));
    if (!allowed) return json(404, "Не найдено.");

    const target = new URL(GOOGLE + path + url.search);
    const headers = new Headers();
    for (const [name, value] of request.headers) {
      const n = name.toLowerCase();
      if (n.startsWith("x-goog-upload") || n === "content-type" || n === "content-length") {
        headers.set(name, value);
      }
    }
    headers.set("x-goog-api-key", env.GEMINI_API_KEY);

    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "GET" ? undefined : request.body,
    });

    // Step one of a file upload returns a Google upload URL; point it back at this proxy.
    const out = new Headers(upstream.headers);
    const uploadUrl = out.get("x-goog-upload-url");
    if (uploadUrl) {
      const u = new URL(uploadUrl);
      out.set("x-goog-upload-url", url.origin + "/upload-session" + u.pathname + u.search);
    }
    return new Response(upstream.body, { status: upstream.status, headers: out });
  },
};

function json(status, message) {
  return new Response(JSON.stringify({ error: { code: status, message } }), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
