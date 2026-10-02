// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import org.jdownloader.extensions.nativetray.core.*;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import jd.SecondLevelLaunch;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.gui.swing.jdgui.*;
import jd.plugins.AddonPanel;
import org.appwork.utils.swing.dialog.Dialog;
import org.appwork.utils.swing.windowmanager.WindowManager;
import org.appwork.utils.swing.windowmanager.WindowManager.FrameState;
import org.jdownloader.extensions.*;
import org.jdownloader.gui.IconKey;
import org.jdownloader.gui.jdtrayicon.*;
import org.jdownloader.gui.jdtrayicon.actions.TrayExitAction;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.staticreferences.CFG_GUI;

/** Normal JD application extension. All JD/Swing access stays on the EDT. */
public final class NativeTrayExtension extends AbstractExtension<NativeTrayConfig, NativeTrayTranslation> {
    private NativeTrayConfigPanel panel;
    private volatile String status = "Disabled";
    private volatile SniService service;
    private volatile boolean running;
    private int generation;
    private boolean attached, startingHidden, passwordDialog;
    private int normalState;
    private JFrame frame;
    private MainFrameClosingHandler previousClosing;
    private final MainFrameClosingHandler closeHandler = this::onClose;
    private WindowStateListener stateListener;
    private ComponentListener visibilityListener;
    private javax.swing.Timer timer;
    private final ActivationPolicy clicks = new ActivationPolicy();
    private JDMenuAdapter menus;
    private BufferedImage logo;
    private String iconStyle = "";
    private volatile String activationToken;
    private Thread shutdownHook;
    private final DesktopSupport.Result support = DesktopSupport.current();

