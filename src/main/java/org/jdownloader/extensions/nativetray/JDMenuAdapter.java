// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.awt.event.ActionEvent;
import java.util.*;
import javax.swing.*;
import org.freedesktop.dbus.types.Variant;
import org.jdownloader.controlling.contextmenu.*;
import org.jdownloader.gui.jdtrayicon.MenuManagerTrayIcon;

/** Converts JD's customized menu model into the subset supported by dbusmenu. EDT only. */
final class JDMenuAdapter {
    private final NativeTrayExtension extension;
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<MenuItemData, CustomizableAppAction> actions = new IdentityHashMap<>();
    private final Set<JDialog> dialogs = new HashSet<>();
    private final Map<Icon, byte[]> icons = new WeakHashMap<>();
    private int nextId = 10;
    JDMenuAdapter(NativeTrayExtension extension) { this.extension = extension; }
    MenuNode snapshot() {
        List<MenuNode> children = new ArrayList<>();
        children.add(item(1, extension.isLocked() ? "Unlock JDownloader" : extension.isWindowHidden() ? "Show JDownloader" : "Show / hide JDownloader",
            true, () -> { if (extension.isLocked()) extension.revealWindow(true); else extension.toggleWindow(); }));
        if (extension.isLocked()) return MenuNode.root(children);
        children.add(new MenuNode(2, Map.of("type", new Variant<>("separator")), List.of(), null));
        try { children.addAll(convert(MenuManagerTrayIcon.getInstance().getMenuData().getItems(), "root")); }
        catch (Exception e) { extension.getLogger().log(e); children.add(item(3, "Tray menu unavailable", false, null)); }
        return MenuNode.root(children);
    }
    private List<MenuNode> convert(List<MenuItemData> data, String path) {
        List<MenuNode> nodes = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            MenuItemData entry = data.get(i);
            if (!entry.isVisible() || entry._getValidateException() != null) continue;
            String key = path + "/" + i + ":" + entry._getIdentifier();
            int id = ids.computeIfAbsent(key, ignored -> nextId++);
            try {
                if (entry instanceof SeparatorData) {
                    nodes.add(new MenuNode(id, Map.of("type", new Variant<>("separator")), List.of(), null));
                } else if (entry.getType() == MenuItemData.Type.CONTAINER) {
                    nodes.add(new MenuNode(id, Map.of("label", new Variant<>(label(entry.getName())), "children-display", new Variant<>("submenu")),
                        convert(entry.getItems(), key), null));
                } else if (entry instanceof MenuLink) {
                    nodes.add(item(id, label(entry.getName()) + "…", true, () -> openEditor(entry)));
                } else {
                    CustomizableAppAction action = actions.get(entry);
                    if (action == null) { action = entry.createAction(); actions.put(entry, action); }
                    action.requestUpdate(null);
                    if (!action.isVisible()) continue;
                    Map<String, Variant<?>> props = new LinkedHashMap<>();
                    props.put("label", new Variant<>(label(action.getName()))); props.put("enabled", new Variant<>(action.isEnabled()));
                    Icon icon = action.getSmallIcon();
                    if (icon != null) props.put("icon-data", new Variant<>(icons.computeIfAbsent(icon, JDMenuAdapter::png)));
                    if (action.isToggle()) { props.put("toggle-type", new Variant<>("checkmark")); props.put("toggle-state", new Variant<>(action.isSelected() ? 1 : 0)); }
                    CustomizableAppAction chosen = action;
                    nodes.add(new MenuNode(id, props, List.of(), () -> SwingUtilities.invokeLater(() -> {
                        if (!extension.isEnabled() || !extension.checkPassword()) return;
                        chosen.requestUpdate(null);
                        if (chosen.isVisible() && chosen.isEnabled()) {
                            // Use Swing's own menu item to preserve toggle Action semantics.
                            JMenuItem control = chosen.isToggle() ? new JCheckBoxMenuItem(chosen) : new JMenuItem(chosen);
                            control.doClick(0); control.setAction(null); extension.refresh();
                        }
                    })));
                }
            } catch (Exception e) { extension.getLogger().log(e); }
        }
        actions.keySet().removeIf(entry -> !contains(MenuManagerTrayIcon.getInstance().getMenuData().getItems(), entry));
        return nodes;
    }
    private static byte[] png(Icon icon) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(Math.max(1,icon.getIconWidth()), Math.max(1,icon.getIconHeight()), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics(); icon.paintIcon(null,g,0,0); g.dispose();
        try { java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",out); return out.toByteArray(); }
        catch (java.io.IOException e) { return new byte[0]; }
    }
    private static boolean contains(List<MenuItemData> entries, MenuItemData target) {
        for (MenuItemData e : entries) if (e == target || contains(e.getItems(), target)) return true;
        return false;
    }
    private MenuNode item(int id, String name, boolean enabled, Runnable callback) {
        return new MenuNode(id, Map.of("label", new Variant<>(name), "enabled", new Variant<>(enabled)), List.of(),
            callback == null ? null : () -> SwingUtilities.invokeLater(() -> { if (extension.isEnabled()) { callback.run(); extension.refresh(); } }));
    }
    private void openEditor(MenuItemData entry) {
        if (!extension.checkPassword()) return;
        try {
            JComponent widget = entry.createItem(null);
            if (widget == null) return;
            JDialog dialog = new JDialog(jd.gui.swing.jdgui.JDGui.getInstance().getMainFrame(), label(entry.getName()), false);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JPanel content = new JPanel(new java.awt.BorderLayout(12, 12)); content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            content.add(widget, java.awt.BorderLayout.CENTER);
            JButton close = new JButton("Close"); close.addActionListener(e -> dialog.dispose()); content.add(close, java.awt.BorderLayout.SOUTH);
            dialog.setContentPane(content); dialog.pack(); dialog.setLocationRelativeTo(dialog.getOwner());
            dialogs.add(dialog); dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) { dialogs.remove(dialog); }
            }); dialog.setVisible(true);
        } catch (Exception e) { extension.getLogger().log(e); }
    }
    private static String label(String value) { return value == null ? "JDownloader" : value.replace("&", "_"); }
    void dispose() { for (JDialog dialog : List.copyOf(dialogs)) dialog.dispose(); actions.clear(); icons.clear(); }
}
