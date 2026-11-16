#!/usr/bin/env python3
"""Collect redistribution notices for the libraries linked into the IAMF decoder."""
import pathlib, sys

root = pathlib.Path(__file__).resolve().parent.parent
source, external = map(pathlib.Path, sys.argv[1:])
parts = ['IAMF decoder redistribution notices\n\nMedia3 wrapper: Apache 2.0 (see Apache-2.0.txt).\n'
         'Ember modifications: AAC build dependencies excluded; JNI handle, buffer and lifecycle validation.\n']
def add(name, file):
    parts.append('\n' + name + '\n\n' + file.read_text())
for name in ['LICENSE', 'PATENTS']:
    add('IAMF Tools — ' + name, source/name)
for directory, names in {
    'abseil-cpp+': ['LICENSE'],
    '+_repo_rules+com_google_obr': ['LICENSE', 'PATENTS'],
    '+_repo_rules+flac': ['COPYING.Xiph'],
    '+_repo_rules+libopus': ['COPYING'],
    'eigen+': ['COPYING.README', 'COPYING.MPL2', 'COPYING.BSD', 'COPYING.MINPACK', 'COPYING.LGPL'],
}.items():
    for name in names:
        add(directory + ' — ' + name, external/directory/name)
for name in ['pffft.c', 'fftpack.c']:
    text = (external/'+_repo_rules+pffft'/name).read_text()
    parts.append('\nPFFFT / FFTPACK — ' + name + '\n\n' + text[:text.index('*/')+2])
(root/'app/src/main/assets/licenses/IAMF-NOTICES.txt').write_text('\n'.join(parts))