    public NativeTrayExtension() { setTitle("Native Tray"); }
    @Override public String getName() { return "Native Tray"; }
    @Override public String getDescription() { return "Native StatusNotifierItem tray with JD2 window behavior and menu settings"; }
    @Override public String getIconKey() { return IconKey.ICON_GUI; }
    @Override public boolean isHeadlessRunnable() { return false; }
    @Override public boolean isWindowsRunnable() { return false; }
    @Override public boolean isMacRunnable() { return false; }
    @Override public boolean isDefaultEnabled() { return false; }
    @Override public boolean hasConfigPanel() { return true; }
    @Override public ExtensionConfigPanel<?> getConfigPanel() { return panel; }
    @Override public AddonPanel<NativeTrayExtension> getGUI() { return null; }
    public String getStatus() { return status; }
    public DesktopSupport.Result getSupport() { return support; }
    @Override public synchronized void setEnabled(boolean enabled) throws StartException, StopException {
        if (enabled && !support.supported()) {
            // JD's settings checkbox persists its value before calling setEnabled(). Undo that too.
            getSettings().setEnabled(false);
            diagnostic(support.message());
            throw new StartException(support.message());
        }
        super.setEnabled(enabled);
    }
    void diagnostic(String value) {
        if (!value.equals(status)) { status = value; logger.info(value); if (panel != null) panel.setStatus(value); }
    }
    @Override protected void initExtension() throws StartException {
        if (!support.supported()) {
            getSettings().setEnabled(false);
            diagnostic(support.message());
        }
        try { onEdt(() -> { panel = new NativeTrayConfigPanel(this); return null; }); }
        catch (Exception e) { throw new StartException(e); }
    }
    @Override protected void start() {
        running = true;
        final int ticket = ++generation;
        diagnostic("Waiting for JDownloader's GUI");
        SecondLevelLaunch.INIT_COMPLETE.executeWhenReached(() -> SwingUtilities.invokeLater(() -> {
            if (!running || generation != ticket) return;
            try {
                frame = JDGui.getInstance().getMainFrame();
                migratePreferences();
                menus = new JDMenuAdapter(this);
                logo = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = logo.createGraphics();
                g.drawImage(NewTheme.I().getImage("logo/jd_logo_128_128", 128), 0, 0, 128, 128, null); g.dispose();
                String namespace = System.getenv().getOrDefault("FLATPAK_ID", "org.jdownloader.JDownloader") + ".NativeTray";
                SniService next = new SniService(TrayIdentity.create(namespace, "jd2-native-tray", "JDownloader"), available -> SwingUtilities.invokeLater(() -> {
                    if (running && generation == ticket) onAvailability(available);
                }), this::diagnostic, secondary -> SwingUtilities.invokeLater(() -> {
                    if (running && generation == ticket) activate(secondary);
                }), token -> activationToken = token, () -> {
                    try { return onEdt(() -> running && generation == ticket ? menus.snapshot() : MenuNode.root(List.of())); }
                    catch (Exception e) { logger.log(e); return MenuNode.root(List.of()); }
                });
                service = next;
                next.setMenu(menus.snapshot());
                refresh(); next.start();
                timer = new javax.swing.Timer(1500, event -> { try { refresh(); } catch (Exception e) { logger.log(e); } }); timer.start();
                shutdownHook = new Thread(() -> { running = false; next.close(); }, "JD Native Tray shutdown");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            } catch (Exception e) {
                logger.log(e); diagnostic("Cannot start Native Tray: " + e.getMessage());
                if (service != null) { service.close(); service = null; }
            }
        }));
    }
    private void migratePreferences() {
        NativeTrayConfig own = getSettings();
        if (own.isSettingsMigrated()) return;
        TrayConfig old = JDGui.getInstance().getTray().getSettings();
        own.setOnCloseAction(old.getOnCloseAction()); own.setOnMinimizeAction(old.getOnMinimizeAction());
        own.setStartMinimizedEnabled(old.isStartMinimizedEnabled());
        own.setToogleWindowStatusWithSingleClickEnabled(old.isToogleWindowStatusWithSingleClickEnabled());
        own.setToolTipEnabled(old.isToolTipEnabled()); own.setGreyIconEnabled(old.isGreyIconEnabled());
        own.setTrayIconClipboardIndicatorEnabled(old.isTrayIconClipboardIndicatorEnabled());
        own.setTrayOnlyVisibleIfWindowIsHiddenEnabled(old.isTrayOnlyVisibleIfWindowIsHiddenEnabled());
        own.setBuiltinWasEnabled(old.isEnabled()); own.setSettingsMigrated(true);
    }
    private void onAvailability(boolean available) {
        if (available && !attached) {
            try {
                JDGui gui = JDGui.getInstance();
                // stop() unregisters the old close/minimize handlers and its AWT recovery thread.
                if (gui.getTray().isEnabled()) { getSettings().setBuiltinWasEnabled(true); gui.getTray().setEnabled(false); }
                previousClosing = gui.getClosingHandler();
                gui.setClosingHandler(closeHandler);
                normalState = frame.getExtendedState() & ~Frame.ICONIFIED;
                frame.setAlwaysOnTop(CFG_GUI.MAIN_WINDOW_ALWAYS_ON_TOP.isEnabled());
                stateListener = event -> {
                    if (!running || !attached) return;
                    if ((event.getNewState() & Frame.ICONIFIED) == 0) normalState = event.getNewState();
                    else if (getSettings().getOnMinimizeAction() == OnMinimizeAction.TO_TRAY && canHide()) hideWindow();
                };
                frame.addWindowStateListener(stateListener);
                visibilityListener = new ComponentAdapter() {
                    @Override public void componentHidden(ComponentEvent e) { refresh(); }
                    @Override public void componentShown(ComponentEvent e) { refresh(); }
                };
                frame.addComponentListener(visibilityListener); attached = true;
                if (!startingHidden) {
                    startingHidden = true;
                    if (getSettings().isStartMinimizedEnabled()) hideWindow();
                }
            } catch (Exception e) { logger.log(e); diagnostic("Window integration failed: " + e.getMessage()); emergencyReveal(); }
        } else if (!available && attached) {
            // Preserve a taskbar entry if Plasma or the bus disappears while JD is hidden.
            emergencyReveal();
        }
        refresh();
    }
    boolean canHide() { return running && attached && service != null && service.isAvailable(); }
    boolean isWindowHidden() { return frame != null && !frame.isVisible(); }
    private void hideWindow() {
        if (!canHide()) { minimizeToTaskbar(); return; }
        if ((frame.getExtendedState() & Frame.ICONIFIED) == 0) normalState = frame.getExtendedState();
        WindowManager.getInstance().hide(frame); refresh();
    }
    void toggleWindow() {
        if (frame.isVisible() && (frame.getExtendedState() & Frame.ICONIFIED) == 0) hideWindow();
        else revealWindow(true);
    }
    private void activate(boolean secondary) {
        Object interval = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        long milliseconds = interval instanceof Number n ? n.longValue() : 500;
        if (secondary || clicks.accept(getSettings().isToogleWindowStatusWithSingleClickEnabled(),
                System.nanoTime() / 1_000_000, milliseconds)) toggleWindow();
    }
    boolean checkPassword() {
        if (!isWindowHidden() || !CFG_GUI.PASSWORD_PROTECTION_ENABLED.isEnabled() || CFG_GUI.PASSWORD.getValue().isEmpty()) return true;
        if (passwordDialog) return false;
        passwordDialog = true;
        try {
            String entered = Dialog.getInstance().showInputDialog(Dialog.STYLE_PASSWORD, "JDownloader password", "Enter the password to unlock JDownloader.", null, null, null, null);
            if (CFG_GUI.PASSWORD.getValue().equals(entered)) return true;
            Dialog.getInstance().showMessageDialog("Incorrect password");
        } catch (org.appwork.utils.swing.dialog.DialogNoAnswerException ignored) { }
        finally { passwordDialog = false; }
        return false;
    }
    boolean isLocked() {
        return isWindowHidden() && CFG_GUI.PASSWORD_PROTECTION_ENABLED.isEnabled() && !CFG_GUI.PASSWORD.getValue().isEmpty();
    }
    void revealWindow(boolean authorize) {
        if (authorize && !checkPassword()) return;
        String token = activationToken; activationToken = null;
        if (token != null) X11Activation.apply(frame, token, logger);
        frame.setExtendedState(normalState & ~Frame.ICONIFIED);
        WindowManager.getInstance().setVisible(frame, true, FrameState.TO_FRONT_FOCUSED);
        refresh();
    }
    private void emergencyReveal() {
        if (frame != null && !frame.isVisible()) {
            // Match the existing taskbar behavior without opening protected contents automatically.
            if (isLocked()) { frame.setExtendedState(normalState | Frame.ICONIFIED); frame.setVisible(true); }
            else revealWindow(false);
        }
    }
    private void minimizeToTaskbar() {
        if ((frame.getExtendedState() & Frame.ICONIFIED) == 0) normalState = frame.getExtendedState();
        frame.setExtendedState(normalState | Frame.ICONIFIED); frame.setVisible(true);
    }
    private void onClose(WindowEvent event) {
        OnCloseAction choice = getSettings().getOnCloseAction();
        if (choice == OnCloseAction.ASK) {
            JCheckBox remember = new JCheckBox("Remember this choice");
            int answer = JOptionPane.showOptionDialog(frame, new Object[]{"What should happen when you close JDownloader?", remember},
                "Close JDownloader", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new String[]{"Hide to tray", "Minimize to taskbar", "Exit", "Cancel"}, "Hide to tray");
            choice = switch (answer) { case 0 -> OnCloseAction.TO_TRAY; case 1 -> OnCloseAction.TO_TASKBAR; case 2 -> OnCloseAction.EXIT; default -> OnCloseAction.ASK; };
            if (choice == OnCloseAction.ASK) return;
            if (remember.isSelected()) getSettings().setOnCloseAction(choice);
        }
        switch (choice) {
            case TO_TRAY -> hideWindow();
            case TO_TASKBAR -> minimizeToTaskbar();
            case EXIT -> new TrayExitAction().actionPerformed(new ActionEvent(frame, ActionEvent.ACTION_PERFORMED, "exit"));
            default -> { }
        }
    }
    void refresh() {
        SniService bus = service;
        if (bus == null || !running || frame == null) return;
        NativeTrayConfig cfg = getSettings();
        if (attached && JDGui.getInstance().getTray().isEnabled()) {
            try {
                JDGui.getInstance().getTray().setEnabled(false);
                JDGui.getInstance().setClosingHandler(closeHandler);
                frame.setAlwaysOnTop(CFG_GUI.MAIN_WINDOW_ALWAYS_ON_TOP.isEnabled());
                logger.info("Built-in tray disabled while Native Tray is active");
            } catch (Exception e) { logger.log(e); }
        }
        boolean badge = cfg.isTrayIconClipboardIndicatorEnabled() && !CFG_GUI.CLIPBOARD_MONITORED.isEnabled();
        String style = cfg.isGreyIconEnabled() + ":" + badge;
        List<Protocols.Pixmap> pixels = null;
        if (!style.equals(iconStyle)) { iconStyle = style; pixels = IconPixels.sizes(JDTrayIcons.withClipboardBadge(logo, badge), cfg.isGreyIconEnabled()); }
        String tip = "";
        if (cfg.isToolTipEnabled()) {
            DownloadWatchDog watch = DownloadWatchDog.getInstance();
            tip = (watch.isPaused() ? "Paused" : watch.isRunning() ? "Downloading" : "Idle") + " · " + watch.getActiveDownloads()
                + " active downloads · " + String.format(java.util.Locale.ROOT, "%.1f KiB/s", watch.getDownloadSpeedManager().getSpeed() / 1024.0);
        }
        bus.update(pixels, tip, cfg.isTrayOnlyVisibleIfWindowIsHiddenEnabled() && !isWindowHidden());
        bus.setMenu(menus.snapshot());
    }
    @Override protected void stop() throws StopException {
        running = false; ++generation;
        SniService old = service; service = null;
        try {
            onEdt(() -> {
                if (timer != null) { timer.stop(); timer = null; }
                emergencyReveal();
                if (attached) {
                    if (stateListener != null) frame.removeWindowStateListener(stateListener);
                    if (visibilityListener != null) frame.removeComponentListener(visibilityListener);
                    JDGui gui = JDGui.getInstance();
                    if (gui.getClosingHandler() == closeHandler) gui.setClosingHandler(previousClosing);
                    attached = false;
                    if (getSettings().isBuiltinWasEnabled()) gui.getTray().setEnabled(true);
                }
                if (menus != null) menus.dispose(); menus = null;
                iconStyle = ""; startingHidden = false;
                return null;
            });
        } catch (Exception e) { throw new StopException(e); }
        finally {
            if (old != null) old.close();
            if (shutdownHook != null) try { Runtime.getRuntime().removeShutdownHook(shutdownHook); } catch (IllegalStateException ignored) { }
            shutdownHook = null; diagnostic(support.supported() ? "Disabled" : support.message());
        }
    }
    static <T> T onEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        AtomicReference<T> result = new AtomicReference<>(); AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(task.call()); } catch (Exception e) { error.set(e); } });
        if (error.get() != null) throw error.get();
        return result.get();
    }
}
