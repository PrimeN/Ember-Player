#!/usr/bin/env python3
"""Merge device-collected phone/TV profiles into Android's release profile inputs."""
from pathlib import Path
import argparse


def merge(files):
    rules = {}
    for file in files:
        for raw in file.read_text().splitlines():
            line = raw.strip()
            if not line or line.startswith('#'):
                continue
            index = 0
            while index < len(line) and line[index] in 'HSP':
                index += 1
            body = line[index:]
            if not body.startswith(('L', '[')) or any(c.isspace() for c in body):
                raise ValueError(f'Invalid ART profile rule in {file.name}')
            if body.startswith(('Lapp/ember/tv/player/ProfileFixtures', 'Lapp/ember/tv/player/ProfileMedia')):
                continue
            rules.setdefault(body, set()).update(line[:index])
    if not any(body.startswith('Lapp/ember/') for body in rules):
        raise ValueError('The collected profile has no app code.')
    return ''.join(''.join(flag for flag in 'HSP' if flag in rules[body]) + body + '\n' for body in sorted(rules))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('captures', type=Path)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'app/src/main')
    args = parser.parse_args()
    for kind in ('baseline', 'startup'):
        files = list(args.captures.rglob(f'*-{kind}-prof.txt'))
        if not files:
            raise ValueError(f'No generated {kind} profiles found.')
        result = merge(files)
        args.output.mkdir(parents=True, exist_ok=True)
        target = args.output / f'{kind}-prof.txt'
        target.write_text(result)
        print(f'Wrote {target.name}: {len(result.splitlines())} rules from {len(files)} captures.')


if __name__ == '__main__':
    main()
