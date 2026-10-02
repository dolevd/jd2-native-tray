// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray.core;

import java.util.Objects;
import java.util.UUID;

/** Application identity supplied by the caller; no desktop or application policy is imposed. */
public record TrayIdentity(String busName, String itemId, String title) {
    public TrayIdentity {
        Objects.requireNonNull(busName); Objects.requireNonNull(itemId); Objects.requireNonNull(title);
        if (busName.length() > 255 || !busName.matches("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*)+"))
            throw new IllegalArgumentException("A valid well-known D-Bus name is required");
        if (itemId.isBlank() || title.isBlank()) throw new IllegalArgumentException("Tray ID and title must not be blank");
    }

    /** Unique per tray instance, beneath a namespace the application is permitted to own. */
    public static TrayIdentity create(String namespace, String itemId, String title) {
        return new TrayIdentity(namespace + ".p" + ProcessHandle.current().pid() + ".i"
            + UUID.randomUUID().toString().replace("-", ""), itemId, title);
    }
}
