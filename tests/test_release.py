"""Distribution checks for production APK metadata, identity and integrity."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

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

    def test_release_notes_require_the_requested_version(self):
        text = '# Changelog\n\n## [0.7.5]\nCurrent features.\n\n## [0.1.0]\nOld notes.\n'
        self.assertEqual(publish.notes_for_version(text, '0.7.5'), 'Current features.')
        with self.assertRaisesRegex(ValueError, 'matching CHANGELOG'):
            publish.notes_for_version(text, '1.0.0')


if __name__ == '__main__':
    unittest.main()
