"""Verify the APK built by Gradle and prepare downloadable build assets."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile


def expected_version(root):
    match = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"',
                      (root / 'app/build.gradle.kts').read_text())
    if not match:
        raise ValueError('Cannot determine the app release version.')
    return match.group(1)


def verify_metadata(metadata, version):
    if metadata.get('applicationId') != 'app.ember.player':
        raise ValueError('Only the production application ID can be packaged for release.')
    elements = metadata.get('elements', [])
    if len(elements) != 1 or elements[0].get('versionName') != version:
        raise ValueError('The APK version does not match the app source.')
    name = elements[0].get('outputFile', '')
    if not name or Path(name).name != name or not name.endswith('.apk'):
        raise ValueError('Invalid APK output path.')
    return elements[0]


def apksigner():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        raise ValueError('An Android SDK is required to verify release signatures.')
    candidates = list((Path(sdk) / 'build-tools').glob('*/apksigner'))
    if not candidates:
        raise ValueError('The Android SDK does not contain apksigner.')
    return max(candidates, key=lambda p: tuple(int(v) for v in re.findall(r'\d+', p.parent.name)))


def signing_fingerprint(output):
    match = re.search(r'^(?:Signer #1|V[1-4] Signer:)\s+certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$',
                      output, re.MULTILINE)
    return match.group(1).lower() if match else None


def prepare(root, require_signed=False):
    root = Path(root).resolve()
    version = expected_version(root)
    output = root / 'app/build/outputs/apk/release'
    metadata = json.loads((output / 'output-metadata.json').read_text())
    element = verify_metadata(metadata, version)
    apk = output / element['outputFile']
    with zipfile.ZipFile(apk) as archive:
        abis = {name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
        if abis != {'arm64-v8a', 'armeabi-v7a'}:
            raise ValueError('The APK must contain exactly the supported ARM32 and ARM64 libraries.')
    verification = subprocess.run([str(apksigner()), 'verify', '--print-certs', str(apk)],
                                  text=True, capture_output=True)
    signed = verification.returncode == 0
    fingerprint = None
    if signed:
        fingerprint = signing_fingerprint(verification.stdout)
        expected = (root / 'release-signing-certificate.sha256').read_text().strip().lower()
        if fingerprint != expected:
            raise ValueError('The APK signing certificate does not match the release identity.')
    if require_signed and not signed:
        raise ValueError('A signed production APK is required for distribution.')
    destination = root / 'dist'
    destination.mkdir(exist_ok=True)
    filename = f'ember-player-{version}{"" if signed else "-unsigned"}.apk'
    target = destination / filename
    shutil.copyfile(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (destination / 'SHA256SUMS.txt').write_text(f'{digest}  {filename}\n')
    if signed:
        (destination / 'SIGNING-CERTIFICATE-SHA256.txt').write_text(fingerprint + '\n')
    record = {'applicationId': metadata['applicationId'], 'version': version,
              'versionCode': element['versionCode'], 'signed': signed,
              'certificateSha256': fingerprint, 'apk': filename, 'sha256': digest,
              'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()}
    (destination / 'release-metadata.json').write_text(json.dumps(record, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as outputs:
            outputs.write(f'version={version}\nsigned={str(signed).lower()}\n')
    print(f'Prepared {filename}; signed={signed}; SHA-256={digest}')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-signed', action='store_true')
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[1], args.require_signed)
