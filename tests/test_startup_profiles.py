import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('profiles', Path(__file__).resolve().parents[1] / 'scripts/merge-startup-profiles.py')
profiles = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profiles)


class ProfileChecks(unittest.TestCase):
    def test_device_profiles_merge_method_flags_and_exclude_test_fixtures(self):
        with tempfile.TemporaryDirectory() as directory:
            first, second = [Path(directory) / name for name in ('phone.txt', 'tv.txt')]
            first.write_text('HSLapp/ember/player/Library;->open()V\nLapp/ember/tv/player/ProfileMedia;\n')
            second.write_text('PLapp/ember/player/Library;->open()V\nLandroidx/compose/runtime/Composer;\n')
            result = profiles.merge([first, second])
            self.assertEqual(1, result.count('->open()V'))
            self.assertIn('HSPLapp/ember/player/Library;->open()V', result)
            self.assertNotIn('ProfileMedia', result)

    def test_missing_app_rules_and_non_profile_text_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'profile.txt'
            for content in ['Landroidx/compose/Runtime;\n', 'not a profile\n']:
                path.write_text(content)
                with self.assertRaises(ValueError):
                    profiles.merge([path])
