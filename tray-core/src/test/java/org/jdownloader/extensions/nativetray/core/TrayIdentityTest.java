// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrayIdentityTest {
    @Test void callerIdentityReplacesApplicationSpecificPropertiesAndAllowsMultipleInstances() {
        var one = TrayIdentity.create("org.example.Demo", "demo", "A different application");
        var two = TrayIdentity.create("org.example.Demo", "demo", "A different application");
        assertNotEquals(one.busName(), two.busName());
        try (var service = new SniService(one, ignored -> {}, ignored -> {}, ignored -> {}, ignored -> {}, () -> MenuNode.root(List.of()))) {
            var properties = service.new ItemObject().GetAll(SniService.ITEM_IFACE);
            assertEquals("demo", properties.get("Id").getValue());
            assertEquals("A different application", properties.get("Title").getValue());
            assertEquals("A different application", ((Protocols.ToolTip)properties.get("ToolTip").getValue()).title);
            assertEquals(one.busName(), service.busName);
        }
    }

    @Test void validatesBusNamesBeforeConnecting() {
        assertThrows(IllegalArgumentException.class, () -> TrayIdentity.create("bad namespace", "demo", "Demo"));
        assertThrows(IllegalArgumentException.class, () -> new TrayIdentity("org.example.Demo", "", "Demo"));
    }
}
