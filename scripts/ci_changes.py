"""Skip Java only for known documentation paths; uncertainty runs verification."""
import os
from pathlib import PurePosixPath
import subprocess


def docs_only(paths):
    extensions = {'.md', '.doc', '.docx', '.pdf', '.txt', '.png', '.jpg', '.svg'}
    return bool(paths) and all(
        PurePosixPath(path).suffix.lower() in extensions
        and (path.startswith('docs/') or '/' not in path)
        for path in paths
    )


def main():
    base, head = os.environ.get('BASE_SHA', ''), os.environ.get('HEAD_SHA', '')
    skip = False
    if base and head and set(base) != {'0'}:
        result = subprocess.run(
            ['git', 'diff', '--name-only', '--no-renames', '-z', base, head, '--'],
            capture_output=True, check=False,
        )
        if result.returncode == 0:
            skip = docs_only(result.stdout.decode().rstrip('\0').split('\0'))
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        output.write('verify=' + ('false' if skip else 'true') + '\n')
    print('Documentation only: Java skipped.' if skip else 'Java verification required.')


if __name__ == '__main__':
    main()
