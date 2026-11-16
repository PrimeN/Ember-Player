#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ndk="${1:?Usage: build-hdr-surface.sh NDK_DIRECTORY}"
grep -q 'Pkg.Revision = 29.0.14206865' "$ndk/source.properties"
for abi in arm64-v8a armeabi-v7a; do
    case "$abi" in arm64-v8a) target=aarch64-linux-android29;; armeabi-v7a) target=armv7a-linux-androideabi29;; esac
    mkdir -p "$root/app/src/main/jniLibs/$abi"
    "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/$target-clang" -shared -O2 -fvisibility=hidden \
        -Wl,-z,max-page-size=16384 -Wl,-soname,libember_surface.so \
        "$root/app/native/hdr-surface/hdr_surface.c" -landroid -o "$root/app/src/main/jniLibs/$abi/libember_surface.so"
    "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$root/app/src/main/jniLibs/$abi/libember_surface.so"
done
python3 - "$root" <<'PY'
import pathlib,hashlib,sys
r=pathlib.Path(sys.argv[1]);lines=['ndk.version=29.0.14206865','api=29']
for name,p in [('source',r/'app/native/hdr-surface/hdr_surface.c')]+[(a,r/f'app/src/main/jniLibs/{a}/libember_surface.so') for a in ['arm64-v8a','armeabi-v7a']]:
 lines.append(name+'.sha256='+hashlib.sha256(p.read_bytes()).hexdigest())
(r/'app/native/hdr-surface/checksums.properties').write_text('\n'.join(lines)+'\n')
PY
