#!/usr/bin/env bash
# Build Media3's IAMF renderer dependency for the two supported ARM architectures.
set -euo pipefail
task_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ndk_path="${1:?Usage: build-iamf-decoder.sh NDK_DIRECTORY CMAKE_DIRECTORY BUILD_DIRECTORY}"
cmake_path="${2:?Supply Android SDK CMake 3.22.1}"
build_path="${3:?Supply a writable build directory}"
ndk_path="$(cd -- "$ndk_path" && pwd)"
cmake_path="$(cd -- "$cmake_path" && pwd)"
mkdir -p "$build_path"
build_path="$(cd -- "$build_path" && pwd)"
bazel_bin="${BAZEL:-bazel}"
"$bazel_bin" --output_user_root="$build_path/bazel" version > "$build_path/bazel-version.txt"
python3 - "$build_path/bazel-version.txt" <<'PY'
import pathlib,sys
assert 'Build label: 8.5.1' in pathlib.Path(sys.argv[1]).read_text(), 'Use Bazel 8.5.1'
PY
source_commit=e552e7d6d8b55530d201fd2764ca7e12d2c66cd6
curl --fail --location --retry 2 "https://codeload.github.com/AOMediaCodec/iamf-tools/tar.gz/$source_commit" -o "$build_path/source.tar.gz"
python3 - "$build_path/source.tar.gz" "$ndk_path/source.properties" <<'PY'
import hashlib,pathlib,sys
assert hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest()=='08a4f80ff8f30a70da926fec4532f789763188f088e324d84d3fc285ef8d326c'
assert 'Pkg.Revision = 29.0.14206865' in pathlib.Path(sys.argv[2]).read_text()
PY
mkdir -p "$build_path/source"
tar -xzf "$build_path/source.tar.gz" --strip-components=1 -C "$build_path/source"
# Upstream already gates its AAC implementation behind this macro. Remove its unused
# build dependencies too, so no FDK-AAC objects or headers are fetched or linked.
python3 - "$build_path/source" <<'PY'
import pathlib,sys
root=pathlib.Path(sys.argv[1])
for name,line in [('iamf/cli/BUILD','        "//iamf/cli/codec:aac_decoder",\n'),
                  ('iamf/obu/decoder_config/BUILD','        "@fdk_aac//:fdk_sys_lib",\n')]:
    file=root/name;text=file.read_text();assert text.count(line)==1;file.write_text(text.replace(line,''))
PY
for abi in arm64-v8a armeabi-v7a; do
    case "$abi" in arm64-v8a) config=android_arm64 ;; armeabi-v7a) config=android_armv7 ;; esac
    (
        cd "$build_path/source"
        ANDROID_NDK_HOME="$ndk_path" "$bazel_bin" --output_user_root="$build_path/bazel" build \
            --experimental_cc_static_library --copt=-femulated-tls --copt=-DIAMF_TOOLS_DISABLE_AAC_DECODER \
            --config="$config" --compilation_mode=opt --copt=-fvisibility=hidden --copt=-fno-exceptions \
            --copt=-fno-rtti --jobs="${IAMF_BUILD_JOBS:-4}" //iamf/include/iamf_tools:iamf_decoder_static
    )
    "$cmake_path/bin/cmake" -S "$task_root/app/native/iamf/jni" -B "$build_path/jni-$abi" -G Ninja \
        -DCMAKE_MAKE_PROGRAM="$cmake_path/bin/ninja" -DCMAKE_TOOLCHAIN_FILE="$ndk_path/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
        -DIAMF_SOURCE_DIR="$build_path/source" \
        -DIAMF_STATIC_LIBRARY="$build_path/source/bazel-bin/iamf/include/iamf_tools/libiamf_decoder_static.a" \
        '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,--exclude-libs,ALL -Wl,--gc-sections'
    "$cmake_path/bin/cmake" --build "$build_path/jni-$abi" -j "${IAMF_BUILD_JOBS:-4}"
    cp "$build_path/jni-$abi/libiamfJNI.so" "$task_root/app/src/main/jniLibs/$abi/libiamfJNI.so"
    "$ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded "$task_root/app/src/main/jniLibs/$abi/libiamfJNI.so"
    chmod 644 "$task_root/app/src/main/jniLibs/$abi/libiamfJNI.so"
done
python3 "$task_root/scripts/write-iamf-notices.py" "$build_path/source" "$build_path/source/bazel-source/external"
python3 - "$task_root" <<'PY'
import hashlib,pathlib,sys
root=pathlib.Path(sys.argv[1]);lines=['# IAMF Tools; Media3 1.11.1 JNI; AAC disabled; ARM only',
    'source.commit=e552e7d6d8b55530d201fd2764ca7e12d2c66cd6',
    'source.sha256=08a4f80ff8f30a70da926fec4532f789763188f088e324d84d3fc285ef8d326c',
    'media3.version=1.11.1',
    'media3.commit=8c6678b657ede1e7883fc164ef73ed483c7796c3','ndk.version=29.0.14206865','bazel.version=8.5.1']
for abi in ['arm64-v8a','armeabi-v7a']:
    lines.append(abi+'.sha256='+hashlib.sha256((root/'app/src/main/jniLibs'/abi/'libiamfJNI.so').read_bytes()).hexdigest())
(root/'app/native/iamf/checksums.properties').write_text('\n'.join(lines)+'\n')
PY
python3 "$task_root/scripts/verify-iamf-native.py"
