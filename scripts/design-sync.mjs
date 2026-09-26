import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { tmpdir } from 'node:os';

const root = resolve(import.meta.dirname, '..');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const [mode = 'check', repository = '../design', revision] = process.argv.slice(2);
const write = (path, bytes) => { const target = resolve(root, path); mkdirSync(dirname(target), { recursive: true }); writeFileSync(target, bytes); };
if (mode === 'sync') {
  if (!/^[a-f0-9]{40}$/.test(revision ?? '')) throw Error('Pass an immutable design commit');
  const repo = resolve(root, repository);
  const git = (...args) => execFileSync('git', ['-C', repo, ...args], { maxBuffer: 16 * 1024 * 1024 });
  const files = {};
  const names = git('ls-tree', '-r', '--name-only', revision).toString().trim().split('\n');
  const docs = ['DESIGN.md', 'DESIGN_SYNC.md', 'ANDROID_DESIGN.md', 'TV_POLISH.md', 'viptv-design-system/README.md', 'viptv-design-system/components.md', 'viptv-design-system/copy.md', 'viptv-design-system/decisions.md', 'viptv-design-system/tokens/tokens.json'];
  const mappings = docs.map(path => [path, `design-contract/${path}`]);
  for (const path of names) {
    if (path.startsWith('assets/fonts/')) mappings.push([path, path.endsWith('.ttf') ? `app/src/androidMain/res/font/${path.split('/').at(-1)}` : `app/src/androidMain/assets/design/fonts/${path.split('/').at(-1)}`]);
    if (path.startsWith('assets/roku/roku/images/lucide/') && !path.endsWith('.svg')) mappings.push([path, `app/src/androidMain/assets/design/lucide/${path.split('/').at(-1)}`]);
  }
  for (const [source, destination] of mappings) {
    const bytes = git('show', `${revision}:${source}`);
    write(destination, bytes); files[destination] = { source, sha256: hash(bytes) };
  }
  const temporary = mkdtempSync(resolve(tmpdir(), 'viptv-android-theme-'));
  try {
    for (const path of ['viptv-design-system/tokens/tokens.json', 'viptv-design-system/tools/targets.json', 'viptv-design-system/tools/gen-themes.mjs']) {
      const target = resolve(temporary, 'design', path); mkdirSync(dirname(target), { recursive: true }); writeFileSync(target, git('show', `${revision}:${path}`));
    }
    mkdirSync(resolve(temporary, 'android'));
    execFileSync('node', [resolve(temporary, 'design/viptv-design-system/tools/gen-themes.mjs')], { stdio: 'pipe' });
    const path = 'app/src/androidMain/kotlin/org/viptv/app/theme/ViptvTokens.kt';
    const bytes = readFileSync(resolve(temporary, 'android', path));
    write(path, bytes); files[path] = { source: 'generated from pinned tokens', sha256: hash(bytes) };
  } finally { rmSync(temporary, { recursive: true, force: true }); }
  write('DESIGN_REF', revision + '\n');
  write('design-contract/lock.json', JSON.stringify({ repository: 'viptv-org/design', revision, files }, null, 2) + '\n');
} else if (mode === 'check') {
  const lock = JSON.parse(readFileSync(resolve(root, 'design-contract/lock.json')));
  if (readFileSync(resolve(root, 'DESIGN_REF'), 'utf8').trim() !== lock.revision) throw Error('Design pin mismatch');
  for (const [path, item] of Object.entries(lock.files)) {
    if (path.startsWith('/') || path.split('/').includes('..') || hash(readFileSync(resolve(root, path))) !== item.sha256) throw Error(`Design artifact mismatch: ${path}`);
  }
} else throw Error('Use sync <repo> <commit> or check');
console.log('Android design integrity passed');
