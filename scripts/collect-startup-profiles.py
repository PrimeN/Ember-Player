#!/usr/bin/env python3
"""Collect startup and scrolling profiles on a dedicated Android emulator."""
from pathlib import Path
import argparse
import os
import subprocess
import time


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--television', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    sdk = Path(os.environ['ANDROID_HOME'])
    image = 'system-images;android-36;android-tv;x86_64' if args.television else 'system-images;android-35;default;x86_64'
    run(str(sdk / 'cmdline-tools/latest/bin/sdkmanager'), image, 'emulator')
    name = 'ember-profile-tv' if args.television else 'ember-profile-phone'
    avds = root / 'benchmark/build/avd'
    avds.mkdir(parents=True, exist_ok=True)
    os.environ['ANDROID_AVD_HOME'] = str(avds)
    avd = avds / f'{name}.avd'
    run(str(sdk / 'cmdline-tools/latest/bin/avdmanager'), 'create', 'avd', '--force', '--name', name, '--path', str(avd), '--package', image, input='no\n', text=True)
    (avds / f'{name}.ini').write_text(f'avd.ini.encoding=UTF-8\npath={avd}\n')
    logs = root / 'benchmark/build/reports/device'
    logs.mkdir(parents=True, exist_ok=True)
    adb = str(sdk / 'platform-tools/adb')
    with (logs / 'emulator.log').open('w') as output:
        emulator = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', name, '-port', '5554', '-no-window', '-no-audio',
            '-no-snapshot', '-no-boot-anim', '-no-metrics', '-gpu', 'swiftshader_indirect', '-memory', '2048', '-cores', '2'], stdout=output, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 480
            while time.monotonic() < deadline:
                if emulator.poll() is not None:
                    raise RuntimeError('Emulator exited before boot. See emulator.log.')
                try:
                    reply = subprocess.run([adb, '-s', 'emulator-5554', 'shell', 'getprop', 'sys.boot_completed'], capture_output=True, text=True, timeout=10)
                    if reply.stdout.strip() == '1':
                        break
                except subprocess.TimeoutExpired:
                    pass
                time.sleep(5)
            else:
                raise RuntimeError('Emulator did not boot within eight minutes.')
            for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
                run(adb, 'shell', 'settings', 'put', 'global', key, '0')
            run(adb, 'shell', 'settings', 'put', 'system', 'screen_off_timeout', '1800000')
            run(adb, 'shell', 'svc', 'power', 'stayon', 'true')
            run(adb, 'shell', 'input', 'keyevent', '82')
            run(str(root / 'gradlew'), '--no-daemon', '--max-workers=2', '-PincludePerformanceTests=true', '-PuseMavenCentralMirror=true', ':benchmark:connectedBenchmarkAndroidTest', cwd=root)
            # AGP normally pulls additional test outputs; retain a direct copy as well.
            profiles = logs / 'profiles'
            profiles.mkdir(exist_ok=True)
            subprocess.run([adb, 'pull', '/sdcard/Android/media/app.ember.benchmark/additional_test_output', str(profiles)], check=False)
        finally:
            with (logs / 'logcat.txt').open('w') as logcat:
                try:
                    subprocess.run([adb, 'logcat', '-d', '-t', '2000'], stdout=logcat, stderr=subprocess.STDOUT, timeout=30)
                except subprocess.TimeoutExpired:
                    pass
            try:
                subprocess.run([adb, '-s', 'emulator-5554', 'emu', 'kill'], capture_output=True, timeout=15)
            except subprocess.TimeoutExpired:
                pass
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()


if __name__ == '__main__':
    main()
