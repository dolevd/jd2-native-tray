"""Exercise install/upgrade rollback against dummy JD files, never the user's JD2."""
from pathlib import Path
import subprocess
import tempfile
import json
import os
project = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='jdtray-install-') as name:
    root = Path(name)
    (root/'Core.jar').touch(); (root/'JDownloader.jar').touch()
    (root/'cfg').mkdir()
    old = b'{"enabled":true,"onminimizeaction":"TO_TASKBAR"}\n'
    tray = root/'cfg/org.jdownloader.gui.jdtrayicon.TrayExtension.json'
    tray.write_bytes(old)
    environment = dict(os.environ, JD2_INSTALL_DIR=str(root))
    run = lambda *args: subprocess.run(['python3',str(project/'scripts/install.py'),*args],env=environment,check=True,capture_output=True,text=True)
    run('--dry-run')
    assert not (root/'.native-tray-backups').exists()
    run()
    assert (root/'extensions/NativeTray.jar').read_bytes() == (project/'jd2-adapter/target/NativeTray.jar').read_bytes()
    assert (root/'tmp/invalidextensions').exists()
    native = root/'cfg/org.jdownloader.extensions.nativetray.NativeTrayExtension.json'
    assert json.loads(native.read_text())['enabled'] is False
    tray.write_text('{"enabled":false}')
    # An explicit directory takes precedence over an inherited environment setting.
    environment['JD2_INSTALL_DIR'] = str(root/'does-not-exist')
    run(str(root), '--uninstall')
    assert tray.read_bytes() == old
    assert not native.exists() and not (root/'extensions/NativeTray.jar').exists()
    print('PASS: environment-selected install, explicit-directory precedence, dry run, rescan and exact tray rollback')
