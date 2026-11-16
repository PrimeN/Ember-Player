#!/usr/bin/env python3
"""Verify shipped IAMF libraries, supported CPUs, JNI registration and alignment."""
import hashlib, pathlib, re, struct, subprocess

root = pathlib.Path(__file__).resolve().parent.parent
native = root/'app/native/iamf'
props = dict(line.split('=', 1) for line in (native/'checksums.properties').read_text().splitlines()
             if line and not line.startswith('#'))
java = (root/'app/src/main/java/androidx/media3/decoder/iamf/IamfDecoder.java').read_text()
registered = dict(re.findall(r'\{"(iamf\w+)",\s*"([^"]+)"', (native/'jni/iamf_jni.cc').read_text()))
types = {'int':'I', 'long':'J', 'boolean':'Z', 'void':'V', 'byte[]':'[B', 'ByteBuffer':'Ljava/nio/ByteBuffer;'}
declared = {}
for result, name, args in re.findall(r'private native\s+(?:@\w+\s+)*(\w+)\s+(iamf\w+)\(([^)]*)\)', java):
    arguments = [re.sub(r'@\w+\s*', '', arg).strip().split()[0] for arg in args.split(',') if arg.strip()]
    declared[name] = '(' + ''.join(types[t] for t in arguments) + ')' + types[result]
assert registered == declared, ('JNI signature mismatch', registered, declared)
abis = {'arm64-v8a':183, 'armeabi-v7a':40}
files = list((root/'app/src/main/jniLibs').glob('*/libiamfJNI.so'))
assert {f.parent.name for f in files} == set(abis)
for file in files:
    data = file.read_bytes(); abi = file.parent.name
    assert data[:4] == b'\x7fELF' and data[5] == 1
    assert struct.unpack_from('<H', data, 18)[0] == abis[abi]
    assert hashlib.sha256(data).hexdigest() == props[abi+'.sha256']
    dynamic = subprocess.check_output(['readelf', '-d', str(file)], text=True)
    needed = set(re.findall(r'\(NEEDED\).*\[(.*?)\]', dynamic))
    assert needed <= {'libandroid.so','liblog.so','libm.so','libdl.so','libc.so'}, needed
    assert 'Library soname: [libiamfJNI.so]' in dynamic
    symbols = subprocess.check_output(['readelf','--dyn-syms','--wide',str(file)],text=True)
    exports = {parts[7] for line in symbols.splitlines() if len(parts:=line.split()) >= 8
               and parts[6] != 'UND' and parts[4] in ('GLOBAL','WEAK')}
    assert exports == {'JNI_OnLoad'}, exports
    for line in subprocess.check_output(['readelf','-lW',str(file)],text=True).splitlines():
        if line.strip().startswith('LOAD'): assert int(line.split()[-1],16) >= 16384
    print(f'{abi}: checksum, CPU, JNI registration, hidden helpers and 16 KiB alignment passed')
