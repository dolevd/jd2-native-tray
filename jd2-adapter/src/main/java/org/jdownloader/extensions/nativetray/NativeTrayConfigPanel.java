// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.appwork.storage.config.handler.*;
import org.jdownloader.extensions.ExtensionConfigPanel;
import org.jdownloader.gui.jdtrayicon.*;
import org.jdownloader.settings.staticreferences.CFG_GUI;
import jd.gui.swing.jdgui.views.settings.components.*;

public final class NativeTrayConfigPanel extends ExtensionConfigPanel<NativeTrayExtension> {
    private final JLabel status;
    @SuppressWarnings("unchecked")
    public NativeTrayConfigPanel(NativeTrayExtension extension) {
        // Keep an explanation visible on unsupported systems, without an enable checkbox.
        super(extension, !extension.getSupport().supported());
        addDescriptionPlain("A native desktop tray for KDE Plasma. Existing tray preferences are imported on first enable.");
        status = addDescriptionPlain(extension.getStatus());
        if (!extension.getSupport().supported()) {
            addDescriptionPlain("The extension cannot be enabled on this system. JDownloader's built-in tray and window settings are unchanged.");
            return;
        }
        StorageHandler<?> sh = extension.getSettings()._getStorageHandler();
        addPair("When closing the main window", null, new ComboBox<OnCloseAction>(
            (KeyHandler) sh.getKeyHandler("OnCloseAction"), OnCloseAction.values(),
            Arrays.stream(OnCloseAction.values()).map(OnCloseAction::getTranslation).toArray(String[]::new)));
        addPair("When minimizing the main window", null, new ComboBox<OnMinimizeAction>(
            (KeyHandler) sh.getKeyHandler("OnMinimizeAction"), OnMinimizeAction.values(),
            Arrays.stream(OnMinimizeAction.values()).map(OnMinimizeAction::getTranslation).toArray(String[]::new)));
        checkbox(sh, "StartMinimizedEnabled", "Start minimized to the tray");
        checkbox(sh, "ToogleWindowStatusWithSingleClickEnabled", "Toggle the window with a single click");
        checkbox(sh, "ToolTipEnabled", "Show a tray tooltip");
        checkbox(sh, "TrayIconClipboardIndicatorEnabled", "Show an indicator when clipboard monitoring is disabled");
        checkbox(sh, "TrayOnlyVisibleIfWindowIsHiddenEnabled", "Show the tray icon only while the window is hidden");
        checkbox(sh, "GreyIconEnabled", "Use a grey tray icon");
        addPair("Password protection", CFG_GUI.PASSWORD_PROTECTION_ENABLED, new PasswordInput(CFG_GUI.PASSWORD));
        addDescriptionPlain("Password settings are shared with JDownloader. Native icons support transparency.");
        addDescriptionPlain("The tray uses JDownloader's existing menu customization. Number editors open as dialogs.");
    }
    private void checkbox(StorageHandler<?> sh, String key, String label) {
        addPair(label, null, new Checkbox(sh.getKeyHandler(key, BooleanKeyHandler.class)));
    }
    public void setStatus(String text) { SwingUtilities.invokeLater(() -> status.setText(text)); }
    @Override public void onConfigValueModified(KeyHandler<Object> key, Object value) {
        if (!extension.getSupport().supported() && key.getKey().equalsIgnoreCase("enabled") && Boolean.TRUE.equals(value)) {
            // Advanced Settings can write the flag without invoking the extension's lifecycle.
            extension.getSettings().setEnabled(false);
            extension.diagnostic(extension.getSupport().message());
        }
    }
    @Override public void save() { }
    @Override public void updateContents() { }
}
