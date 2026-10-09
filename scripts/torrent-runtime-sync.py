#!/usr/bin/env python3
"""Adopt/check immutable shared Go runtime artifacts without loading JNI in the UI."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'vendor/torrent-runtime'
ABIS = {'arm64-v8a':183, 'armeabi-v7a':40, 'x86_64':62}
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def check():
    lock = json.loads((DEST / 'lock.json').read_text())
    assert (ROOT / 'TORRENT_RUNTIME_REF').read_text().strip() == lock['revision']
    for name,expected in lock['files'].items():
        path = DEST / name
        assert not path.is_symlink() and digest(path) == expected, 'Runtime artifact integrity failed'
    for abi,machine in ABIS.items():
        for name in ['libtorrent_runtime.so','libtorrent_runtime_jni.so']:
            data=(DEST/'jni'/abi/name).read_bytes()
            assert data[:4] == b'\x7fELF' and data[5] == 1
            assert struct.unpack_from('<H',data,18)[0] == machine
            bits=data[4]
            off=struct.unpack_from('<Q' if bits == 2 else '<I',data,32 if bits == 2 else 28)[0]
            size,count=struct.unpack_from('<HH',data,54 if bits == 2 else 42)
            for index in range(count):
                row=off+index*size
                if struct.unpack_from('<I',data,row)[0] == 1:
                    alignment=struct.unpack_from('<Q' if bits == 2 else '<I',data,row+(48 if bits == 2 else 28))[0]
                    assert alignment >= 16384, 'Runtime ELF alignment failed'
    print('Shared runtime artifact integrity passed')
if sys.argv[1:2] == ['sync']:
    source=Path(sys.argv[2]).resolve(); artifacts=Path(sys.argv[3]).resolve()
    build=json.loads((artifacts/'build.json').read_text())
    revision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip()
    assert build['revision'] == revision and not build['dirty'] and build['platform'] == 'android'
    names=['classes.jar','NDK-TOOLCHAIN-NOTICE.txt']+[f'jni/{abi}/{name}' for abi in ABIS for name in ['libtorrent_runtime.so','libtorrent_runtime_jni.so']]
    DEST.mkdir(parents=True,exist_ok=True)
    for name in names:
        assert digest(artifacts/name) == build['files'][name]
        dest=DEST/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(artifacts/name,dest)
    for name in ['LICENSE','PROVENANCE.md']:
        shutil.copyfile(source/name,DEST/name)
    for name in ['go.mod','go.sum','README.md']:
        shutil.copyfile(source/'torrent-runtime'/name,DEST/name)
    # Preserve the actual dependency notices, including transitive Go/C dependencies.
    go=os.environ.get('GO','go')
    raw=subprocess.check_output([go,'list','-m','-json','all'],cwd=source/'torrent-runtime',text=True)
    decoder=json.JSONDecoder();modules=[]
    while raw.strip():
        module,end=decoder.raw_decode(raw.lstrip());raw=raw.lstrip()[end:];modules.append(module)
    for module in modules:
        directory=module.get('Dir')
        if not directory or module.get('Main'):continue
        for path in Path(directory).rglob('*'):
            if path.is_file() and (path.name.lower().startswith(('license','copying','notice'))):
                relative=path.relative_to(directory)
                target=DEST/'licenses'/module['Path']/str(relative)
                target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,target)
    shutil.copyfile(artifacts/'build.json',DEST/'build.json')
    files={str(path.relative_to(DEST)):digest(path) for path in sorted(DEST.rglob('*')) if path.is_file() and path.name != 'lock.json'}
    (DEST/'lock.json').write_text(json.dumps({'repository':'viptv-org/playback-gateway','revision':revision,'files':files},indent=2)+'\n')
    (ROOT/'TORRENT_RUNTIME_REF').write_text(revision+'\n')
check()
