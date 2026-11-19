"""Verify Gradle APKs and the app bundle, then prepare downloadable release assets."""
from pathlib import Path
import argparse
import base64
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


SUPPORTED_ABIS = {'arm64-v8a', 'armeabi-v7a'}


def verify_elements(metadata, version):
    if metadata.get('applicationId') != 'app.ember.player':
        raise ValueError('Only the production application ID can be packaged for release.')
    outputs = {}
    codes = set()
    for element in metadata.get('elements', []):
        if element.get('versionName') != version:
            raise ValueError('The APK version does not match the app source.')
        name = element.get('outputFile', '')
        if not name or Path(name).name != name or not name.endswith('.apk'):
            raise ValueError('Invalid APK output path.')
        filters = element.get('filters', [])
        abi = None
        if filters:
            if len(filters) != 1 or filters[0].get('filterType') != 'ABI' or filters[0].get('value') not in SUPPORTED_ABIS:
                raise ValueError('Unsupported APK architecture filter.')
            abi = filters[0]['value']
        if abi in outputs:
            raise ValueError('Duplicate APK architecture.')
        outputs[abi] = element
        codes.add(element.get('versionCode'))
    if set(outputs) not in ({None}, {None, *SUPPORTED_ABIS}) or len(codes) != 1:
        raise ValueError('Expected a universal APK or a complete ARM APK set with one version code.')
    return outputs


def verify_metadata(metadata, version):
    return verify_elements(metadata, version)[None]


def protobuf_fields(data):
    """Read the bounded scalar fields used by AAPT2's Resources.proto manifest."""
    offset = 0
    def varint():
        nonlocal offset
        value = 0
        for shift in range(0, 70, 7):
            if offset >= len(data):
                raise ValueError('Truncated bundle manifest.')
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
        raise ValueError('Invalid bundle manifest integer.')
    while offset < len(data):
        tag = varint()
        wire = tag & 7
        if wire == 0:
            value = varint()
        elif wire in (1, 2, 5):
            size = varint() if wire == 2 else (8 if wire == 1 else 4)
            end = offset + size
            if end > len(data):
                raise ValueError('Truncated bundle manifest field.')
            value = data[offset:end]
            offset = end
        else:
            raise ValueError('Unsupported bundle manifest field.')
        yield tag >> 3, value


def bundle_identity(data):
    # XmlNode.element=1; XmlElement.name=3/attribute=4; XmlAttribute namespace/name/value=1/2/3.
    node = dict(protobuf_fields(data))
    fields = list(protobuf_fields(node[1]))
    if dict(fields).get(3) != b'manifest':
        raise ValueError('Missing bundle manifest root.')
    attributes = {}
    for number, value in fields:
        if number == 4:
            attr = dict(protobuf_fields(value))
            key = (attr.get(1, b'').decode(), attr[2].decode())
            if key in attributes:
                raise ValueError('Duplicate bundle manifest attribute.')
            attributes[key] = attr.get(3, b'').decode()
    android = 'http://schemas.android.com/apk/res/android'
    return attributes.get(('', 'package')), attributes.get((android, 'versionName')), attributes.get((android, 'versionCode'))


