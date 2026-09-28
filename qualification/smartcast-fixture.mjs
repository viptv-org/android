// Local synthetic TV, never a real pairing credential. Run after start-fixture.sh.
import { createServer } from 'node:https';
import { readFileSync } from 'node:fs';
const tls = new URL('./fixtures/tls/', import.meta.url);
let paired = false;
let offline = false;
let pairing = false;
let starts = 0;
let cancels = 0;
let keyDelay = 0;
let authDelay = 0;
let authChecks = 0;
const commands = [];
createServer({ key: readFileSync(new URL('server.key', tls)), cert: readFileSync(new URL('server.crt', tls)) }, async (request, response) => {
  let raw = '';
  for await (const chunk of request) { raw += chunk; if (raw.length > 8192) { response.writeHead(413).end(); return; } }
  const body = raw ? JSON.parse(raw) : {};
  response.setHeader('Content-Type', 'application/json');
  const send = (value) => response.end(JSON.stringify(value));
  if (request.url === '/__control') {
    if ('offline' in body) offline = body.offline;
    if ('paired' in body) paired = body.paired;
    if ('keyDelay' in body) keyDelay = Math.max(0, Math.min(3000, Number(body.keyDelay)));
    if ('authDelay' in body) authDelay = Math.max(0, Math.min(3000, Number(body.authDelay)));
    if (body.reset) { pairing = false; paired = false; starts = 0; cancels = 0; commands.length = 0; }
    return send({ offline, paired, pairing, starts, cancels, commands, authChecks });
  }
  if (request.url === '/state/device/power_mode') { authChecks++; await new Promise(resolve => setTimeout(resolve, authDelay)); }
  if (offline) { response.writeHead(503); return send({ STATUS: { RESULT: 'FAILURE' } }); }
  const success = (ITEM = {}) => send({ STATUS: { RESULT: 'SUCCESS' }, ITEM, ITEMS: [{ TYPE: 'T_VIZIO_DEVICE_INFO_V1', VALUE: { CAST_NAME: 'Fixture TV' } }] });
  if (request.url === '/state/device/deviceinfo') return success();
  if (request.url === '/pairing/start') {
    if (pairing) return send({ STATUS: { RESULT: 'BUSY' } });
    pairing = true; starts++; return success({ CHALLENGE_TYPE: 1, PAIRING_REQ_TOKEN: 42 });
  }
  if (request.url === '/pairing/pair') {
    if (body.RESPONSE_VALUE !== '1234') return send({ STATUS: { RESULT: 'INVALID_PARAMETER' } });
    paired = true; pairing = false; return success({ AUTH_TOKEN: 'synthetic-fixture-token' });
  }
  if (request.url === '/pairing/cancel') { pairing = false; cancels++; return success(); }
  if (!paired || request.headers.auth !== 'synthetic-fixture-token') { response.writeHead(401); return send({ STATUS: { RESULT: 'UNAUTHORIZED' } }); }
  if (request.url === '/key_command/') { commands.push(body.KEYLIST); await new Promise(resolve => setTimeout(resolve, keyDelay)); }
  if (request.url === '/app/launch') commands.push('launch');
  success();
}).listen(7345, '127.0.0.1', () => console.log('Synthetic SmartCast TV on https://10.0.2.2:7345; PIN 1234'));
