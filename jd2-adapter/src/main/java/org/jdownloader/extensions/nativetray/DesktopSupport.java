// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** JD2 support policy. The reusable protocol module has no desktop restrictions. */
public final class DesktopSupport {
    private DesktopSupport() { }
    public record Result(boolean supported, String message) { }

    public static Result current() {
        Map<String, String> env = System.getenv();
        String metadata = null;
        boolean flatpak = env.containsKey("FLATPAK_ID") || Files.exists(Path.of("/.flatpak-info"));
        if (flatpak) try { metadata = Files.readString(Path.of("/.flatpak-info")); } catch (IOException ignored) { }
        return evaluate(System.getProperty("os.name", ""), GraphicsEnvironment.isHeadless(), env, flatpak, metadata);
    }

    static Result evaluate(String os, boolean headless, Map<String, String> env, boolean flatpak, String metadata) {
        if (!os.toLowerCase(Locale.ROOT).startsWith("linux"))
            return unsupported("Native Tray requires Linux with KDE Plasma. This operating system is unsupported.");
        if (headless) return unsupported("Native Tray requires a graphical KDE Plasma session; this process is headless.");
        String desktop = env.getOrDefault("XDG_CURRENT_DESKTOP", "").trim();
        boolean kde;
        if (!desktop.isEmpty()) {
            kde = Arrays.stream(desktop.split(":")).anyMatch(token -> token.trim().equalsIgnoreCase("KDE"));
        } else {
            // Flatpak normally inherits these variables; never identify Plasma just from a watcher name.
            String session = env.getOrDefault("XDG_SESSION_DESKTOP", env.getOrDefault("DESKTOP_SESSION", ""));
            kde = session.isBlank() ? "true".equalsIgnoreCase(env.get("KDE_FULL_SESSION"))
                : Set.of("kde", "plasma", "plasmawayland", "plasmax11").contains(session.toLowerCase(Locale.ROOT));
        }
        if (!kde) return unsupported(desktop.isEmpty()
            ? "Cannot identify a KDE Plasma session. Start JDownloader from your Plasma desktop. Native Tray remains disabled."
            : "Native Tray currently supports KDE Plasma only. Your desktop (" + desktop + ") is unsupported.");
        if (flatpak && metadata == null)
            return unsupported("Cannot verify this Flatpak's tray permissions. Restart JDownloader after installing with scripts/install.py --flatpak.");
        if (flatpak && !permitsWatcher(metadata))
            return unsupported("This Flatpak cannot access the native tray. Quit JDownloader and run scripts/install.py --flatpak, then restart it.");
        return new Result(true, "KDE Plasma detected. The native tray must also be accessible on the session bus.");
    }

    private static Result unsupported(String message) { return new Result(false, message); }

    static boolean permitsWatcher(String metadata) {
        String section = "", permission = "";
        int specificity = -1;
        for (String raw : metadata.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("[") && line.endsWith("]")) { section = line; continue; }
            int equal = line.indexOf('=');
            if (equal < 0) continue;
            String key = line.substring(0, equal).trim(), value = line.substring(equal + 1).trim();
            if (section.equals("[Context]") && key.equals("sockets")
                    && Arrays.asList(value.split(";")).contains("session-bus")) return true;
            if (section.equals("[Instance]") && key.equals("session-bus-proxy") && value.equals("false")) return true;
            if (!section.equals("[Session Bus Policy]")) continue;
            String watcher = "org.kde.StatusNotifierWatcher";
            boolean matches = key.equals(watcher) || (key.endsWith(".*") && watcher.startsWith(key.substring(0, key.length() - 1)));
            if (matches && key.length() >= specificity) { specificity = key.length(); permission = value; }
        }
        return permission.equals("talk") || permission.equals("own");
    }
}