def verify_bundle(bundle, version, code, certificate, require_signed):
    with zipfile.ZipFile(bundle) as archive:
        identity = bundle_identity(archive.read('base/manifest/AndroidManifest.xml'))
        abis = {n.split('/')[2] for n in archive.namelist() if n.startswith('base/lib/') and n.endswith('.so')}
        if identity != ('app.ember.player', version, str(code)) or abis != SUPPORTED_ABIS:
            raise ValueError('The app bundle identity, version or architectures do not match the APKs.')
    verification = subprocess.run(['jarsigner', '-J-Duser.language=en', '-verify', '-strict', str(bundle)], text=True, capture_output=True)
    signed = verification.returncode in (0, 4) and re.search(r'^jar verified[.,]', verification.stdout, re.MULTILINE) is not None
    if require_signed and not signed:
        raise ValueError('A signed production app bundle is required for distribution.')
    if signed:
        result = subprocess.run(['keytool', '-J-Duser.language=en', '-printcert', '-jarfile', str(bundle), '-rfc'],
                                text=True, capture_output=True, check=True)
        certificates = re.findall(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----', result.stdout, re.DOTALL)
        if len(re.findall(r'^Signer #\d+:', result.stdout, re.MULTILINE)) != 1 or not certificates or hashlib.sha256(base64.b64decode(certificates[0])).hexdigest() != certificate:
            raise ValueError('The app bundle signing certificate does not match the release identity.')
    return signed


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
    elements = verify_elements(metadata, version)
    element = elements[None]
    expected_code = int(re.search(r'versionCode\s*=\s*(\d+)', (root / 'app/build.gradle.kts').read_text()).group(1))
    if element['versionCode'] != expected_code:
        raise ValueError('The APK version code does not match the app source.')
    expected = (root / 'release-signing-certificate.sha256').read_text().strip().lower()
    destination = root / 'dist'
    destination.mkdir(exist_ok=True)
    assets = []
    signed = None
    fingerprint = None
    def copy_asset(source, filename):
        target = destination / filename
        shutil.copyfile(source, target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        assets.append({'filename': filename, 'sha256': digest})
        return digest
    for abi, item in elements.items():
        apk = output / item['outputFile']
        with zipfile.ZipFile(apk) as archive:
            abis = {name.split('/')[1] for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
            if abis != ({abi} if abi else SUPPORTED_ABIS):
                raise ValueError('The APK does not contain its advertised ARM libraries.')
        verification = subprocess.run([str(apksigner()), 'verify', '--print-certs', str(apk)], text=True, capture_output=True)
        current_signed = verification.returncode == 0
        if signed is not None and current_signed != signed:
            raise ValueError('All release APKs must have the same signing status.')
        signed = current_signed
        if signed:
            fingerprint = signing_fingerprint(verification.stdout)
            if fingerprint != expected:
                raise ValueError('The APK signing certificate does not match the release identity.')
        if require_signed and not signed:
            raise ValueError('A signed production APK is required for distribution.')
        filename = f'ember-player-{version}{"-" + abi if abi else ""}{"" if signed else "-unsigned"}.apk'
        digest = copy_asset(apk, filename)
        if abi is None:
            universal_name, universal_digest = filename, digest
    bundle = root / 'app/build/outputs/bundle/release/app-release.aab'
    if require_signed and not bundle.is_file():
        raise ValueError('The signed release must include an app bundle.')
    if bundle.is_file():
        if verify_bundle(bundle, version, expected_code, expected, require_signed) != signed:
            raise ValueError('The app bundle and APK signing status must match.')
        copy_asset(bundle, f'ember-player-{version}{"" if signed else "-unsigned"}.aab')
    assets.sort(key=lambda asset: asset['filename'])
    (destination / 'SHA256SUMS.txt').write_text(''.join(f'{asset["sha256"]}  {asset["filename"]}\n' for asset in assets))
    if signed:
        (destination / 'SIGNING-CERTIFICATE-SHA256.txt').write_text(fingerprint + '\n')
    record = {'applicationId': metadata['applicationId'], 'version': version,
              'versionCode': element['versionCode'], 'signed': signed,
              'certificateSha256': fingerprint, 'apk': universal_name, 'sha256': universal_digest,
              'assets': assets,
              'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()}
    (destination / 'release-metadata.json').write_text(json.dumps(record, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as outputs:
            outputs.write(f'version={version}\nsigned={str(signed).lower()}\n')
    print(f'Prepared {len(assets)} release packages; signed={signed}.')
    return record


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-signed', action='store_true')
    args = parser.parse_args()
    prepare(Path(__file__).resolve().parents[1], args.require_signed)
