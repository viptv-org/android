import { createHash } from 'node:crypto';
import { lstatSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const repository = 'viptv-org/playback-gateway';
const binding = 'ffi/generated/kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt';
const machines = { 'armeabi-v7a': 40, 'arm64-v8a': 183, x86_64: 62 };
const required = ['LICENSE', 'PROVENANCE.md', 'THIRD_PARTY/native-dependencies.json',
    'THIRD_PARTY/native-NOTICES.txt', 'THIRD_PARTY/native-SHA256SUMS.txt', binding,
    ...Object.keys(machines).map(abi => `ffi/generated/android/jniLibs/${abi}/libplayback_gateway_ffi.so`)];
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const fail = message => { throw new Error(message); };

function safePath(path) {
    return typeof path === 'string' && /^[A-Za-z0-9_.\/-]+$/.test(path) &&
        !path.startsWith('/') && !path.split('/').some(part => !part || part === '.' || part === '..' ||
            part.endsWith('.') || /^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(part));
}
function allowed(path) {
    return required.includes(path) || /^THIRD_PARTY\/[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(path);
}
const statIfPresent = path => lstatSync(path, { throwIfNoEntry: false });
function destination(root, path) {
    const start = lstatSync(root);
    if (start.isSymbolicLink()) fail('Artifact project root is a link');
    if (!start.isDirectory()) fail('Invalid artifact destination type');
    let current = root;
    const parts = path.split('/');
    for (let index = 0; index < parts.length; index++) {
        current = resolve(current, parts[index]);
        const stat = statIfPresent(current);
        if (!stat) continue;
        if (stat.isSymbolicLink()) fail('Artifact destination is a link');
        if (index < parts.length - 1) {
            if (!stat.isDirectory()) fail('Invalid artifact destination type');
        } else {
            if (!stat.isFile()) fail('Invalid artifact destination type');
            if (stat.nlink !== 1) fail('Artifact destination is multiply linked');
        }
    }
}
function regular(root, path, limit = 134_217_728) {
    if (!safePath(path)) fail('Invalid artifact path');
    let current = root;
    if (lstatSync(current).isSymbolicLink()) fail('Artifact root is a link');
    for (const part of path.split('/')) {
        current = resolve(current, part);
        if (lstatSync(current).isSymbolicLink()) fail('Artifact path is a link');
    }
    const stat = lstatSync(current);
    if (!stat.isFile() || stat.size > limit) fail('Artifact is not a bounded regular file');
    if (stat.nlink !== 1) fail('Artifact is multiply linked');
    return readFileSync(current);
}
function manifest(root, name, revision) {
    const bytes = regular(root, name, 1_048_576);
    const lock = JSON.parse(bytes.toString('utf8'));
    if (!object(lock) || Object.keys(lock).sort().join(',') !== 'files,repository,revision,version' ||
        lock.version !== 1 || lock.repository !== repository ||
        !/^[0-9a-f]{40}$/.test(lock.revision) || lock.revision !== revision || !object(lock.files)) {
        fail('Artifact manifest or revision mismatch');
    }
    const entries = Object.entries(lock.files);
    if (new Set(entries.map(([path]) => path.toLowerCase())).size !== entries.length) fail('Artifact path case collision');
    if (entries.length > 256 || required.some(path => !Object.hasOwn(lock.files, path))) fail('Incomplete artifact manifest');
    for (const [path, hash] of entries) {
        if (!safePath(path) || !allowed(path) || typeof hash !== 'string' || !/^[0-9a-f]{64}$/.test(hash)) {
            fail('Invalid artifact manifest entry');
        }
    }
    return lock;
}

// Validate the actual ELF target and every load segment, not an exporter assertion.
export function verifyElf(bytes, abi) {
    const cls = bytes[4];
    const is64 = cls === 2;
    const header = is64 ? 64 : 52;
    if (bytes.length < header || bytes.subarray(0, 4).toString('hex') !== '7f454c46' ||
        ![1, 2].includes(cls) || bytes[5] !== 1 || bytes[6] !== 1 ||
        bytes.readUInt16LE(16) !== 3 || bytes.readUInt16LE(18) !== machines[abi] ||
        bytes.readUInt32LE(20) !== 1 || is64 !== (abi !== 'armeabi-v7a')) fail('Invalid native ELF target');
    const uint64 = offset => {
        const value = bytes.readBigUInt64LE(offset);
        if (value > BigInt(Number.MAX_SAFE_INTEGER)) fail('Invalid native ELF bounds');
        return Number(value);
    };
    const offset = is64 ? uint64(32) : bytes.readUInt32LE(28);
    const size = bytes.readUInt16LE(is64 ? 54 : 42);
    const count = bytes.readUInt16LE(is64 ? 56 : 44);
    if (size !== (is64 ? 56 : 32) || count === 0 || count > 1024 || offset < header || offset + size * count > bytes.length) {
        fail('Invalid native ELF program headers');
    }
    let loads = 0;
    for (let index = 0; index < count; index++) {
        const entry = offset + index * size;
        if (bytes.readUInt32LE(entry) !== 1) continue;
        loads++;
        const alignment = is64 ? uint64(entry + 48) : bytes.readUInt32LE(entry + 28);
        const fileOffset = is64 ? uint64(entry + 8) : bytes.readUInt32LE(entry + 4);
        const fileSize = is64 ? uint64(entry + 32) : bytes.readUInt32LE(entry + 16);
        const memorySize = is64 ? uint64(entry + 40) : bytes.readUInt32LE(entry + 20);
        const address = is64 ? uint64(entry + 16) : bytes.readUInt32LE(entry + 8);
        const integerAlignment = BigInt(alignment);
        if (alignment < 16384 || (integerAlignment & (integerAlignment - 1n)) !== 0n ||
            fileOffset + fileSize > bytes.length || fileSize > memorySize || fileOffset % alignment !== address % alignment) {
            fail('Invalid native ELF load alignment or bounds');
        }
    }
    if (!loads) fail('Native ELF has no load segments');
}

function payload(root, lock) {
    const files = new Map();
    for (const [path, hash] of Object.entries(lock.files)) {
        const bytes = regular(root, path);
        if (bytes.length > 134_217_728 || digest(bytes) !== hash) fail('Artifact checksum mismatch');
        const abi = path.match(/^ffi\/generated\/android\/jniLibs\/([^/]+)\//)?.[1];
        if (abi) verifyElf(bytes, abi);
        files.set(path, bytes);
    }
    return files;
}
function inventory(root, lock) {
    const walk = (directory, prefix = '') => {
        for (const entry of readdirSync(directory, { withFileTypes: true })) {
            const path = prefix + entry.name;
            if (entry.isSymbolicLink()) fail('Imported artifact path is a link');
            if (entry.isDirectory()) walk(resolve(directory, entry.name), path + '/');
            else if (path !== 'lock.json' && !Object.hasOwn(lock.files, path)) fail('Unpinned imported artifact');
        }
    };
    walk(root);
}
export function checkArtifacts(project) {
    destination(project, 'TORRENT_REF');
    destination(project, 'vendor/playback-gateway/lock.json');
    const revision = regular(project, 'TORRENT_REF').toString('utf8').trim();
    if (!/^[0-9a-f]{40}$/.test(revision)) fail('Invalid torrent revision pin');
    const dest = resolve(project, 'vendor/playback-gateway');
    const lock = manifest(project, 'vendor/playback-gateway/lock.json', revision);
    payload(dest, lock);
    inventory(dest, lock);
    return lock;
}
export function syncArtifacts(project, source, revision) {
    if (!/^[0-9a-f]{40}$/.test(revision ?? '')) fail('Supply the expected immutable gateway revision');
    const lock = manifest(source, 'manifest.json', revision);
    const files = payload(source, lock);
    const dest = resolve(project, 'vendor/playback-gateway');
    // Verify all inputs, metadata and destination types before the first write.
    destination(project, 'TORRENT_REF');
    destination(project, 'vendor/playback-gateway/lock.json');
    for (const path of files.keys()) destination(project, 'vendor/playback-gateway/' + path);
    if (statIfPresent(dest) || statIfPresent(resolve(project, 'TORRENT_REF'))) {
        const prior = checkArtifacts(project);
        if (Object.keys(prior.files).some(path => !files.has(path))) fail('Artifact removal requires an explicit migration');
    }
    for (const [path, bytes] of files) {
        mkdirSync(dirname(resolve(dest, path)), { recursive: true });
        writeFileSync(resolve(dest, path), bytes);
    }
    writeFileSync(resolve(dest, 'lock.json'), JSON.stringify(lock, null, 2) + '\n');
    writeFileSync(resolve(project, 'TORRENT_REF'), revision + '\n');
    return lock;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    const project = resolve(import.meta.dirname, '..');
    const mode = process.argv[2] ?? 'check';
    const lock = mode === 'sync' ? syncArtifacts(project, resolve(process.argv[3] ?? fail('Supply an artifact bundle')), process.argv[4]) :
        mode === 'check' ? checkArtifacts(project) : fail('Use sync <bundle-directory> <revision> or check');
    console.log(`Torrent artifact integrity passed: ${lock.revision}; playback qualification is separate.`);
}
