#!/usr/bin/env python3
"""Build pinned official JD2 sources once, then reuse the verified compilation JARs."""
# SPDX-License-Identifier: AGPL-3.0-only
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
LOCK = PROJECT / 'ci/jd2-sources.json'
REQUIRED = ('Core.jar', 'JDownloader.jar', 'libs/JDUtils.jar', 'libs/JDGUI.jar',
            'libs/jna.jar', 'libs/jna_platform.jar')
MANIFEST = 'native-tray-sdk.json'


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint():
    return hashlib.sha256(LOCK.read_bytes() + Path(__file__).read_bytes()).hexdigest()


def verify(root):
    try:
        manifest = json.loads((root / MANIFEST).read_text())
        if manifest['fingerprint'] != fingerprint() or set(manifest['jars']) != set(REQUIRED):
            return False
        for relative in REQUIRED:
            path = root / relative
            if sha256(path) != manifest['jars'][relative]:
                return False
            with zipfile.ZipFile(path) as archive:
                if archive.testzip() is not None:
                    return False
        return True
    except (OSError, ValueError, KeyError, zipfile.BadZipFile):
        return False


def prepare(root, check_only=False):
    if verify(root):
        print(f'Using verified cached JD2 JARs: {root}', flush=True)
        return
    if check_only:
        raise RuntimeError('JD2 cache is missing, corrupt, or built from different inputs. Delete the Actions cache and retry.')
    if root.exists() and any(root.iterdir()):
        raise RuntimeError(f'Refusing to replace an invalid JD2 cache: {root}. Remove it and retry.')
    for command in ('svn', 'ant', 'java'):
        if not shutil.which(command):
            raise RuntimeError(f'{command} is required on a cache miss.')
    lock = json.loads(LOCK.read_text())
    if lock['schema'] != 1 or {item['directory'] for item in lock['projects']} != {
            'AppWorkUtils', 'JDBrowser', 'JDownloader', 'MyJDownloaderClient'}:
        raise RuntimeError('Invalid JD2 source lock.')
    root.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='jd2-source-', dir=root.parent) as temporary:
        work = Path(temporary)
        for item in lock['projects']:
            revision = item['revision']
            if not isinstance(revision, int) or revision <= 0:
                raise RuntimeError('Every SVN revision must be a positive integer.')
            print(f"Exporting {item['directory']} at r{revision}", flush=True)
            subprocess.run(['svn', 'export', '--non-interactive', '--quiet', '-r', str(revision),
                            f"{item['url']}@{revision}", str(work / item['directory'])], check=True)
        jd = work / 'JDownloader'
        # Exactly the standalone build documented by JDownloader; no updater or GUI is launched.
        subprocess.run(['ant', f'-Dbasedir={jd}', '-f', str(jd / 'build/newBuild/build_standalone.xml')],
                       cwd=work, check=True)
        dist = jd / 'standalone/dist'
        staging = work / 'sdk'
        for relative in REQUIRED:
            destination = staging / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(dist / relative, destination)
        java = subprocess.run(['java', '-version'], capture_output=True, text=True, check=True)
        (staging / MANIFEST).write_text(json.dumps({
            'fingerprint': fingerprint(), 'sources': lock,
            'java': java.stderr.strip() or java.stdout.strip(),
            'jars': {relative: sha256(staging / relative) for relative in REQUIRED},
        }, indent=2) + '\n')
        if not verify(staging):
            raise RuntimeError('JD2 build produced invalid compilation artifacts.')
        if root.exists():
            root.rmdir()  # Only the empty directory accepted above.
        staging.rename(root)
    print(f'Prepared pinned JD2 JARs: {root}', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=PROJECT / '.local-build/ci/jd2')
    parser.add_argument('--check', action='store_true', help='verify a restored cache without network access')
    args = parser.parse_args()
    try:
        prepare(args.directory.resolve(), args.check)
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
