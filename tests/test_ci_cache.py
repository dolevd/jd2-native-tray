"""Check offline cache reuse and rejection of stale or damaged JD2 artifacts."""
# SPDX-License-Identifier: AGPL-3.0-only
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
JARS = ('Core.jar', 'JDownloader.jar', 'libs/JDUtils.jar', 'libs/JDGUI.jar',
        'libs/jna.jar', 'libs/jna_platform.jar')


class CacheTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='jdtray-ci-cache-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.script = self.root / 'scripts/ci/prepare_jd2.py'
        self.script.parent.mkdir(parents=True)
        shutil.copyfile(PROJECT / 'scripts/ci/prepare_jd2.py', self.script)
        self.lock = self.root / 'ci/jd2-sources.json'
        self.lock.parent.mkdir()
        shutil.copyfile(PROJECT / 'ci/jd2-sources.json', self.lock)
        self.toolchain = self.lock.with_name('jd2-toolchain.json')
        shutil.copyfile(PROJECT / 'ci/jd2-toolchain.json', self.toolchain)
        self.sdk = self.root / '.local-build/ci/jd2'
        for relative in JARS:
            path = self.sdk / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('fixture.txt', relative)
        manifest = {
            'fingerprint': hashlib.sha256(self.lock.read_bytes() + self.toolchain.read_bytes() + self.script.read_bytes()).hexdigest(),
            'toolchain': json.loads(self.toolchain.read_text()),
            'jars': {name: hashlib.sha256((self.sdk / name).read_bytes()).hexdigest() for name in JARS},
        }
        (self.sdk / 'native-tray-sdk.json').write_text(json.dumps(manifest))

    def run_script(self, *args):
        # A hit must work even when Java, SVN and Ant cannot be found.
        return subprocess.run([sys.executable, str(self.script), *args],
                              env=dict(os.environ, PATH=''), capture_output=True, text=True)

    def test_hit_needs_no_build_tools(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Using verified cached', result.stdout)

    def test_pin_change_rejects_previous_cache(self):
        self.lock.write_text(self.lock.read_text() + '\n')
        result = self.run_script('--check')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('different inputs', result.stderr)

    def test_tampered_jar_is_not_reused_or_replaced(self):
        path = self.sdk / 'Core.jar'
        path.write_bytes(b'damaged artifact')
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('invalid JD2 cache', result.stderr)
        self.assertEqual(b'damaged artifact', path.read_bytes())

    def test_java_patch_change_rejects_previous_cache(self):
        pins = json.loads(self.toolchain.read_text())
        pins['java']['version'] = '17.0.20.1+2'
        self.toolchain.write_text(json.dumps(pins))
        self.assertNotEqual(0, self.run_script('--check').returncode)

    def test_ant_change_rejects_previous_cache(self):
        pins = json.loads(self.toolchain.read_text())
        pins['ant']['version'] = '1.10.16'
        self.toolchain.write_text(json.dumps(pins))
        self.assertNotEqual(0, self.run_script('--check').returncode)

    def test_missing_jar_fails_verification(self):
        (self.sdk / 'libs/JDGUI.jar').unlink()
        self.assertNotEqual(0, self.run_script('--check').returncode)


if __name__ == '__main__':
    unittest.main()
