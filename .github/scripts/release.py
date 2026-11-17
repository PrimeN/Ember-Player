"""Publish a requested version using its successful main-build artifact."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile


def gh(*args):
    result = subprocess.run(['gh', *args], text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout


def version_and_commit(root):
    match = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"',
                      (root / 'app/build.gradle.kts').read_text())
    if not match:
        raise ValueError('Cannot determine the release version.')
    version = match.group(1)
    requested = os.environ.get('REQUESTED_TAG') or os.environ.get('GITHUB_REF', '').removeprefix('refs/tags/')
    if requested != f'v{version}':
        raise ValueError('The requested version tag must match versionName.')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    subprocess.run(['git', 'merge-base', '--is-ancestor', commit, 'origin/main'], cwd=root, check=True)
    return version, commit


def validate_assets(root, version, commit):
    dist = root / 'dist'
    record = json.loads((dist / 'release-metadata.json').read_text())
    certificate = (root / 'release-signing-certificate.sha256').read_text().strip().lower()
    expected_name = f'ember-player-{version}.apk'
    if (record.get('applicationId') != 'app.ember.player' or record.get('version') != version
            or record.get('sourceCommit') != commit or record.get('signed') is not True
            or record.get('certificateSha256') != certificate or record.get('apk') != expected_name):
        raise ValueError('The build artifact does not match the requested signed production release.')
    apk = dist / expected_name
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if record.get('sha256') != digest or (dist / 'SHA256SUMS.txt').read_text().strip() != f'{digest}  {expected_name}':
        raise ValueError('The APK checksum does not match the validated build.')
    if (dist / 'SIGNING-CERTIFICATE-SHA256.txt').read_text().strip() != certificate:
        raise ValueError('The published signing fingerprint does not match the release identity.')
    return apk, digest


def notes_for_version(text, version):
    heading = re.search(r'^## \[' + re.escape(version) + r'\][^\n]*\n', text, re.MULTILINE)
    if not heading:
        raise ValueError('A matching CHANGELOG section is required for publication.')
    return re.split(r'^## ', text[heading.end():], maxsplit=1, flags=re.MULTILINE)[0].strip()


def main():
    root = Path(__file__).resolve().parents[2]
    version, commit = version_and_commit(root)
    repo = os.environ['GITHUB_REPOSITORY']
    if len(sys.argv) > 1 and sys.argv[1] == 'find-build':
        data = json.loads(gh('api', f'repos/{repo}/actions/workflows/android.yml/runs?branch=main&event=push&status=success&per_page=100'))
        run = next((run for run in data.get('workflow_runs', []) if run['head_sha'] == commit
                    and run['head_branch'] == 'main' and run['conclusion'] == 'success'), None)
        if run is None:
            raise ValueError('A successful Android player build of this commit on main is required before releasing.')
        with open(os.environ['GITHUB_OUTPUT'], 'a') as outputs:
            outputs.write(f'run_id={run["id"]}\n')
        print(f'Using validated main build {run["id"]}.')
        return
    apk, digest = validate_assets(root, version, commit)
    tag = f'v{version}'
    response = subprocess.run(['gh', 'api', f'repos/{repo}/releases/tags/{tag}'], text=True, capture_output=True)
    if response.returncode == 0:
        existing = json.loads(response.stdout)
        if any(asset['name'] == apk.name and asset.get('digest') == f'sha256:{digest}' for asset in existing.get('assets', [])):
            print(f'{tag} is already published with the validated APK.')
            return
        raise ValueError('A release already exists for this version; it will not be replaced.')
    if 'HTTP 404' not in response.stderr:
        raise RuntimeError(response.stderr.strip())
    notes = notes_for_version((root / 'CHANGELOG.md').read_text(), version)
    notes = re.sub(r'\]\((?!https?://|#)([^)]+)\)',
                   lambda m: f'](https://github.com/{repo}/blob/{tag}/{m.group(1)})', notes)
    with tempfile.TemporaryDirectory() as temporary:
        path = Path(temporary) / 'notes.md'
        path.write_text(notes + '\n')
        gh('release', 'create', tag, str(apk), str(root / 'dist/SHA256SUMS.txt'),
           str(root / 'dist/SIGNING-CERTIFICATE-SHA256.txt'), '--repo', repo, '--target', commit,
           '--title', f'Ember Player {version}', '--notes-file', str(path), '--latest')
    print(f'Published Ember Player {version}.')


if __name__ == '__main__':
    main()
