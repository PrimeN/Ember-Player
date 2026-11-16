#!/usr/bin/env python3
"""Validate the surface metadata bridge shipped with both Android ARM builds."""
import hashlib
import pathlib
import re
import struct
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
source = root/'app/native/hdr-surface'
properties = dict(line.split('=', 1) for line in (source/'checksums.properties').read_text().splitlines() if line and not line.startswith('#'))
assert hashlib.sha256((source/'hdr_surface.c').read_bytes()).hexdigest() == properties['source.sha256']
expected = {'arm64-v8a': 183, 'armeabi-v7a': 40}
files = list((root/'app/src/main/jniLibs').glob('*/libember_surface.so'))
assert {file.parent.name for file in files} == expected.keys()
for file in files:
    data = file.read_bytes()
    assert data[:4] == b'\x7fELF' and data[5] == 1
    assert struct.unpack_from('<H', data, 18)[0] == expected[file.parent.name]
    assert hashlib.sha256(data).hexdigest() == properties[file.parent.name+'.sha256']
    symbols = subprocess.check_output(['readelf', '--dyn-syms', '--wide', str(file)], text=True)
    for name in ('readSpace', 'writeSpace'):
        symbol = 'Java_app_ember_tv_player_HdrSurface_'+name
        assert any(symbol in row and ' UND ' not in row for row in symbols.splitlines()), symbol
    dynamic = subprocess.check_output(['readelf', '-d', str(file)], text=True)
    assert 'Library soname: [libember_surface.so]' in dynamic
    needed = re.findall(r'\(NEEDED\).*\[(.*?)\]', dynamic)
    assert set(needed) <= {'libandroid.so', 'libdl.so', 'libc.so'}
    assert 'libandroid.so' in needed
    for row in subprocess.check_output(['readelf', '-lW', str(file)], text=True).splitlines():
        if row.strip().startswith('LOAD'):
            assert int(row.split()[-1], 16) >= 16384
    print(file.parent.name+': surface bridge hashes, JNI exports, platform-only links and 16 KiB alignment passed')
