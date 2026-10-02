// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.awt.Window;
import java.nio.charset.StandardCharsets;
import com.sun.jna.*;
import org.appwork.utils.logging2.LogSource;

/** Supplies Plasma's activation token to the XWayland Swing window, when offered. */
final class X11Activation {
    interface X11 extends Library {
        Pointer XOpenDisplay(String name);
        NativeLong XInternAtom(Pointer display, String name, boolean onlyIfExists);
        int XChangeProperty(Pointer display, NativeLong window, NativeLong property, NativeLong type,
            int format, int mode, byte[] data, int length);
        int XFlush(Pointer display);
        int XCloseDisplay(Pointer display);
    }
    static void apply(Window window, String token, LogSource logger) {
        if (!window.isDisplayable() || token.isEmpty()) return;
        try {
            X11 x = Native.load("X11", X11.class);
            Pointer display = x.XOpenDisplay(null);
            if (display == null) return;
            try {
                byte[] data = token.getBytes(StandardCharsets.UTF_8);
                x.XChangeProperty(display, new NativeLong(Native.getComponentID(window)),
                    x.XInternAtom(display, "_NET_STARTUP_ID", false), x.XInternAtom(display, "UTF8_STRING", false), 8, 0, data, data.length);
                x.XFlush(display);
            } finally { x.XCloseDisplay(display); }
        } catch (LinkageError | RuntimeException e) { logger.warning("Window activation token could not be applied: " + e.getMessage()); }
    }
}
