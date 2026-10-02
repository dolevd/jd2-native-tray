// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import org.appwork.storage.config.annotations.DefaultBooleanValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.jdownloader.gui.jdtrayicon.TrayConfig;

public interface NativeTrayConfig extends TrayConfig {
    @DefaultBooleanValue(false)
    @DescriptionForConfigEntry("Internal: existing tray preferences have been imported")
    boolean isSettingsMigrated();
    void setSettingsMigrated(boolean migrated);
    @DefaultBooleanValue(false)
    boolean isBuiltinWasEnabled();
    void setBuiltinWasEnabled(boolean value);
}
