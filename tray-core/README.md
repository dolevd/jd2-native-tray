# Reusable native tray core

A Java 17 StatusNotifierItem and dbusmenu implementation. This module depends on dbus-java and SLF4J API, with no JDownloader, AppWork or JNA dependency. It uses Java Unix-domain sockets and a desktop session bus. The JD2 adapter's KDE support policy is not part of this library.

Logging is supplied by the consuming application; the core does not impose a logging backend.

Build it from the repository root without installing JD2:

```sh
mvn -f tray-core/pom.xml -Dmaven.repo.local="$PWD/.local-build/m2" package
```

To consume it from another Maven project, install the parent POM and core artifact (both commands run from the repository root):

```sh
mvn -N -Dmaven.repo.local="$PWD/.local-build/m2" install
mvn -f tray-core/pom.xml -Dmaven.repo.local="$PWD/.local-build/m2" install
```

Then use that same local repository and add:

```xml
<dependency>
  <groupId>org.jdownloader.extensions.nativetray</groupId>
  <artifactId>tray-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

The Java API lives in `org.jdownloader.extensions.nativetray.core`:

- `TrayIdentity.create(namespace, itemId, title)` supplies application identity and a unique bus name. Choose a namespace the application is permitted to own. Inside Flatpak this is normally `FLATPAK_ID` or a child namespace; grant talk access to `org.kde.StatusNotifierWatcher` separately.
- `SniService` owns one connection and retries registration when the watcher/host returns. Construct it with identity, availability/diagnostic callbacks, primary/secondary activation and activation-token callbacks, and a menu supplier. Call `start()` once and `close()` when finished.
- `update(pixmaps, tooltip, passive)` publishes icon/status snapshots. A null pixmap list keeps the current icon. `IconPixels.sizes(image, grey)` generates transparent ARGB pixmaps; application-specific badges belong to the caller.
- `MenuNode` describes immutable menu snapshots and callbacks. Its properties follow dbusmenu and use dbus-java `Variant` values. Call `setMenu()` for a new snapshot; the supplier refreshes it when the host opens the menu.
- `ActivationPolicy` optionally interprets consecutive protocol activations as double clicks. Desktop hosts choose their own input conventions.

Callbacks can run on D-Bus or worker threads. Dispatch application/UI work to your own event loop, and keep callbacks short. Availability means a watcher has accepted registration and reports a tray host; it does not guarantee every host supports pixmap icons or every menu feature. The caller decides supported desktops and window fallback behavior. Always keep a way to reopen a hidden window when availability becomes false.

The [standalone example](examples/ReusableTrayDemo.java) creates a tray for a different application without loading any JD2 classes. It supplies its own name, title, menu, and callbacks. Compile it against the core JAR and dbus-java; run it with the core JAR and its Maven runtime dependencies. Pass an allowed bus namespace as its first argument when running inside a sandbox.

This module uses the repository's AGPL-3.0-only license. Its dependencies retain their own licenses; see the root third-party notices.
