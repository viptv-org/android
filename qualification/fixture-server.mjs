#!/usr/bin/env node
// Native Android exercises the same HTTP fixtures as the TV/browser previews.
// All art/media is served locally over TLS; no account or upstream is contacted.
import https from 'node:https';
import { readFileSync, statSync, createReadStream } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { boundedCategoryCount, categoryFixturePage } from './category-fixture.mjs';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const port = Number(process.env.ANDROID_FIXTURE_PORT ?? 9443);
const origin = 'https://10.0.2.2:' + port;
const nativeMedia = process.env.ANDROID_FIXTURE_MEDIA;
const nativeDuration = Number(process.env.ANDROID_FIXTURE_DURATION ?? 12);
process.env.PREVIEW_API_ORIGIN = origin;
const { installBackend, referenceDir } = await import(process.env.ANDROID_FIXTURE_TV_WEB
  ? pathToFileURL(resolve(process.env.ANDROID_FIXTURE_TV_WEB, 'tests/preview/backend.ts')).href
  : '../../tv-web/tests/preview/backend.ts');
const routes = [];
const fixturePage = {
  on() {},
  async addInitScript() {},
  async route(pattern, handler) { routes.push({ pattern, handler }); },
};
await installBackend(fixturePage, { family: process.argv.includes('--tv') ? 'tv' : 'phone', session: 'profiles', sourcesDone: true, favorites: true, profilePin: process.argv.includes('--parent-pin'), signOutPin: process.argv.includes('--parent-pin'), noCatalogs: process.argv.includes('--empty'), queue: !process.argv.includes('--empty'), searchFail: process.argv.includes('--search-failure'), manyProfiles: process.argv.includes('--many-profiles'), media: { duration: 12, position: 0 } });
let approved = !process.argv.includes('--pairing');
let live = false;
let delayMetadata = 0;
let delayIdentity = 0;
let delaySearchMovies = 0;
let copyUrl = false;
let delayPlayback = 0;
let failPlayback = false;
let liveCount = 0;
let categoryCount = 0;
const calls = [];
const playbackLeases = new Map();
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
      if ('liveCount' in body) liveCount = Math.max(0, Math.min(1000, Number(body.liveCount) || 0));
      if ('categoryCount' in body) categoryCount = boundedCategoryCount(body.categoryCount);
      if ('copyUrl' in body) copyUrl = !!body.copyUrl;
      if ('delayPlayback' in body) delayPlayback = Math.max(0, Math.min(10000, Number(body.delayPlayback) || 0));
      if ('failPlayback' in body) failPlayback = !!body.failPlayback;
      if ('approved' in body) approved = body.approved;
      if ('delayMetadata' in body) delayMetadata = body.delayMetadata;
      if ('delayIdentity' in body) delayIdentity = Math.max(0, Math.min(10000, Number(body.delayIdentity) || 0));
      if ('delaySearchMovies' in body) delaySearchMovies = Math.max(0, Math.min(10000, Number(body.delaySearchMovies)));
      return json(response, { ok: true });
    }
    if (path === '/__requests') return json(response, calls);
    const pageOffset = /^page_\d+$/.test(url.searchParams.get('cursor') ?? '') ? Number(url.searchParams.get('cursor').slice(5)) : 0;
    calls.push({ method: request.method, path, at: Date.now(), ...(path === '/api/v2/iptv/live/channels' ? { pageOffset } : {}) });
    if ((liveCount || categoryCount) && path === '/api/v2/iptv/live/categories') {
      const page = categoryFixturePage(url, categoryCount);
      return json(response, page.body, page.status);
    }
    if (liveCount && path === '/api/v2/iptv/live/channels') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      const items = Array.from({ length: liveCount }, (_, index) => ({ id: `channel-${index}`, name: `Channel ${index + 1}`, category_id: 'news', category: 'News' }))
        .filter(channel => channel.name.toLowerCase().includes(search));
      const limit = Number(url.searchParams.get('limit') ?? 50);
      return json(response, { catalog_id: 1, generation: 1, items: items.slice(pageOffset, pageOffset + limit),
        next_cursor: pageOffset + limit < items.length ? `page_${pageOffset + limit}` : null,
        previous_cursor: pageOffset > 0 ? `page_${Math.max(0, pageOffset - limit)}` : null });
    }
    if (path === '/api/auth/me' && delayIdentity) await new Promise(resolve => setTimeout(resolve, delayIdentity));
    if (path === '/api/v2/playback' && request.method === 'POST') {
      if (delayPlayback) await new Promise(resolve => setTimeout(resolve, delayPlayback));
      if (failPlayback) return json(response, { error: 'Synthetic copy failure' }, 503);
    }
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
      request: () => ({ url: () => dispatchUrl, method: () => request.method, headers: () => request.headers, postData: () => bodyText, postDataJSON: () => body, frame: () => ({ page: () => fixturePage }) }),
      abort: () => json(response, { error: 'External access blocked' }, 404),
      async fulfill(result) {
        let output = result.body ?? '';
        if (typeof output === 'string' && (result.contentType?.includes('json') || result.headers?.['content-type']?.includes('json'))) {
          let value = rewrite(JSON.parse(output));
          if (path.startsWith('/api/v2/iptv/guide/')) {
            const shift = Math.floor(Date.now() / 1000) - Date.parse('2026-09-23T10:55:00-04:00') / 1000;
            value.programs = (value.programs ?? []).map(program => ({ ...program, start: program.start + shift, end: program.end + shift }));
          }
          if (favoritePage) value = { items: value, total: value.length, offset: 0, next_offset: null };
          if (path === '/api/v2/playback' && request.method === 'POST') {
            // The shared browser preview can still return legacy flat playback
            // fields. Native qualification must use the current v2 lease envelope.
            const delivery = value.delivery ?? value;
            const isLive = String(body.stream_id ?? '').startsWith('live_source_') || delivery.live;
            value = { id: value.id, status: 'ready', expires_at: Math.floor(Date.now() / 1000) + 60,
              renew_after_seconds: 20, delivery: { ...delivery, kind: 'direct', mode: 'direct', format: nativeMedia ? 'original' : delivery.format, headers: {},
              live: isLive, duration: isLive ? 0 : nativeDuration, position: 0,
              ...(nativeMedia ? { url: origin + '/fixtures/native-validation.mp4' } : {}) } };
            if (copyUrl) value.delivery.url = 'https://provider.test/stream/' + encodeURIComponent(body.stream_id) + '.mkv?token=synthetic%2Bvalue';
            playbackLeases.set(value.id, value);
          }
          const heartbeat = /^\/api\/v2\/playback\/([^/]+)\/heartbeat$/.exec(path);
          if (heartbeat && playbackLeases.has(heartbeat[1]))
            value = { ...playbackLeases.get(heartbeat[1]), expires_at: Math.floor(Date.now() / 1000) + 60 };
          output = JSON.stringify(value);
        }
        response.writeHead(result.status ?? 200, { ...result.headers, ...(result.contentType ? { 'content-type': result.contentType } : {}) });
        response.end(output);
      },
    });
  } catch { if (!response.headersSent) json(response, { error: 'Fixture request failed' }, 500); else response.end(); }
});
server.listen(port, '127.0.0.1', () => console.log('Android HTTPS fixture ready on loopback port ' + port));
