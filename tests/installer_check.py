"""Exercise install/upgrade rollback against dummy JD files, never the user's JD2."""
from pathlib import Path
import subprocess
import tempfile
import json
project = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='jdtray-install-') as name:
    root = Path(name)
    (root/'Core.jar').touch(); (root/'JDownloader.jar').touch()
    (root/'cfg').mkdir()
    old = b'{"enabled":true,"onminimizeaction":"TO_TASKBAR"}\n'
    tray = root/'cfg/org.jdownloader.gui.jdtrayicon.TrayExtension.json'
    tray.write_bytes(old)
    run = lambda *args: subprocess.run(['python3',str(project/'scripts/install.py'),str(root),*args],check=True,capture_output=True,text=True)
    run()
    assert (root/'extensions/NativeTray.jar').read_bytes() == (project/'jd2-adapter/target/NativeTray.jar').read_bytes()
    assert (root/'tmp/invalidextensions').exists()
    native = root/'cfg/org.jdownloader.extensions.nativetray.NativeTrayExtension.json'
    assert json.loads(native.read_text())['enabled'] is False
    tray.write_text('{"enabled":false}')
    run('--uninstall')
    assert tray.read_bytes() == old
    assert not native.exists() and not (root/'extensions/NativeTray.jar').exists()
    print('PASS: install, disabled initial state, rescan marker and exact tray rollback')
