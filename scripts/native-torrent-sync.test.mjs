import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, linkSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, symlinkSync, unlinkSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { checkArtifacts, syncArtifacts, verifyElf } from './native-torrent-sync.mjs';

const revision = 'a'.repeat(40);
const binding = 'ffi/generated/kotlin/uniffi/playback_gateway_ffi/playback_gateway_ffi.kt';
const library = abi => `ffi/generated/android/jniLibs/${abi}/libplayback_gateway_ffi.so`;
const roots = [];
const scratch = resolve(import.meta.dirname, '../.scratch/native-torrent/artifact-validator-tests');
mkdirSync(scratch, { recursive: true });
afterEach(() => { for (const root of roots.splice(0)) rmSync(root, { recursive: true }); });

// Header-only synthetic ELF vectors test the importer, not ABI loading or torrent playback.
function elf(abi, alignment = 16384) {
    const is64 = abi !== 'armeabi-v7a';
    const data = Buffer.alloc(192);
    data.set([0x7f, 69, 76, 70, is64 ? 2 : 1, 1, 1]);
    data.writeUInt16LE(3, 16);
    data.writeUInt16LE({ 'armeabi-v7a': 40, 'arm64-v8a': 183, x86_64: 62 }[abi], 18);
    data.writeUInt32LE(1, 20);
    const start = is64 ? 64 : 52;
    if (is64) data.writeBigUInt64LE(BigInt(start), 32); else data.writeUInt32LE(start, 28);
    data.writeUInt16LE(is64 ? 56 : 32, is64 ? 54 : 42);
    data.writeUInt16LE(1, is64 ? 56 : 44);
    data.writeUInt32LE(1, start);
    if (is64) {
        data.writeBigUInt64LE(192n, start + 32);
        data.writeBigUInt64LE(192n, start + 40);
        data.writeBigUInt64LE(BigInt(alignment), start + 48);
    } else {
        data.writeUInt32LE(192, start + 16);
        data.writeUInt32LE(192, start + 20);
        data.writeUInt32LE(alignment, start + 28);
    }
    return data;
}
function put(root, path, bytes) {
    mkdirSync(dirname(resolve(root, path)), { recursive: true });
    writeFileSync(resolve(root, path), bytes);
}
function fixture() {
    const root = mkdtempSync(resolve(scratch, 'case-'));
    roots.push(root);
    const source = resolve(root, 'bundle');
    const project = resolve(root, 'project');
    mkdirSync(source); mkdirSync(project);
    const files = { LICENSE: Buffer.from('License test vector'), 'PROVENANCE.md': Buffer.from('Provenance test vector'),
        [binding]: Buffer.from('Kotlin test vector') };
    for (const abi of ['armeabi-v7a', 'arm64-v8a', 'x86_64']) files[library(abi)] = elf(abi);
    for (const [path, bytes] of Object.entries(files)) put(source, path, bytes);
    const lock = { version: 1, repository: 'viptv-org/playback-gateway', revision,
        files: Object.fromEntries(Object.entries(files).map(([path, bytes]) => [path, createHash('sha256').update(bytes).digest('hex')])) };
    const save = () => put(source, 'manifest.json', JSON.stringify(lock));
    save();
    return { source, project, lock, save };
}
function untouched(project) {
    assert.equal(existsSync(resolve(project, 'TORRENT_REF')), false);
    assert.equal(existsSync(resolve(project, 'vendor/playback-gateway/lock.json')), false);
    assert.equal(existsSync(resolve(project, 'vendor/playback-gateway', binding)), false);
}

