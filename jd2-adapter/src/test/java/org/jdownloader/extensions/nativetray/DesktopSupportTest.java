// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopSupportTest {
    private static final Map<String, String> KDE = Map.of("XDG_CURRENT_DESKTOP", "KDE");
    private static final String TALK = "[Instance]\nsession-bus-proxy=true\n[Session Bus Policy]\norg.kde.StatusNotifierWatcher=talk\n";

    @Test void supportsPlasmaWithoutRequiringWaylandOrHostProcesses() {
        assertTrue(DesktopSupport.evaluate("Linux", false, KDE, false, null).supported());
        assertTrue(DesktopSupport.evaluate("Linux", false, Map.of("XDG_CURRENT_DESKTOP", "Vendor:KDE"), true, TALK).supported());
        assertTrue(DesktopSupport.evaluate("Linux", false, Map.of("KDE_FULL_SESSION", "true"), true, TALK).supported());
        assertTrue(DesktopSupport.evaluate("Linux", false, Map.of("XDG_SESSION_DESKTOP", "plasmawayland"), false, null).supported());
    }

    @Test void rejectsOtherDesktopsEvenWithStaleKdeFallbackVariables() {
        var result = DesktopSupport.evaluate("Linux", false, Map.of("XDG_CURRENT_DESKTOP", "GNOME", "KDE_FULL_SESSION", "true"), false, null);
        assertFalse(result.supported()); assertTrue(result.message().contains("GNOME"));
        assertFalse(DesktopSupport.evaluate("Linux", false, Map.of("XDG_CURRENT_DESKTOP", "NotKDE"), false, null).supported());
        assertFalse(DesktopSupport.evaluate("Linux", false, Map.of("XDG_SESSION_DESKTOP", "GNOME", "KDE_FULL_SESSION", "true"), false, null).supported());
        assertFalse(DesktopSupport.evaluate("Linux", false, Map.of(), true, TALK).supported());
        assertFalse(DesktopSupport.evaluate("Windows", false, KDE, false, null).supported());
        assertFalse(DesktopSupport.evaluate("Linux", true, KDE, false, null).supported());
    }

    @Test void checksEffectiveFlatpakPolicyAndExplainsHowToRepairIt() {
        assertTrue(DesktopSupport.evaluate("Linux", false, KDE, true, TALK).supported());
        var missing = DesktopSupport.evaluate("Linux", false, KDE, true, "[Session Bus Policy]\n");
        assertFalse(missing.supported()); assertTrue(missing.message().contains("--flatpak"));
        assertFalse(DesktopSupport.evaluate("Linux", false, KDE, true, null).supported());
        assertFalse(DesktopSupport.permitsWatcher(TALK.replace("=talk", "=see")));
        assertTrue(DesktopSupport.permitsWatcher(TALK.replace("=talk", "=own")));
        assertTrue(DesktopSupport.permitsWatcher("[Session Bus Policy]\norg.kde.*=talk\n"));
        assertFalse(DesktopSupport.permitsWatcher("[Session Bus Policy]\norg.kde.*=talk\norg.kde.StatusNotifierWatcher=none\n"));
        assertFalse(DesktopSupport.permitsWatcher("[Environment]\norg.kde.StatusNotifierWatcher=talk\n"));
        assertTrue(DesktopSupport.permitsWatcher("[Context]\nsockets=x11;session-bus;\n"));
    }
}
