"""Distribution checks for production APK metadata, identity and integrity."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import subprocess
import zipfile
import shutil

ROOT = Path(__file__).resolve().parents[1]


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


prepare = load('prepare_player_release', ROOT / 'scripts/prepare-release.py')
publish = load('publish_player_release', ROOT / '.github/scripts/release.py')


class ReleaseChecks(unittest.TestCase):
    def test_signing_requires_successful_push_checks_for_the_exact_main_commit(self):
        valid = {'id': 42, 'head_sha': 'approved', 'head_branch': 'main', 'event': 'push',
                 'status': 'completed', 'conclusion': 'success'}
        self.assertEqual(publish.successful_main_build([valid], 'approved')['id'], 42)
        for change in [{'head_sha': 'other'}, {'head_branch': 'beta'}, {'event': 'pull_request'},
                       {'status': 'in_progress'}, {'conclusion': 'failure'}]:
            with self.subTest(change=change), self.assertRaisesRegex(ValueError, 'main is required'):
                publish.successful_main_build([{**valid, **change}], 'approved')
        with self.assertRaises(ValueError):
            publish.successful_main_build([], 'approved')

    def test_signer_fingerprints_support_current_and_older_sdk_output(self):
        fingerprint = 'a' * 64
        for prefix in ['Signer #1', 'V2 Signer:', 'V3 Signer:']:
            with self.subTest(prefix=prefix):
                self.assertEqual(prepare.signing_fingerprint(
                    prefix + ' certificate SHA-256 digest: ' + fingerprint + '\n'), fingerprint)
        self.assertIsNone(prepare.signing_fingerprint('Source Stamp certificate SHA-256 digest: ' + fingerprint))

    def test_debug_application_cannot_be_published_as_production(self):
        with self.assertRaisesRegex(ValueError, 'production application ID'):
            prepare.verify_metadata({'applicationId': 'app.ember.player.debug'}, '0.7.5')

    def test_wrong_version_and_output_path_are_rejected(self):
        for element in [{'versionName': '0.1.0', 'outputFile': 'app-release.apk'},
                        {'versionName': '0.7.5', 'outputFile': '../outside.apk'}]:
            with self.subTest(element=element), self.assertRaises(ValueError):
                prepare.verify_metadata({'applicationId': 'app.ember.player', 'elements': [element]}, '0.7.5')

    def test_split_apks_require_all_supported_architectures_and_one_version_code(self):
        def item(abi=None, code=1000):
            return {'versionName': '1.0.0', 'versionCode': code, 'outputFile': f'app-{abi or "universal"}.apk',
                    'filters': [{'filterType': 'ABI', 'value': abi}] if abi else []}
        valid = [item(), item('arm64-v8a'), item('armeabi-v7a')]
        metadata = {'applicationId': 'app.ember.player', 'elements': valid}
        self.assertEqual(set(prepare.verify_elements(metadata, '1.0.0')), {None, 'arm64-v8a', 'armeabi-v7a'})
        for elements in [valid[:2], valid + [item('x86')], valid + [item()],
                         [item(), item('arm64-v8a', 999), item('armeabi-v7a')]]:
            with self.subTest(elements=elements), self.assertRaises(ValueError):
                prepare.verify_elements({**metadata, 'elements': elements}, '1.0.0')

    @staticmethod
    def manifest(version='1.0.0', code='1000', package='app.ember.player'):
        def field(number, value):
            value = value.encode() if isinstance(value, str) else value
            length = len(value)
            encoded = bytearray()
            while length >= 128:
                encoded.append((length & 127) | 128)
                length >>= 7
            encoded.append(length)
            return bytes([number << 3 | 2]) + encoded + value
        def attribute(namespace, name, value):
            return field(4, field(1, namespace) + field(2, name) + field(3, value))
        android = 'http://schemas.android.com/apk/res/android'
        return field(1, field(3, 'manifest') + attribute('', 'package', package) +
                     attribute(android, 'versionName', version) + attribute(android, 'versionCode', code))

    def test_bundle_identity_is_read_from_the_packaged_manifest(self):
        self.assertEqual(prepare.bundle_identity(self.manifest()), ('app.ember.player', '1.0.0', '1000'))
        with self.assertRaises(ValueError):
            prepare.bundle_identity(self.manifest()[:-1])
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / 'app.aab'
            with zipfile.ZipFile(bundle, 'w') as archive:
                archive.writestr('base/manifest/AndroidManifest.xml', self.manifest(version='0.8.5'))
                for abi in prepare.SUPPORTED_ABIS:
                    archive.writestr(f'base/lib/{abi}/libtest.so', b'library')
            with self.assertRaisesRegex(ValueError, 'identity, version or architectures'):
                prepare.verify_bundle(bundle, '1.0.0', 1000, 'a' * 64, True)

    def test_successful_jarsigner_exit_alone_does_not_mean_a_bundle_is_signed(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle = Path(directory) / 'app.aab'
            with zipfile.ZipFile(bundle, 'w') as archive:
                archive.writestr('base/manifest/AndroidManifest.xml', self.manifest())
                for abi in prepare.SUPPORTED_ABIS:
                    archive.writestr(f'base/lib/{abi}/libtest.so', b'library')
            result = subprocess.CompletedProcess([], 0, 'jar is unsigned.', '')
            with patch.object(prepare.subprocess, 'run', return_value=result):
                with self.assertRaisesRegex(ValueError, 'signed production app bundle'):
                    prepare.verify_bundle(bundle, '1.0.0', 1000, 'a' * 64, True)

    @unittest.skipUnless(shutil.which('keytool') and shutil.which('jarsigner'), 'Requires the release JDK')
    def test_signed_bundle_pins_the_certificate_and_rejects_unsigned_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            key, bundle = work / 'fixture.p12', work / 'fixture.aab'
            def command(*args):
                return subprocess.run(args, check=True, capture_output=True)
            command('keytool', '-genkeypair', '-keystore', str(key), '-storepass', 'fixture-passphrase',
                    '-alias', 'fixture', '-dname', 'CN=Release test fixture', '-keyalg', 'RSA', '-validity', '2')
            certificate = command('keytool', '-exportcert', '-keystore', str(key), '-storepass', 'fixture-passphrase',
                                  '-alias', 'fixture').stdout
            fingerprint = hashlib.sha256(certificate).hexdigest()
            with zipfile.ZipFile(bundle, 'w') as archive:
                archive.writestr('base/manifest/AndroidManifest.xml', self.manifest())
                for abi in prepare.SUPPORTED_ABIS:
                    archive.writestr(f'base/lib/{abi}/libtest.so', b'library')
            command('jarsigner', '-keystore', str(key), '-storepass', 'fixture-passphrase', str(bundle), 'fixture')
            self.assertTrue(prepare.verify_bundle(bundle, '1.0.0', 1000, fingerprint, True))
            with self.assertRaisesRegex(ValueError, 'signing certificate'):
                prepare.verify_bundle(bundle, '1.0.0', 1000, '0' * 64, True)
            with zipfile.ZipFile(bundle, 'a') as archive:
                archive.writestr('unsigned-entry', b'changed after signing')
            with self.assertRaisesRegex(ValueError, 'signed production app bundle'):
                prepare.verify_bundle(bundle, '1.0.0', 1000, fingerprint, True)

    def fixture(self, root):
        dist = root / 'dist'
        dist.mkdir()
        name = 'ember-player-0.7.5.apk'
        (dist / name).write_bytes(b'validated APK bytes')
        digest = hashlib.sha256((dist / name).read_bytes()).hexdigest()
        fingerprint = 'f' * 64
        (root / 'release-signing-certificate.sha256').write_text(fingerprint + '\n')
        (dist / 'SIGNING-CERTIFICATE-SHA256.txt').write_text(fingerprint + '\n')
        (dist / 'SHA256SUMS.txt').write_text(f'{digest}  {name}\n')
        record = {'applicationId': 'app.ember.player', 'version': '0.7.5', 'sourceCommit': 'commit',
                  'signed': True, 'certificateSha256': fingerprint, 'apk': name, 'sha256': digest}
        (dist / 'release-metadata.json').write_text(json.dumps(record))
        return record

    def test_matching_signed_artifact_can_be_published(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = self.fixture(root)
            apk, digest = publish.validate_assets(root, '0.7.5', 'commit')
            self.assertEqual(apk.name, record['apk'])
            self.assertEqual(digest, record['sha256'])

    def test_unsigned_wrong_commit_or_certificate_is_rejected(self):
        for change in [{'signed': False}, {'sourceCommit': 'other'}, {'certificateSha256': '0' * 64},
                       {'applicationId': 'app.ember.player.debug'}, {'apk': '../other.apk'}]:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                record = self.fixture(root)
                record.update(change)
                (root / 'dist/release-metadata.json').write_text(json.dumps(record))
                with self.assertRaisesRegex(ValueError, 'requested signed production release'):
                    publish.validate_assets(root, '0.7.5', 'commit')

    def test_changed_apk_cannot_reuse_a_valid_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            record = self.fixture(root)
            (root / 'dist' / record['apk']).write_bytes(b'changed APK')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                publish.validate_assets(root, '0.7.5', 'commit')

    def test_split_or_bundle_tampering_cannot_reuse_the_universal_apk_checksum(self):
        for damaged in ['arm64-v8a.apk', 'armeabi-v7a.apk', 'aab']:
            with self.subTest(damaged=damaged), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                record = self.fixture(root)
                assets = [{'filename': record['apk'], 'sha256': record['sha256']}]
                for ending in ['-arm64-v8a.apk', '-armeabi-v7a.apk', '.aab']:
                    name = 'ember-player-0.7.5' + ending
                    data = name.encode()
                    (root / 'dist' / name).write_bytes(data)
                    assets.append({'filename': name, 'sha256': hashlib.sha256(data).hexdigest()})
                record['assets'] = sorted(assets, key=lambda asset: asset['filename'])
                (root / 'dist/release-metadata.json').write_text(json.dumps(record))
                (root / 'dist/SHA256SUMS.txt').write_text(''.join(f'{a["sha256"]}  {a["filename"]}\n' for a in record['assets']))
                publish.validate_assets(root, '0.7.5', 'commit')
                victim = next(a['filename'] for a in assets if a['filename'].endswith(damaged))
                (root / 'dist' / victim).write_bytes(b'changed package')
                with self.assertRaisesRegex(ValueError, 'checksum'):
                    publish.validate_assets(root, '0.7.5', 'commit')

    def test_partial_publication_is_not_reported_as_complete(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ['universal.apk', 'arm64.apk', 'app.aab']]
            for path in paths:
                path.write_bytes(path.name.encode())
            existing = {'assets': [{'name': p.name, 'digest': 'sha256:' + hashlib.sha256(p.read_bytes()).hexdigest()} for p in paths]}
            self.assertTrue(publish.existing_assets_match(existing, paths))
            existing['assets'].pop()
            self.assertFalse(publish.existing_assets_match(existing, paths))

    def test_release_notes_require_the_requested_version(self):
        text = '# Changelog\n\n## [0.7.5]\nCurrent features.\n\n## [0.1.0]\nOld notes.\n'
        self.assertEqual(publish.notes_for_version(text, '0.7.5'), 'Current features.')
        with self.assertRaisesRegex(ValueError, 'matching CHANGELOG'):
            publish.notes_for_version(text, '1.0.0')


if __name__ == '__main__':
    unittest.main()
