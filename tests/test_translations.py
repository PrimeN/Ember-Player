import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('translations', Path(__file__).resolve().parents[1] / 'scripts/check-translations.py')
translations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(translations)


class TranslationChecks(unittest.TestCase):
    def fixture(self, directory, translated):
        root = Path(directory)
        (root / 'values').mkdir()
        (root / 'values-fr').mkdir()
        (root / 'values/strings.xml').write_text('<resources><string name="item">%1$s: %2$d</string><plurals name="count"><item quantity="one">%1$d file</item><item quantity="other">%1$d files</item></plurals></resources>')
        (root / 'values-fr/strings.xml').write_text('<resources>' + translated + '</resources>')
        return root

    def test_translations_can_reorder_arguments_and_remain_partial(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.fixture(directory, '<string name="item">%2$d : %1$s</string>')
            self.assertEqual([], translations.check(root))

    def test_missing_or_changed_arguments_and_missing_other_are_rejected(self):
        for text in ['<string name="item">%1$s</string>', '<string name="item">%1$d: %2$d</string>',
                     '<plurals name="count"><item quantity="one">%1$d fichier</item></plurals>',
                     '<string name="unknown">Texte</string>']:
            with self.subTest(text=text), tempfile.TemporaryDirectory() as directory:
                self.assertTrue(translations.check(self.fixture(directory, text)))
