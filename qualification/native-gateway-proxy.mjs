#!/usr/bin/env node
// Emulator ingress for the actual cfg(test) backend/gateway stack. This does
// not implement account/catalog/playback responses or alter endpoint policy.
import http from 'node:http';
import https from 'node:https';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const directory = process.env.QUALIFICATION_DIR;
if (!directory) throw Error('Private actual-stack qualification directory required');
const config = JSON.parse(readFileSync(resolve(directory, 'backend.json')));
const endpoint = new URL(config.gateway_endpoint);
const control = new URL(config.gateway_control);
const [backendHost, backendPort] = config.backend_bind.split(':');
const port = Number(process.env.ANDROID_NATIVE_PROXY_PORT ?? 9445);
const origin = `https://10.0.2.2:${port}`;
const tls = resolve(process.env.ANDROID_FIXTURE_TLS_DIR ?? 'qualification/fixtures/tls');
const requests = [];
const summary = () => writeFileSync(resolve(directory, 'native-requests.json'), JSON.stringify(requests), { mode: 0o600 });

https.createServer({ key: readFileSync(resolve(tls, 'server.key')), cert: readFileSync(resolve(tls, 'server.crt')) }, async (request, response) => {
  try {
    const path = new URL(request.url, origin).pathname;
    const api = path.startsWith('/api/');
    const media = path.startsWith(endpoint.pathname + 'media/');
    if (!api && !media) { response.writeHead(404); response.end(); return; }
    const chunks = [];
    let bodySize = 0;
    for await (const chunk of request) {
      chunks.push(chunk);
      bodySize += chunk.length;
      if (bodySize > 2 * 1024 * 1024) throw Error('Native fixture request limit');
    }
    const body = Buffer.concat(chunks);
    const headers = { ...request.headers, host: endpoint.host };
    delete headers['accept-encoding'];
    delete headers['transfer-encoding'];
    delete headers.expect;
    headers['content-length'] = body.length;
    if (headers.origin) headers.origin = endpoint.origin;
    const entry = { kind: media ? 'gateway media' : path === '/api/v2/playback' ? 'playback create'
      : path.startsWith('/api/v2/playback/') && request.method === 'DELETE' ? 'playback release'
        : path.startsWith('/api/v2/playback/') ? 'playback lifecycle' : 'account/catalog/source', method: request.method };
    if (api) entry.api_path = path;
    if (path === '/api/v2/playback' && request.method === 'POST') {
      const value = JSON.parse(body.toString());
      writeFileSync(resolve(directory, 'native-last-request.json'), JSON.stringify(value), { mode: 0o600 });
      entry.position = value.position;
      entry.audio_track = value.audio_track ?? value.audio_track_index;
    }
    requests.push(entry);
    summary();
    const upstream = http.request({
      hostname: api ? backendHost : control.hostname,
      port: api ? backendPort : control.port,
      path: api ? request.url : request.url.slice(endpoint.pathname.length - 1),
      method: request.method, headers,
    }, incoming => {
      entry.status = incoming.statusCode;
      const outputHeaders = { ...incoming.headers };
      if (api && incoming.headers['content-type']?.includes('application/json')) {
        const received = [];
        let size = 0;
        incoming.on('data', chunk => { size += chunk.length; if (size > 2 * 1024 * 1024) incoming.destroy(); else received.push(chunk); });
        incoming.on('end', () => {
          try {
            const value = JSON.parse(Buffer.concat(received).toString());
            if (typeof value.error_code === 'string') entry.error_code = value.error_code;
            if (value.delivery?.url) {
              writeFileSync(resolve(directory, 'native-raw-last-delivery.json'), JSON.stringify(value), { mode: 0o600 });
              const delivered = new URL(value.delivery.url);
              if (delivered.origin !== endpoint.origin || !delivered.pathname.startsWith(endpoint.pathname + 'media/')) throw Error('Unexpected actual gateway delivery origin');
              value.delivery.url = origin + delivered.pathname + delivered.search;
              entry.delivery_kind = value.delivery.kind;
              entry.delivery_mode = value.delivery.mode;
              entry.audio_tracks = value.delivery.audio_tracks?.map(track => ({ input_index: track.input_index, language: track.language, selected: track.selected, selectable: track.selectable }));
              writeFileSync(resolve(directory, 'native-last-delivery.json'), JSON.stringify(value), { mode: 0o600 });
            }
            const output = Buffer.from(JSON.stringify(value));
            delete outputHeaders['transfer-encoding'];
            delete outputHeaders['content-encoding'];
            outputHeaders['content-length'] = output.length;
            response.writeHead(incoming.statusCode, outputHeaders);
            response.end(output);
            summary();
          } catch { entry.ingress_error = true; summary(); response.writeHead(502); response.end(); }
        });
        incoming.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end(); });
      } else {
        response.writeHead(incoming.statusCode, outputHeaders);
        incoming.pipe(response);
        incoming.on('end', summary);
      }
    });
    upstream.setTimeout(15_000, () => upstream.destroy());
    upstream.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end(); });
    upstream.end(body);
  } catch { if (!response.headersSent) response.writeHead(502); response.end(); }
}).listen(port, '127.0.0.1', () => console.log('Actual native HTTPS ingress ready; API and gateway media are forwarded.'));