test('imports and checks all required pinned ABIs, binding and notices', () => {
    const { source, project } = fixture();
    syncArtifacts(project, source, revision);
    assert.equal(checkArtifacts(project).revision, revision);
    assert.deepEqual(readFileSync(resolve(project, 'vendor/playback-gateway', binding)), readFileSync(resolve(source, binding)));
    syncArtifacts(project, source, revision);
    assert.equal(checkArtifacts(project).revision, revision);
});
test('requires an explicit matching immutable revision before writes', () => {
    const { source, project } = fixture();
    assert.throws(() => syncArtifacts(project, source), /expected immutable/);
    assert.throws(() => syncArtifacts(project, source, 'b'.repeat(40)), /revision mismatch/);
    untouched(project);
});
test('rejects missing required ABI before writes', () => {
    const { source, project, lock, save } = fixture();
    delete lock.files[library('armeabi-v7a')]; save();
    assert.throws(() => syncArtifacts(project, source, revision), /Incomplete/);
    untouched(project);
});
test('rejects modified binding bytes without normalizing checksums', () => {
    const { source, project } = fixture();
    put(source, binding, 'Changed\r\n');
    assert.throws(() => syncArtifacts(project, source, revision), /checksum/);
    untouched(project);
});
test('rejects destination owner edits before overwriting anything', () => {
    const { source, project } = fixture();
    syncArtifacts(project, source, revision);
    const path = resolve(project, 'vendor/playback-gateway', binding);
    writeFileSync(path, 'Owner edit');
    assert.throws(() => syncArtifacts(project, source, revision), /checksum/);
    assert.equal(readFileSync(path, 'utf8'), 'Owner edit');
});
test('rejects unpinned destination files', () => {
    const { source, project } = fixture();
    syncArtifacts(project, source, revision);
    put(project, 'vendor/playback-gateway/extra.kt', 'Owner file');
    assert.throws(() => checkArtifacts(project), /Unpinned/);
    assert.throws(() => syncArtifacts(project, source, revision), /Unpinned/);
});
for (const path of ['../escape', '/absolute', 'C:/escape', 'THIRD_PARTY/../escape', 'THIRD_PARTY\\escape', 'THIRD_PARTY//escape',
    'THIRD_PARTY/NUL', 'THIRD_PARTY/CON.txt', 'THIRD_PARTY/notice.', 'THIRD_PARTY/.git']) {
    test(`rejects unsafe manifest path ${JSON.stringify(path)}`, () => {
        const { source, project, lock, save } = fixture();
        lock.files[path] = 'a'.repeat(64); save();
        assert.throws(() => syncArtifacts(project, source, revision), /Invalid artifact manifest entry/);
        untouched(project);
    });
}
test('rejects source directory links', () => {
    const { source, project } = fixture();
    const target = resolve(source, 'notices'); mkdirSync(target);
    symlinkSync(target, resolve(source, 'THIRD_PARTY'), 'junction');
    const lock = JSON.parse(readFileSync(resolve(source, 'manifest.json')));
    lock.files['THIRD_PARTY/license.txt'] = 'a'.repeat(64);
    put(source, 'manifest.json', JSON.stringify(lock));
    assert.throws(() => syncArtifacts(project, source, revision), /path is a link/);
    untouched(project);
});
test('refuses an unmanaged destination directory', () => {
    const { source, project } = fixture();
    mkdirSync(resolve(project, 'vendor/playback-gateway'), { recursive: true });
    assert.throws(() => syncArtifacts(project, source, revision));
    untouched(project);
});
test('requires an explicit migration instead of silently removing notices', () => {
    const { source, project, lock, save } = fixture();
    put(source, 'THIRD_PARTY/test.txt', 'Notice');
    lock.files['THIRD_PARTY/test.txt'] = createHash('sha256').update('Notice').digest('hex'); save();
    syncArtifacts(project, source, revision);
    delete lock.files['THIRD_PARTY/test.txt']; save();
    assert.throws(() => syncArtifacts(project, source, revision), /explicit migration/);
    assert.equal(readFileSync(resolve(project, 'vendor/playback-gateway/THIRD_PARTY/test.txt'), 'utf8'), 'Notice');
});
for (const abi of ['armeabi-v7a', 'arm64-v8a', 'x86_64']) {
    test(`checks ${abi} ELF alignment and machine`, () => {
        assert.doesNotThrow(() => verifyElf(elf(abi), abi));
        assert.throws(() => verifyElf(elf(abi, 4096), abi), /alignment/);
        assert.throws(() => verifyElf(elf(abi), abi === 'arm64-v8a' ? 'x86_64' : 'arm64-v8a'), /target/);
        assert.throws(() => verifyElf(Buffer.alloc(8), abi), /target/);
    });
}
test('rejects load segments extending beyond the ELF', () => {
    const bytes = elf('arm64-v8a');
    bytes.writeBigUInt64LE(193n, 64 + 32);
    assert.throws(() => verifyElf(bytes, 'arm64-v8a'), /bounds/);
});
test('rejects executable ELF and missing load segments', () => {
    const bytes = elf('arm64-v8a');
    bytes.writeUInt16LE(2, 16);
    assert.throws(() => verifyElf(bytes, 'arm64-v8a'), /target/);
    bytes.writeUInt16LE(3, 16); bytes.writeUInt32LE(2, 64);
    assert.throws(() => verifyElf(bytes, 'arm64-v8a'), /no load/);
});
test('rejects large non-power-of-two ELF alignment exactly', () => {
    const bytes = elf('arm64-v8a');
    bytes.writeBigUInt64LE(4503599627370497n, 64 + 48);
    assert.throws(() => verifyElf(bytes, 'arm64-v8a'), /alignment/);
});
test('rejects Windows case-fold collisions before writes', () => {
    const { source, project, lock, save } = fixture();
    for (const path of ['THIRD_PARTY/Notice.txt', 'THIRD_PARTY/notice.txt']) {
        put(source, path, 'Notice'); lock.files[path] = createHash('sha256').update('Notice').digest('hex');
    }
    save();
    assert.throws(() => syncArtifacts(project, source, revision), /collision/);
    untouched(project);
});
test('rejects a junction used as the project root', () => {
    const { source, project } = fixture();
    const alias = resolve(project, '../alias');
    symlinkSync(project, alias, 'junction');
    assert.throws(() => syncArtifacts(alias, source, revision), /link/);
    untouched(project);
});
test('rejects dangling revision metadata links before payload writes', () => {
    const { source, project } = fixture();
    const target = resolve(project, '../missing-metadata');
    symlinkSync(target, resolve(project, 'TORRENT_REF'), 'junction');
    assert.throws(() => syncArtifacts(project, source, revision), /link/);
    assert.equal(existsSync(target), false);
    assert.equal(existsSync(resolve(project, 'vendor/playback-gateway', binding)), false);
});
test('check rejects a junction in the vendor ancestor', () => {
    const { source, project } = fixture();
    syncArtifacts(project, source, revision);
    const original = resolve(project, 'vendor');
    const external = resolve(project, '../external-vendor');
    // Move only the fixture-created directory to create a real ancestor-link vector.
    renameSync(original, external); symlinkSync(external, original, 'junction');
    assert.throws(() => checkArtifacts(project), /link/);
});
test('rejects hard-linked destination payloads without changing outside bytes', () => {
    const { source, project, lock, save } = fixture();
    syncArtifacts(project, source, revision);
    const dest = resolve(project, 'vendor/playback-gateway', binding);
    const outside = resolve(project, '../outside-binding.kt');
    const original = readFileSync(dest);
    writeFileSync(outside, original); unlinkSync(dest); linkSync(outside, dest);
    put(source, binding, 'Updated binding');
    lock.files[binding] = createHash('sha256').update('Updated binding').digest('hex');
    lock.revision = 'b'.repeat(40); save();
    assert.throws(() => syncArtifacts(project, source, lock.revision), /linked/);
    assert.deepEqual(readFileSync(outside), original);
});
test('preflights destination type conflicts before overwriting a snapshot', () => {
    const { source, project, lock, save } = fixture();
    syncArtifacts(project, source, revision);
    const paths = [...Object.keys(lock.files).map(path => 'vendor/playback-gateway/' + path), 'vendor/playback-gateway/lock.json', 'TORRENT_REF'];
    const prior = paths.map(path => [path, readFileSync(resolve(project, path))]);
    mkdirSync(resolve(project, 'vendor/playback-gateway/THIRD_PARTY/new-notice.txt'), { recursive: true });
    put(source, binding, 'Updated binding');
    lock.files[binding] = createHash('sha256').update('Updated binding').digest('hex');
    put(source, 'THIRD_PARTY/new-notice.txt', 'Notice');
    lock.files['THIRD_PARTY/new-notice.txt'] = createHash('sha256').update('Notice').digest('hex');
    lock.revision = 'b'.repeat(40); save();
    assert.throws(() => syncArtifacts(project, source, lock.revision), /type/);
    for (const [path, bytes] of prior) assert.deepEqual(readFileSync(resolve(project, path)), bytes);
});
test('rejects unknown manifest metadata and wrong repository', () => {
    const { source, project, lock, save } = fixture();
    lock.qualified = true; save();
    assert.throws(() => syncArtifacts(project, source, revision), /manifest/);
    delete lock.qualified; lock.repository = 'other/repository'; save();
    assert.throws(() => syncArtifacts(project, source, revision), /manifest/);
    untouched(project);
});
