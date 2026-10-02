"""Reject substituted SVN content before any downloaded build can execute."""
# SPDX-License-Identifier: AGPL-3.0-only
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

PROJECT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('prepare_jd2', PROJECT / 'scripts/ci/prepare_jd2.py')
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class SourceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='jdtray-source-integrity-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'approved'
        (self.source / 'build').mkdir(parents=True)
        (self.source / 'build/build.xml').write_text('<project/>\n')
        self.project = {'directory': 'Fixture', 'tree_sha256': prepare.tree_sha256(self.source)}

    def test_timestamps_do_not_change_content_pin(self):
        os.utime(self.source / 'build/build.xml', (1, 1))
        prepare.verify_sources(self.source, self.project)

    def test_modified_build_is_rejected(self):
        (self.source / 'build/build.xml').write_text('<project><exec executable="evil"/></project>')
        with self.assertRaisesRegex(RuntimeError, 'Source content mismatch'):
            prepare.verify_sources(self.source, self.project)

    def test_added_or_renamed_files_are_rejected(self):
        (self.source / 'build/build.xml').rename(self.source / 'build/injected.xml')
        with self.assertRaises(RuntimeError):
            prepare.verify_sources(self.source, self.project)
        (self.source / 'build/injected.xml').rename(self.source / 'build/build.xml')
        (self.source / 'extra.jar').write_bytes(b'injected bytecode')
        with self.assertRaises(RuntimeError):
            prepare.verify_sources(self.source, self.project)

    def test_changed_executable_flag_is_rejected(self):
        (self.source / 'build/build.xml').chmod(0o755)
        with self.assertRaises(RuntimeError):
            prepare.verify_sources(self.source, self.project)

    def test_symlinks_are_rejected(self):
        (self.source / 'escape').symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(RuntimeError, 'symlinks'):
            prepare.verify_sources(self.source, self.project)

    def test_substitution_in_last_export_never_runs_ant(self):
        lock = json.loads(prepare.LOCK.read_text())
        for item in lock['projects']:
            item['tree_sha256'] = self.project['tree_sha256']
        lock_path = self.root / 'lock.json'
        lock_path.write_text(json.dumps(lock))

        def export(command, **kwargs):
            self.assertEqual('svn', command[0], 'No Java or Ant may run before all exports are verified')
            self.assertIn('--ignore-externals', command)
            destination = Path(command[-1])
            shutil.copytree(self.source, destination)
            if destination.name == lock['projects'][-1]['directory']:
                (destination / 'build/build.xml').write_text('substituted build instructions')
            return subprocess.CompletedProcess(command, 0)

        with patch.object(prepare, 'LOCK', lock_path), patch.object(prepare.shutil, 'which', return_value='fixture'), \
                patch.object(prepare.subprocess, 'run', side_effect=export) as commands:
            with self.assertRaisesRegex(RuntimeError, 'refusing to execute'):
                prepare.prepare(self.root / 'sdk')
        self.assertEqual(4, commands.call_count)
        self.assertFalse((self.root / 'sdk').exists())

    def test_wrong_java_or_ant_version_is_rejected(self):
        pins = json.loads(prepare.TOOLCHAIN.read_text())
        cases = [
            ('different.vendor', pins['java']['version'], pins['ant']['version']),
            (pins['java']['vendor'], '17.0.20.1+2', pins['ant']['version']),
            (pins['java']['vendor'], pins['java']['version'], '1.10.16'),
        ]
        for vendor, java_version, ant_version in cases:
            with self.subTest(vendor=vendor, java=java_version, ant=ant_version):
                java = subprocess.CompletedProcess([], 0, '', f'    java.vendor = {vendor}\n    java.runtime.version = {java_version}\n')
                ant = subprocess.CompletedProcess([], 0, f'Apache Ant(TM) version {ant_version} compiled on some date\n', '')
                with patch.object(prepare.subprocess, 'run', side_effect=[java, ant]):
                    with self.assertRaisesRegex(RuntimeError, 'exact JDK and Ant'):
                        prepare.verify_toolchain()


if __name__ == '__main__':
    unittest.main()
