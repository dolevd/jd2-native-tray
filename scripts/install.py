#!/usr/bin/env python3
"""Install or undo Native Tray while JD2 is closed. No JD core JAR is edited."""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

PROJECT = Path(__file__).resolve().parents[1]
DEFAULT_ROOT = Path(os.environ.get('JD2_INSTALL_DIR') or
                    Path.home() / '.var/app/org.jdownloader.JDownloader/data/jdownloader')
APP_ID = 'org.jdownloader.JDownloader'
TRAY_CONFIGS = ['TrayExtension.json', 'org.jdownloader.gui.jdtrayicon.TrayExtension.json']
NATIVE_CONFIGS = ['NativeTrayExtension.json', 'org.jdownloader.extensions.nativetray.NativeTrayExtension.json']


def atomic_copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as temporary:
        staging = Path(temporary.name)
    try:
        shutil.copyfile(source, staging)
        os.replace(staging, destination)
    finally:
        staging.unlink(missing_ok=True)


def ensure_closed(root):
    for process in Path('/proc').iterdir():
        if not process.name.isdigit():
            continue
        try:
            cwd = (process / 'cwd').resolve(strict=True)
            cmd = (process / 'cmdline').read_bytes()
        except (OSError, RuntimeError):
            continue
        if cwd == root and b'JDownloader.jar' in cmd:
            raise RuntimeError('Quit JD2 using File → Exit before installing or uninstalling (closing its window may only minimize it).')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def install(root, use_flatpak, dry_run):
    artifact = PROJECT / 'jd2-adapter/target/NativeTray.jar'
    if not artifact.is_file():
        raise RuntimeError('Build jd2-adapter/target/NativeTray.jar first; see README.md.')
    ensure_closed(root)
    if dry_run:
        print(f'Would back up tray files and install {artifact} into {root}/extensions/NativeTray.jar')
        print('Would request an extension rescan; Native Tray starts disabled.')
        if use_flatpak:
            print('Would grant the Flatpak talk access to org.kde.StatusNotifierWatcher only.')
        return
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    backup = root / '.native-tray-backups' / stamp
    backup.mkdir(parents=True)
    files = ['extensions/NativeTray.jar'] + ['cfg/' + name for name in TRAY_CONFIGS + NATIVE_CONFIGS]
    manifest = {'files': {}, 'sha256': digest(artifact.read_bytes())}
    for relative in files:
        source = root / relative
        manifest['files'][relative] = source.is_file()
        if source.is_file():
            atomic_copy(source, backup / relative)
    override = Path.home() / '.local/share/flatpak/overrides' / APP_ID
    if use_flatpak:
        # Flatpak's per-user override file lives under XDG_DATA_HOME, not XDG_CONFIG_HOME.
        override = Path(os.environ.get('XDG_DATA_HOME', Path.home() / '.local/share')) / 'flatpak/overrides' / APP_ID
        original = override.read_bytes() if override.exists() else None
        manifest['flatpak'] = {'path': str(override), 'original': base64.b64encode(original).decode() if original is not None else None}
        subprocess.run(['flatpak', 'override', '--user', '--talk-name=org.kde.StatusNotifierWatcher', APP_ID], check=True)
        manifest['flatpak']['installed_sha256'] = digest(override.read_bytes())
    atomic_copy(artifact, root / 'extensions/NativeTray.jar')
    own = root / 'cfg/org.jdownloader.extensions.nativetray.NativeTrayExtension.json'
    if not any((root / 'cfg' / name).exists() for name in NATIVE_CONFIGS):
        own.parent.mkdir(parents=True, exist_ok=True)
        own.write_text('{"enabled":false,"freshinstall":false}\n')
    (root / 'tmp').mkdir(exist_ok=True)
    (root / 'tmp/invalidextensions').touch()
    (backup / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (root / '.native-tray-backups/latest').write_text(stamp + '\n')
    print(f'Installed {root}/extensions/NativeTray.jar')
    print(f'Backup: {backup}')
    print('Launch JD2, then enable Settings → Extensions → Native Tray. It imports your old tray settings and disables the old tray automatically.')


def uninstall(root, dry_run):
    ensure_closed(root)
    latest = root / '.native-tray-backups/latest'
    if not latest.exists():
        raise RuntimeError('No installer backup found. Disable Native Tray, quit JD2, remove extensions/NativeTray.jar, and create tmp/invalidextensions.')
    name = latest.read_text().strip()
    if Path(name).name != name:
        raise RuntimeError('Invalid backup identifier')
    backup = root / '.native-tray-backups' / name
    manifest = json.loads((backup / 'manifest.json').read_text())
    if dry_run:
        print(f'Would restore tray files from {backup}, and undo the permission change if it is unchanged since installation.')
        return
    for relative, existed in manifest['files'].items():
        if relative not in ['extensions/NativeTray.jar'] + ['cfg/' + x for x in TRAY_CONFIGS + NATIVE_CONFIGS]:
            raise RuntimeError('Unexpected backup path')
        target = root / relative
        if existed:
            atomic_copy(backup / relative, target)
        elif relative == 'extensions/NativeTray.jar' or Path(relative).name in NATIVE_CONFIGS:
            target.unlink(missing_ok=True)
    if 'flatpak' in manifest:
        saved = manifest['flatpak']
        override = Path(saved['path'])
        if override.exists() and digest(override.read_bytes()) == saved['installed_sha256']:
            if saved['original'] is None:
                override.unlink()
            else:
                override.write_bytes(base64.b64decode(saved['original']))
        else:
            print('Flatpak overrides changed after installation; preserved them. Remove the watcher permission manually if desired.')
    (root / 'tmp').mkdir(exist_ok=True)
    (root / 'tmp/invalidextensions').touch()
    print('Restored the previous tray files. Restart JD2.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jd_root', nargs='?', type=Path, default=DEFAULT_ROOT,
                        help='JD2 directory (default: JD2_INSTALL_DIR, or the standard Flatpak directory)')
    parser.add_argument('--flatpak', action='store_true', help='grant narrow native tray access to the JD2 Flatpak')
    parser.add_argument('--uninstall', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    root = args.jd_root.expanduser().resolve()
    if not (root / 'Core.jar').is_file() or not (root / 'JDownloader.jar').is_file():
        parser.error(f'Not a JD2 installation: {root}')
    try:
        if args.uninstall:
            uninstall(root, args.dry_run)
        else:
            install(root, args.flatpak, args.dry_run)
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
