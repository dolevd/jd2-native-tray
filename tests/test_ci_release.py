"""Exercise release guards and CLI arguments without contacting GitHub or publishing."""
# SPDX-License-Identifier: AGPL-3.0-only
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

PROJECT = Path(__file__).resolve().parents[1]
COMMIT = 'a' * 40


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='jdtray-release-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.assets = self.root / 'assets with spaces'
        self.assets.mkdir()
        (self.assets / 'NativeTray.jar').write_bytes(b'fixture extension')
        (self.assets / 'SourceBuild.json').write_text(json.dumps({'source_commit': COMMIT}))
        self.checksums()
        self.calls = self.root / 'calls.jsonl'
        gh = self.root / 'gh'
        gh.write_text('''#!/usr/bin/env python3
import json, os, sys
with open(os.environ['GH_TEST_CALLS'], 'a') as output:
    output.write(json.dumps(sys.argv[1:]) + '\\n')
if sys.argv[1] == 'api':
    print(os.environ.get('GH_TEST_TAGS', '[]'))
''')
        gh.chmod(0o755)
        self.environment = dict(os.environ, PATH=str(self.root) + ':' + os.environ['PATH'],
                                GH_TEST_CALLS=str(self.calls), GH_TOKEN='fixture',
                                GITHUB_REF='refs/heads/main', GITHUB_SHA=COMMIT,
                                GITHUB_REPOSITORY='example/native-tray', RELEASE_TAG='v0.1.0')

    def checksums(self):
        (self.assets / 'SHA256SUMS').write_text(''.join(
            f'{hashlib.sha256((self.assets / name).read_bytes()).hexdigest()}  {name}\n'
            for name in ('NativeTray.jar', 'SourceBuild.json')))

    def run_script(self, *args):
        return subprocess.run(['bash', str(PROJECT / 'scripts/ci/release.sh'), *args, str(self.assets)],
                              env=self.environment, capture_output=True, text=True)

    def published(self):
        return [] if not self.calls.exists() else [json.loads(line) for line in self.calls.read_text().splitlines()
                                                  if json.loads(line)[:2] == ['release', 'create']]

    def test_non_main_and_invalid_tags_fail_before_api_access(self):
        self.environment['GITHUB_REF'] = 'refs/heads/feature'
        self.assertNotEqual(0, self.run_script('--validate').returncode)
        self.assertFalse(self.calls.exists())
        self.environment['GITHUB_REF'] = 'refs/heads/main'
        self.environment['RELEASE_TAG'] = '--bad-tag'
        self.assertNotEqual(0, self.run_script('--validate').returncode)
        self.assertFalse(self.calls.exists())

    def test_validation_never_publishes(self):
        self.assertEqual(0, self.run_script('--validate').returncode)
        self.assertEqual([], self.published())

    def test_existing_tag_is_rejected(self):
        self.environment['GH_TEST_TAGS'] = '[{"ref":"refs/tags/v0.1.0"}]'
        self.assertNotEqual(0, self.run_script().returncode)
        self.assertEqual([], self.published())

    def test_damaged_artifact_is_rejected(self):
        (self.assets / 'NativeTray.jar').write_bytes(b'damaged')
        self.assertNotEqual(0, self.run_script().returncode)
        self.assertEqual([], self.published())

    def test_wrong_main_commit_is_rejected(self):
        (self.assets / 'SourceBuild.json').write_text(json.dumps({'source_commit': 'b' * 40}))
        self.checksums()
        self.assertNotEqual(0, self.run_script().returncode)
        self.assertEqual([], self.published())

    def test_release_uses_exact_commit_and_only_requested_prerelease_flag(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        arguments = self.published()[0]
        self.assertEqual(COMMIT, arguments[arguments.index('--target') + 1])
        self.assertEqual('example/native-tray', arguments[arguments.index('--repo') + 1])
        self.assertIn(str(self.assets / 'NativeTray.jar'), arguments)
        self.assertNotIn('--prerelease', arguments)
        self.environment['PRERELEASE'] = 'true'
        self.assertEqual(0, self.run_script().returncode)
        self.assertIn('--prerelease', self.published()[-1])


if __name__ == '__main__':
    unittest.main()
