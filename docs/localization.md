# Translating the interface

English resources in `app/src/main/res/values/` are the source for UI translations.
No additional language packs are enabled yet. Android generates the supported
locale list from the resource directories. Missing translations fall back to English.

Add translations in a language-specific resource directory, for example
`app/src/main/res/values-fr/`. Partial translations fall back to English. Keep
resource names unchanged and do not translate filenames, media titles, saved
settings IDs, protocol names, or strings marked `translatable="false"`.

Preserve indexed arguments such as `%1$s` and `%2$d`, including their types. They
may move to a different position in the sentence. Translate plural resources
using the quantities needed by the language; every plural needs an `other` form.
Keep string-array entries in the same order as English. Escape XML characters
and Android apostrophes correctly, and retain accessibility labels.

Run these checks before submitting a language pack:

```sh
python3 scripts/check-translations.py
python3 -m unittest discover -s tests -p 'test_translations.py'
./gradlew testDebugUnitTest lintRelease
```

Debug builds include Android's expanded-text and right-to-left pseudolocales
(`en-XA` and `ar-XB`). Check navigation, long labels, dialogs, focus and captions
on phone and TV layouts. Do not replace real media names with translated labels.
The settings and library locale regression tests cover representative layouts;
a native speaker should review the actual wording before a language is released.

[Contributing](../CONTRIBUTING.md) · [Build instructions](building.md)
