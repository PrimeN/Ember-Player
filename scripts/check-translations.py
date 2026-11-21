#!/usr/bin/env python3
"""Check translated resources against English keys, format arguments and plural forms."""
from pathlib import Path
from collections import Counter
import re
import sys
import xml.etree.ElementTree as ET

FORMAT = re.compile(r'%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([tT]?[a-zA-Z%])')


def arguments(text):
    result = Counter()
    implicit = 0
    for match in FORMAT.finditer(text):
        position, kind = match.groups()
        if kind in ('%', 'n'):
            continue
        if position is None:
            implicit += 1
            position = str(implicit)
        result[(int(position), kind)] += 1
    return result


def resources(directory):
    found = {}
    for path in sorted(directory.glob('*.xml')):
        for element in ET.parse(path).getroot():
            if element.tag not in ('string', 'plurals', 'string-array'):
                continue
            key = (element.tag, element.attrib['name'])
            if key in found:
                raise ValueError(f'{directory.name}: duplicate {key[1]}')
            found[key] = element
    return found


def check(root):
    default = resources(root / 'values')
    errors = []
    for directory in sorted(root.glob('values-*')):
        # Resource configuration folders are not language packs.
        if not re.match(r'^values-(?:[a-z]{2,3}(?:-|$)|b\+)', directory.name):
            continue
        for key, translated in resources(directory).items():
            label = f'{directory.name}/{key[1]}'
            source = default.get(key)
            if source is None:
                errors.append(f'{label}: no English resource')
                continue
            if source.get('translatable') == 'false':
                errors.append(f'{label}: resource must not be translated')
                continue
            if key[0] == 'plurals':
                quantities = [item.get('quantity') for item in translated]
                if 'other' not in quantities or len(quantities) != len(set(quantities)):
                    errors.append(f'{label}: plurals need one other form and unique quantities')
                source_items = {item.get('quantity'): item for item in source}
                pairs = [(source_items.get(item.get('quantity'), source_items['other']), item) for item in translated]
            elif key[0] == 'string-array':
                if len(source) != len(translated):
                    errors.append(f'{label}: array length differs from English')
                pairs = list(zip(source, translated))
            else:
                pairs = [(source, translated)]
            if source.get('formatted') != 'false' or any(m.group(1) for m in FORMAT.finditer(''.join(source.itertext()))):
                for original, target in pairs:
                    if arguments(''.join(original.itertext())) != arguments(''.join(target.itertext())):
                        errors.append(f'{label}: format arguments differ from English')
    return errors


if __name__ == '__main__':
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / 'app/src/main/res'
    errors = check(root)
    if errors:
        raise SystemExit('\n'.join(errors))
    print('Translation resource keys, arguments and plural forms are valid.')
