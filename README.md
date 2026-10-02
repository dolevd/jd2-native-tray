# JD2 Native Tray

A replacement tray icon extension for JDownloader 2 that integrates with KDE Plasma on Wayland. It also supports Plasma on X11.

JD2's built-in tray uses Java AWT's legacy X11 tray implementation. In Plasma, the icon can appear but ignore clicks and fail to open its menu ([KDE bug #498824, including JD2](https://bugs.kde.org/show_bug.cgi?id=498824)). It can also have an opaque background instead of transparency ([Java bug JDK-6453521](https://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521)).

This project replaces it with a normal JD2 extension using the standard **StatusNotifierItem and dbusmenu protocols over D-Bus**. Plasma renders the transparent icon and native menu. The extension runs inside JD2, preserves its tray actions and settings, and needs no external service or changes to JD2's core.

## Installation

Requires Linux with KDE Plasma and Java 17 or newer. Unsupported environments are rejected with an explanation in the extension's settings. Build the JAR using the [instructions below](#build), then quit JD2 using **File → Exit** before installing.

### Install script (recommended)

For the standard Flatpak installation:

```sh
python3 scripts/install.py --flatpak
```

The installer backs up existing tray files, copies the JAR, requests an extension rescan and grants the Flatpak permission needed for the native tray. For a non-Flatpak installation:

```sh
JD2_INSTALL_DIR=/absolute/path/to/jdownloader python3 scripts/install.py
```

Add `--flatpak` when using a Flatpak installation at a custom location. The installer also accepts the directory as a positional argument.

Restart JD2 and enable **Settings → Extensions → Native Tray**. On first enable, it imports the built-in tray preferences and disables that tray automatically. Configure minimize and close behavior in **Native Tray** from then on.

To undo a scripted installation, quit JD2 and run `python3 scripts/install.py --uninstall`, using the same installation directory.

### Manual installation

Set `JD2_INSTALL_DIR` to the folder containing `JDownloader.jar` and `Core.jar`:

| Installation | Directory |
| --- | --- |
| Standard Flatpak | `~/.var/app/org.jdownloader.JDownloader/data/jdownloader` |
| Non-Flatpak | Your chosen installation folder; find it under [Help → About JDownloader](https://support.jdownloader.org/en/knowledgebase/article/locate-find-jdownloader-installation-directory). There is no universal path. |

With JD2 closed, copy the JAR into `${JD2_INSTALL_DIR}/extensions/` and request a rescan. Replace the example's Flatpak path if using another installation:

```sh
export JD2_INSTALL_DIR="$HOME/.var/app/org.jdownloader.JDownloader/data/jdownloader"
mkdir -p "$JD2_INSTALL_DIR/extensions" "$JD2_INSTALL_DIR/tmp"
cp jd2-adapter/target/NativeTray.jar "$JD2_INSTALL_DIR/extensions/NativeTray.jar"
touch "$JD2_INSTALL_DIR/tmp/invalidextensions"
```

For Flatpak, also grant native tray access:

```sh
flatpak override --user --talk-name=org.kde.StatusNotifierWatcher org.jdownloader.JDownloader
```

Restart JD2 and enable Native Tray as above.

## Reusable library and JD2 adapter

This Maven project has two modules:

| Module | Responsibility | Output |
| --- | --- | --- |
| `tray-core` | Reusable StatusNotifierItem/dbusmenu implementation, transparent icons and bus recovery. No JD2, AppWork or JNA dependency, or KDE restriction. | `tray-core/target/tray-core-0.1.0.jar` |
| `jd2-adapter` | JD2 settings, menu actions, window behavior and supported-desktop checks. | `jd2-adapter/target/NativeTray.jar` |

The installable extension bundles the core and its D-Bus dependencies. Other applications can use the ordinary core library; see its [API and example](tray-core/README.md).

## Build

Requires Maven, JDK 17 or newer, and an existing JD2 installation. JD2's extension API JARs are not available on Maven Central, so Maven reads them from the installed application without bundling them. Other dependencies come from Maven Central.

From the repository root, build against the standard Flatpak directory:

```sh
mvn package
```

To use another installation, set its absolute path:

```sh
JD2_INSTALL_DIR=/absolute/path/to/jdownloader mvn package
```

The installable output is `jd2-adapter/target/NativeTray.jar`. The existing `scripts/build.sh` remains a shortcut and honors the same environment variable.

To build only the reusable library, without JD2:

```sh
mvn -f tray-core/pom.xml package
```

### Tests

`mvn package` runs the unit tests. To rerun them and check the installer using a temporary dummy installation:

```sh
mvn test
python3 tests/installer_check.py
```

Use the same `JD2_INSTALL_DIR` override for Maven tests if needed. The installer check requires the packaged JAR and does not change your JD2 installation.

## License

[AGPL-3.0-only](LICENSE). Dependencies retain their own licenses; see [third-party notices](THIRD-PARTY-NOTICES.md).
