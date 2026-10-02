// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.awt.*;
import java.awt.image.BufferedImage;

/** JD-specific clipboard badge; icon serialization belongs to tray-core. */
final class JDTrayIcons {
    private JDTrayIcons() { }
    static BufferedImage withClipboardBadge(BufferedImage logo, boolean clipboardOff) {
        if (!clipboardOff) return logo;
        int size = logo.getWidth();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.drawImage(logo, 0, 0, null);
            g.setColor(new Color(205,55,55)); g.fillOval(size/2,size/2,size/2,size/2);
            g.setColor(Color.WHITE); g.fillRect(size*5/8,size*3/4,Math.max(2,size/4),Math.max(1,size/12));
        } finally { g.dispose(); }
        return image;
    }
}
