# JD2 Native Tray

A JDownloader 2 application extension that exports a native StatusNotifierItem and dbusmenu on the session bus. Plasma renders the icon and popup menu. It runs in JD2's JVM; no external agent or service is needed, and no JD2 core JAR is patched.

## Install and test

Build the extension first using the [build instructions](#build) below. The built JAR is `target/NativeTray.jar`.

1. Quit JD2 with **File → Exit**. Closing the main window may only minimize it.
2. Run from this directory:

   ```sh
   python3 scripts/install.py --flatpak
   ```

   The installer copies the JAR into the existing Flatpak JD2 data directory, backs up the tray files, requests an extension rescan and grants just `org.kde.StatusNotifierWatcher` D-Bus talk access. It leaves Native Tray disabled initially. It does not alter SELinux policy.
3. Start JD2 normally. Open **Settings → Extensions → Native Tray**, then enable it using the checkbox in its settings panel. Depending on the JD2 settings layout, Native Tray may appear directly in the settings sidebar.
4. The first enable imports the old tray preferences and disables the AWT tray automatically. The panel status should say **Connected to the native desktop tray**. Change close/minimize behavior in **Native Tray** from now on.

Suggested checks:

- Double-click the native icon to hide/show JD2. If the old single-click preference was enabled, use one click. Right-click should open Plasma's native menu with JD2's usual actions.
- Set **When minimizing the main window → Minimize to Tray**, minimize, wait at least 20 seconds, then restore using the icon. Check that the old AWT icon has not returned and the window position/maximized state is preserved.
- Try **When closing**: Ask me, Hide to Tray, Minimize to Taskbar and Exit. Exit invokes JD2's normal shutdown action, including its usual confirmations.
- Check tooltip, grey icon, clipboard indicator, icon visibility while the window is open, and start-minimized after restarting JD2.
- If you use JD2's password protection, hide the window and verify that showing it requires the existing password. While locked, the native menu offers only **Unlock JDownloader**; unlocking opens the window.
- Change a number editor from the tray. The native menu opens JD2's existing editor widget in a dialog. Change menu customization and reopen the native menu to check the result.
- Disable Native Tray while its window is visible. It removes the native icon and restores the old tray if that tray was enabled when the replacement took over.

The GNOME/X11 fake-transparency workaround is unnecessary. Native pixmaps retain alpha at 16, 22, 24, 32, 48 and 64 pixels. A disabled tooltip removes the extension's detailed tooltip; a desktop may still show its own application label.

## Roll back

Disable Native Tray, quit JD2 using File → Exit, then run:

```sh
python3 scripts/install.py --uninstall
```

The latest backup lives in the JD2 directory under `.native-tray-backups/`. Rollback restores the previous tray configuration and any previous NativeTray.jar. If you installed it for the first time, rollback removes its JAR and configuration. It undoes the Flatpak override if no other override changes were made since installation; otherwise it preserves those changes and tells you so. After an upgrade, this command restores the previous extension version.

For a manual removal, quit JD2, remove `extensions/NativeTray.jar`, create `tmp/invalidextensions`, and restore or re-enable the built-in tray. Its config is `cfg/org.jdownloader.gui.jdtrayicon.TrayExtension.json`. The replacement config is `cfg/org.jdownloader.extensions.nativetray.NativeTrayExtension.json`. Neither file stores JD2's shared GUI password.

## Build

Requires Maven and JDK 17 or newer. Compile against the installed JD2 JARs:

```sh
./scripts/build.sh
# For another JD2 installation:
./scripts/build.sh /path/to/jdownloader
```

The build caches Maven dependencies under `.local-build/m2`. JD2 JARs copied to `.local-build/provided` are build inputs only; they are excluded from the distributable. The output bundles and relocates dbus-java 5.2.2, its Java Unix-domain-socket transport and SLF4J. The native transport needs no helper process or native binary. The optional XWayland focus-token bridge uses the JNA already supplied by JD2.

## Architecture and compatibility

- The public `AbstractExtension` lifecycle, normal config storage, JD Swing settings widgets, shared password keys and `MenuManagerTrayIcon` menu model form the JD2 adapter. JD2 APIs are not a versioned third-party SDK, so a future JD2 update could require rebuilding this small adapter.
- The transport exposes `org.kde.StatusNotifierItem`, `org.freedesktop.DBus.Properties`, and `com.canonical.dbusmenu`. Bus wire names survive Java package relocation. Explicit multi-value reply serialization avoids ambiguity in dbus-java's tuple introspection.
- Each enable owns a private connection and a name beneath `org.jdownloader.JDownloader.NativeTray`. Flatpak already permits ownership of that application namespace. Registration waits until both a watcher and a tray host exist. Owner changes trigger re-registration; periodic checks also cover tray-host disappearance while the watcher remains alive.
- Minimize/close handlers are attached only after registration. Hiding does not call `JDGui.setWindowToTray(true)`, which would restart JD2's legacy AWT recovery checker. Losing the bus or tray host restores a hidden window, or gives a protected window a minimized taskbar entry. With no native host, Hide to Tray falls back to taskbar minimization.
- GUI initialization, settings access, menu snapshots and command execution occur on the EDT. Connection setup, bus calls and signals use a separate daemon worker with the extension class loader. Disconnecting or disabling removes the native export and callbacks.
- Menu actions remain JD2's own customized actions, including enabled and checked state. Toggle commands execute through a Swing menu item to retain JD2's Action semantics. dbusmenu cannot embed arbitrary Swing components; number editors open the existing widget in a dialog.
- StatusNotifierItem has no click-count field. Consecutive Activate calls implement JD2's single/double-click preference. This is verified against Plasma's API; other hosts may have different input conventions.

## Verification

Automated checks cover ARGB byte order/transparency, grey rendering, click-pair behavior, menu recursion and enabled state, installer backups and rollback. Packaged-wire probes verify registration, properties, exact introspection, recursive dbusmenu replies, action dispatch, and watcher restart. Tests also ran with Plasma 6.7.5 and JD2's bundled Java 26 inside its Flatpak sandbox. A child-class-loader probe checks transport discovery as loaded by a plugin.

The JD2 runtime probe verifies extension metadata/configuration and renders the settings panel. It also converts JD2's default customized menu; GUI-dependent actions require the full app and are left for the in-app checks above. The download application is not launched by these probes, and no download commands are executed.

Re-run the basic checks:

```sh
mvn -Dmaven.repo.local=.local-build/m2 test
python3 tests/installer_check.py
```

Packaged bus checks (run in a normal terminal, outside an extra no-new-privileges sandbox):

```sh
dbus-run-session -- env JD_TRAY_PRIVATE_BUS=1 java -cp target/NativeTray.jar org.jdownloader.extensions.nativetray.ProtocolProbe
java -cp target/NativeTray.jar org.jdownloader.extensions.nativetray.ProtocolProbe --desktop
javac -d .local-build tests/ChildLoaderProbe.java
java -cp .local-build ChildLoaderProbe "$PWD/target/NativeTray.jar"
```

These desktop probes temporarily register a test icon and exit automatically. The private-bus probe rejects accidental execution on the desktop bus unless explicitly given `--desktop`. Running `dbus-run-session` inside Codex's restricted execution context caused two SELinux transition alerts during development; running it in the normal host context passed without policy changes. The extension itself connects to the existing session bus.

This is the first local test release. Protocol and loader checks have passed; interactive close/minimize/password flows still need the in-app checks above. If loading fails after a JD2 update, retain the backup and rebuild against the updated installed JARs. Native Tray diagnostics go to JD2's `NativeTrayExtension` log and appear in the settings status line.

## License

Extension sources are AGPL-3.0-only; see LICENSE. Third-party dependencies retain their own licenses. JD2 and its assets remain external runtime/build inputs and are not distributed with the extension. dbus-java and SLF4J are MIT licensed; see THIRD-PARTY-NOTICES.md.
