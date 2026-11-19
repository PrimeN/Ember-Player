#!/usr/bin/env python3
"""Exercise SFTP keys and SMB cryptography after applying the app's R8 rules."""
from pathlib import Path
import os
import subprocess
import tempfile
import zipfile


def verify(root):
    paths = (root / 'app/build/network-crypto-classpath.txt').read_text().splitlines()
    r8, *libraries = paths
    java_home = Path(os.environ['JAVA_HOME'])
    source = root / 'scripts/tests/NetworkCryptoSmoke.java'
    with tempfile.TemporaryDirectory() as temporary:
        work = Path(temporary)
        subprocess.run([str(java_home / 'bin/javac'), '-cp', os.pathsep.join(libraries),
                        '-d', str(work), str(source)], check=True)
        entry = work / 'entry.jar'
        subprocess.run([str(java_home / 'bin/jar'), 'cf', str(entry), '-C', str(work),
                        'NetworkCryptoSmoke.class'], check=True)
        rules = work / 'rules.pro'
        # The Android release checks missing platform classes separately. This host check
        # only executes the network algorithms reachable from the production dependencies.
        rules.write_text((root / 'app/proguard-rules.pro').read_text() + '\n-dontwarn **\n'
                         '-keep class NetworkCryptoSmoke { public static void main(java.lang.String[]); }\n')
        output = work / 'shrunk.jar'
        subprocess.run([str(java_home / 'bin/java'), '-Xmx1g', '-cp', r8,
                        'com.android.tools.r8.R8', '--classfile', '--release',
                        '--lib', str(java_home), '--pg-conf', str(rules),
                        '--output', str(output), str(entry), *libraries], check=True)
        # Android consumes DEX, not the original signed dependency JARs. Strip their
        # stale signatures from this temporary host JAR after R8 changes its contents.
        runtime = work / 'runtime.jar'
        with zipfile.ZipFile(output) as source_jar, zipfile.ZipFile(runtime, 'w') as target:
            for info in source_jar.infolist():
                name = info.filename.upper()
                if name.startswith('META-INF/') and (name.endswith(('.SF', '.RSA', '.DSA', '.EC')) or name == 'META-INF/MANIFEST.MF'):
                    continue
                target.writestr(info, source_jar.read(info))
        subprocess.run([str(java_home / 'bin/java'), '-cp', str(runtime), 'NetworkCryptoSmoke'], check=True)


if __name__ == '__main__':
    verify(Path(__file__).resolve().parents[1])
