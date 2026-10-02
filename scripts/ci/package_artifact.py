#!/usr/bin/env python3
"""Prepare the extension, checksums and source-build provenance for CI or a release."""
# SPDX-License-Identifier: AGPL-3.0-only
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

PROJECT = Path(__file__).resolve().parents[2]


def main():
    output = PROJECT / '.local-build/ci/artifact'
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(PROJECT / 'jd2-adapter/target/NativeTray.jar', output / 'NativeTray.jar')
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=PROJECT, text=True).strip()
    sdk = json.loads((PROJECT / '.local-build/ci/jd2/native-tray-sdk.json').read_text())
    (output / 'SourceBuild.json').write_text(json.dumps({
        'source_commit': commit, 'jd2_sdk': sdk,
    }, indent=2) + '\n')
    checksums = []
    for name in ('NativeTray.jar', 'SourceBuild.json'):
        checksums.append(f'{hashlib.sha256((output / name).read_bytes()).hexdigest()}  {name}')
    (output / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
    print(f'Prepared CI artifact from {commit}: {output}')


if __name__ == '__main__':
    main()
