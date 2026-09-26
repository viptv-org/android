#!/usr/bin/env node
// Native Android exercises the same HTTP fixtures as the TV/browser previews.
// All art/media is served locally over TLS; no account or upstream is contacted.
import https from 'node:https';
import { readFileSync, statSync, createReadStream } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.env.ANDROID_FIXTURE_PORT ?? 9443);
const origin = 'https://10.0.2.2:' + port;
const nativeMedia = process.env.ANDROID_FIXTURE_MEDIA;
const nativeDuration = Number(process.env.ANDROID_FIXTURE_DURATION ?? 12);
process.env.PREVIEW_API_ORIGIN = origin;
const { installBackend, referenceDir } = await import('../../tv-web/tests/preview/backend.ts');
const routes = [];
await installBackend({
  on() {},
  async addInitScript() {},
  async route(pattern, handler) { routes.push({ pattern, handler }); },
}, { family: process.argv.includes('--tv') ? 'tv' : 'phone', session: 'profiles', sourcesDone: true, favorites: true, profilePin: process.argv.includes('--parent-pin'), signOutPin: process.argv.includes('--parent-pin'), noCatalogs: process.argv.includes('--empty'), queue: !process.argv.includes('--empty'), searchFail: process.argv.includes('--search-failure'), manyProfiles: process.argv.includes('--many-profiles'), media: { duration: 12, position: 0 } });
let approved = !process.argv.includes('--pairing');
let live = false;
let delayMetadata = 0;
let delaySearchMovies = 0;
const calls = [];
const token = { session_id: 'android-fixture-session', account_id: '7', profile_id: null, access_token: 'fixture-access', refresh_token: 'fixture-refresh', expires_in: 900 };
const json = (response, value, status = 200) => { response.writeHead(status, { 'content-type': 'application/json' }); response.end(JSON.stringify(value)); };
function rewrite(value) {
  if (Array.isArray(value)) return value.map(rewrite);
  if (value && typeof value === 'object') {
    const output = Object.fromEntries(Object.entries(value).map(([key, child]) => [key, rewrite(child)]));
    if (value.avatar_style === 'lorelei' && [47, 48].includes(Number(value.avatar_choice)))
      output.avatar_url = origin + '/assets/avatar-catalog/lorelei-' + value.avatar_choice + '.png';
    return output;
  }
  if (typeof value !== 'string') return value;
  if (value.startsWith('https://art.example/ref/')) return origin + '/fixtures/art/' + value.slice('https://art.example/ref/'.length);
  if (value.startsWith('https://wsrv.nl/')) return rewrite(new URL(value).searchParams.get('url') ?? '');
  return value;
}
const matches = (pattern, url) => typeof pattern === 'function' ? pattern(new URL(url)) : pattern instanceof RegExp ? pattern.test(url) : new RegExp('^' + pattern.split('**').map(part => part.replace(/[.*+?^$()|[\]{}]/g, '\\$&')).join('.*') + '$').test(url);
const server = https.createServer({
  key: readFileSync(resolve(root, 'qualification/fixtures/tls/server.key')),
  cert: readFileSync(resolve(root, 'qualification/fixtures/tls/server.crt')),
}, async (request, response) => {
  try {
    const chunks = []; for await (const chunk of request) chunks.push(chunk);
    const bodyText = Buffer.concat(chunks).toString();
    const body = bodyText ? JSON.parse(bodyText) : {};
    const url = new URL(request.url, origin);
    const path = url.pathname;
    if (path === '/fixtures/native-validation.mp4' && nativeMedia) {
      const size = statSync(nativeMedia).size;
      const range = /bytes=(\d+)-(\d*)/.exec(request.headers.range ?? '');
      const start = range ? Number(range[1]) : 0;
      const end = range?.[2] ? Math.min(Number(range[2]), size - 1) : size - 1;
      if (start >= size || start > end) { response.writeHead(416); return response.end(); }
      response.writeHead(range ? 206 : 200, { 'content-type': 'video/mp4', 'accept-ranges': 'bytes', 'content-length': end - start + 1, ...(range ? { 'content-range': `bytes ${start}-${end}/${size}` } : {}) });
      return createReadStream(nativeMedia, { start, end }).pipe(response);
    }
    if (path === '/__control') {
      if ('approved' in body) approved = body.approved;
      if ('delayMetadata' in body) delayMetadata = body.delayMetadata;
      if ('delaySearchMovies' in body) delaySearchMovies = Math.max(0, Math.min(10000, Number(body.delaySearchMovies)));
      return json(response, { ok: true });
    }
    if (path === '/__requests') return json(response, calls);
    calls.push({ method: request.method, path, at: Date.now(), ...(path === "/api/streams" ? { itemId: body.id, itemType: body.type } : {}), ...(path === "/api/playback" ? { channelId: body.channel_id } : {}) });
    if (path === '/api/auth/device/token') return approved ? json(response, token) : json(response, { error: 'authorization_pending' }, 400);
    if (path === '/api/auth/device/code') return json(response, { device_code: 'fixture-device', user_code: 'AB12CD34', verification_uri: origin + '/device', verification_uri_complete: origin + '/device?code=AB12CD34', expires_in: 600, interval: 1 });
    if (path.endsWith('/continue/next')) {
      const episode = Number(body.episode ?? 1) + 1;
      return json(response, episode <= 10 ? { status: 'next', item: { id: `tt-monster:1:${episode}`, type: 'episode', series_id: 'tt-monster', name: 'Monster: The Jeffrey Dahmer Story', season: 1, episode } } : { status: 'caught_up' });
    }
    if (path === '/api/discover' && url.searchParams.get('search') && url.searchParams.get('type') === 'movie' && delaySearchMovies) await new Promise(resolve => setTimeout(resolve, delaySearchMovies));
    if (path === '/api/streams') live = body.type === 'live';
    if (path.startsWith('/api/meta/') && delayMetadata) await new Promise(resolve => setTimeout(resolve, delayMetadata));
    if (path.startsWith('/fixtures/art/')) {
      const name = path.split('/').pop();
      if (!/^[a-f0-9]{32}\.(jpg|png|webp)$/.test(name)) return json(response, {}, 404);
      response.writeHead(200, { 'content-type': name.endsWith('.png') ? 'image/png' : name.endsWith('.webp') ? 'image/webp' : 'image/jpeg' });
      return response.end(readFileSync(resolve(referenceDir, 'assets', name)));
    }
    const favoritePage = path.endsWith('/favorites/page');
    const dispatchUrl = favoritePage ? url.href.replace('/favorites/page', '/favorites') : url.href;
    const entry = [...routes].reverse().find(route => matches(route.pattern, dispatchUrl));
    if (!entry) return json(response, { error: 'Unknown fixture endpoint' }, 404);
    await entry.handler({
      request: () => ({ url: () => dispatchUrl, method: () => request.method, headers: () => request.headers, postData: () => bodyText, postDataJSON: () => body }),
      abort: () => json(response, { error: 'External access blocked' }, 404),
      async fulfill(result) {
        let output = result.body ?? '';
        if (typeof output === 'string' && (result.contentType?.includes('json') || result.headers?.['content-type']?.includes('json'))) {
          let value = rewrite(JSON.parse(output));
          if (path.startsWith('/api/guide/')) {
            const shift = Math.floor(Date.now() / 1000) - Date.parse('2026-09-23T10:55:00-04:00') / 1000;
            value.programs = (value.programs ?? []).map(program => ({ ...program, start: program.start + shift, end: program.end + shift }));
          }
          if (favoritePage) value = { items: value, total: value.length, offset: 0, next_offset: null };
          if (path === '/api/playback' && request.method === 'POST') value = { ...value, mode: 'direct', live: live || !!body.channel_id, duration: live ? 0 : nativeDuration, position: 0, ...(nativeMedia ? { url: origin + '/fixtures/native-validation.mp4', format: 'file' } : {}) };
          output = JSON.stringify(value);
        }
        response.writeHead(result.status ?? 200, { ...result.headers, ...(result.contentType ? { 'content-type': result.contentType } : {}) });
        response.end(output);
      },
    });
  } catch { if (!response.headersSent) json(response, { error: 'Fixture request failed' }, 500); else response.end(); }
});
server.listen(port, '127.0.0.1', () => console.log('Android HTTPS fixture ready on loopback port ' + port));
