// OPTIONAL Cloudflare Worker: lets the website upload to catbox.moe even if the browser blocks direct uploads (CORS).
// It only forwards POST uploads from the website origins you list, and refuses files over MAX_BYTES.
// Deploy steps are in the README ("If photo and video uploads fail"). Set these Worker variables:
//   ALLOWED_ORIGIN = https://YOURUSERNAME.github.io          (comma-separate several origins)
//   MAX_BYTES      = 100000000                               (optional; keep within your Cloudflare plan's body limit)
export default {
  async fetch(request, env) {
    const origin = request.headers.get('Origin') || '';
    const allowed = (env.ALLOWED_ORIGIN || '').split(',').map((s) => s.trim()).filter(Boolean);
    const ok = allowed.includes(origin);
    const cors = {
      'Access-Control-Allow-Origin': ok ? origin : 'null',
      'Access-Control-Allow-Methods': 'POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Access-Control-Max-Age': '86400',
      Vary: 'Origin',
    };
    if (request.method === 'OPTIONS') return new Response(null, { status: ok ? 204 : 403, headers: cors });
    if (request.method !== 'POST' || !ok) return new Response('Forbidden', { status: 403, headers: cors });

    const length = Number(request.headers.get('Content-Length') || 0);
    const max = Number(env.MAX_BYTES || 100_000_000) + 1_000_000; // + multipart overhead
    if (!length || length > max) return new Response('File too large', { status: 413, headers: cors });

    const upstream = await fetch('https://catbox.moe/user/api.php', {
      method: 'POST',
      headers: { 'Content-Type': request.headers.get('Content-Type') || 'application/octet-stream' },
      body: request.body,
    });
    return new Response(await upstream.text(), { status: upstream.status, headers: { ...cors, 'Content-Type': 'text/plain' } });
  },
};
