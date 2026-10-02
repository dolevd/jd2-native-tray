// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** Owns one session-bus connection; never calls Swing directly. */
public final class SniService implements AutoCloseable {
    public static final String WATCHER = "org.kde.StatusNotifierWatcher";
    public static final String ITEM_IFACE = "org.kde.StatusNotifierItem";
    public static final String MENU_IFACE = "com.canonical.dbusmenu";
    public static final String ITEM_PATH = "/StatusNotifierItem", MENU_PATH = "/Menu";
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Native Tray D-Bus");
        t.setContextClassLoader(SniService.class.getClassLoader()); t.setDaemon(true); return t;
    });
    private final Consumer<Boolean> availability;
    private final Consumer<String> diagnostic;
    private final Consumer<Boolean> activation;
    private final Consumer<String> token;
    private final Supplier<MenuNode> refreshMenu;
    private volatile DBusConnection connection;
    private volatile boolean closed, registered;
    private volatile MenuNode menu = MenuNode.root(List.of());
    private volatile Map<String, Variant<?>> itemProperties;
    private volatile long revision = 1;
    private String watcherOwner = "";
    private boolean announced;
    private String lastError = "";
    public final String busName;
    private final TrayIdentity identity;

    public SniService(TrayIdentity identity, Consumer<Boolean> availability, Consumer<String> diagnostic,
            Consumer<Boolean> activation, Consumer<String> token, Supplier<MenuNode> refreshMenu) {
        this.identity = Objects.requireNonNull(identity); this.busName = identity.busName();
        this.itemProperties = initialProperties(identity);
        this.availability = availability; this.diagnostic = diagnostic; this.activation = activation;
        this.token = token; this.refreshMenu = refreshMenu;
    }
    public void start() { worker.scheduleWithFixedDelay(this::connectAndRegister, 0, 2, TimeUnit.SECONDS); }
    public boolean isAvailable() { return registered && !closed; }
    private void announce(boolean state) {
        registered = state;
        if (!announced || state != lastAvailability) { announced = true; lastAvailability = state; availability.accept(state); }
    }
    private boolean lastAvailability;
    private void connectAndRegister() {
        if (closed) return;
        try {
            if (connection == null || !connection.isConnected()) {
                disconnect();
                DBusConnection next = DBusConnectionBuilder.forSessionBus().withShared(false).build();
                connection = next;
                next.exportObject(ITEM_PATH, new ItemObject());
                next.exportObject(MENU_PATH, new MenuObject());
                next.requestBusName(busName);
                next.addSigHandler(DBus.NameOwnerChanged.class, signal -> {
                    if (WATCHER.equals(signal.name)) {
                        if (signal.newOwner.isEmpty()) announce(false);
                        worker.execute(this::connectAndRegister);
                    }
                });
            }
            DBus bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
            String owner = bus.NameHasOwner(WATCHER) ? bus.GetNameOwner(WATCHER) : "";
            if (owner.isEmpty()) {
                watcherOwner = ""; announce(false); diagnostic.accept("Waiting for the desktop tray service"); return;
            }
            Map<String, Variant<?>> watcherProperties = connection.getRemoteObject(WATCHER, "/StatusNotifierWatcher", Properties.class).GetAll(WATCHER);
            Variant<?> host = watcherProperties.get("IsStatusNotifierHostRegistered");
            if (host == null || !(host.getValue() instanceof Boolean)) {
                announce(false); diagnostic.accept("The desktop tray service does not expose a compatible StatusNotifierItem host"); return;
            }
            if (!Boolean.TRUE.equals(host.getValue())) {
                announce(false); diagnostic.accept("Waiting for a desktop tray host; window hiding is unavailable"); return;
            }
            if (!registered || !owner.equals(watcherOwner)) {
                connection.getRemoteObject(WATCHER, "/StatusNotifierWatcher", Protocols.Watcher.class)
                    .RegisterStatusNotifierItem(busName);
                watcherOwner = owner;
                diagnostic.accept("Connected to the native desktop tray"); announce(true); lastError = "";
            }
        } catch (Exception e) {
            if (closed) return;
            announce(false);
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (!message.equals(lastError)) { diagnostic.accept("Native tray unavailable: " + message); lastError = message; }
            disconnect();
        }
    }
    private void disconnect() {
        DBusConnection old = connection; connection = null; watcherOwner = "";
        if (old != null) try { old.close(); } catch (Exception ignored) { }
    }
    private static Map<String, Variant<?>> initialProperties(TrayIdentity identity) {
        Map<String, Variant<?>> p = new LinkedHashMap<>();
        p.put("Category", new Variant<>("ApplicationStatus")); p.put("Id", new Variant<>(identity.itemId()));
        p.put("Title", new Variant<>(identity.title())); p.put("Status", new Variant<>("Active"));
        p.put("WindowId", new Variant<>(new UInt32(0))); p.put("IconName", new Variant<>(""));
        p.put("IconPixmap", new Variant<>(List.of(), "a(iiay)"));
        for (String prefix : List.of("Attention", "Overlay")) {
            p.put(prefix + "IconName", new Variant<>("")); p.put(prefix + "IconPixmap", new Variant<>(List.of(), "a(iiay)"));
        }
        p.put("AttentionMovieName", new Variant<>("")); p.put("IconThemePath", new Variant<>(""));
        p.put("ItemIsMenu", new Variant<>(false)); p.put("Menu", new Variant<>(new DBusPath(MENU_PATH)));
        p.put("ToolTip", new Variant<>(new Protocols.ToolTip(identity.title(), ""), "(sa(iiay)ss)"));
        return Map.copyOf(p);
    }
    /** Called with immutable snapshots. Serialization and signals happen off the EDT. */
    public void update(List<Protocols.Pixmap> pixels, String tooltip, boolean passive) {
        if (closed) return;
        worker.execute(() -> {
            Map<String, Variant<?>> p = new LinkedHashMap<>(itemProperties);
            if (pixels != null) p.put("IconPixmap", new Variant<>(pixels, "a(iiay)"));
            Protocols.ToolTip previous = (Protocols.ToolTip) p.get("ToolTip").getValue();
            boolean tipChanged = !previous.description.equals(tooltip) || !previous.title.equals(tooltip.isEmpty() ? "" : identity.title());
            if (tipChanged) p.put("ToolTip", new Variant<>(new Protocols.ToolTip(tooltip.isEmpty() ? "" : identity.title(), tooltip), "(sa(iiay)ss)"));
            String status = passive ? "Passive" : "Active";
            boolean statusChanged = !status.equals(p.get("Status").getValue());
            p.put("Status", new Variant<>(status)); itemProperties = Map.copyOf(p);
            try {
                DBusConnection bus = connection;
                if (bus == null || !bus.isConnected()) return;
                if (pixels != null) bus.sendMessage(new Protocols.Item.NewIcon(ITEM_PATH));
                if (tipChanged) bus.sendMessage(new Protocols.Item.NewToolTip(ITEM_PATH));
                if (statusChanged) bus.sendMessage(new Protocols.Item.NewStatus(ITEM_PATH, status));
            } catch (Exception e) { diagnostic.accept("Tray update failed: " + e.getMessage()); }
        });
    }
    public void setMenu(MenuNode next) {
        if (closed) return;
        worker.execute(() -> publishMenu(next));
    }
    private synchronized boolean publishMenu(MenuNode next) {
        // Runnable identities change on refresh; compare only the wire representation.
        if (fingerprint(menu).equals(fingerprint(next))) { menu = next; return false; }
        menu = next; revision = revision == 0xffffffffL ? 1 : revision + 1;
        try {
            DBusConnection bus = connection;
            if (bus != null && bus.isConnected()) bus.sendMessage(new Protocols.Menu.LayoutUpdated(MENU_PATH, new UInt32(revision), 0));
        } catch (Exception e) { diagnostic.accept("Menu update failed: " + e.getMessage()); }
        return true;
    }
    private static String fingerprint(MenuNode n) {
        return n.id() + new TreeMap<>(n.properties()).toString() + n.children().stream().map(SniService::fingerprint).toList();
    }
    @Override public void close() {
        closed = true; registered = false; worker.shutdownNow(); disconnect();
    }
    public final class ItemObject implements Protocols.Item, Properties {
        public String getObjectPath() { return ITEM_PATH; }
        public void Activate(int x, int y) { activation.accept(false); }
        public void SecondaryActivate(int x, int y) { activation.accept(true); }
        public void ContextMenu(int x, int y) { /* The host displays the exported Menu. */ }
        public void Scroll(int delta, String orientation) { }
        public void ProvideXdgActivationToken(String value) { token.accept(value); }
        @SuppressWarnings("unchecked") public <A> A Get(String iface, String name) { return (A) property(GetAll(iface), name); }
        public <A> void Set(String iface, String name, A value) { throw new DBusExecutionException("Properties are read-only"); }
        public Map<String, Variant<?>> GetAll(String iface) { return ITEM_IFACE.equals(iface) ? itemProperties : Map.of(); }
    }
    private static Variant<?> property(Map<String, Variant<?>> map, String name) {
        Variant<?> value = map.get(name);
        if (value == null) throw new DBusExecutionException("Unknown property: " + name);
        return value;
    }
    public final class MenuObject implements Protocols.Menu, Properties {
        public String getObjectPath() { return MENU_PATH; }
        public synchronized Protocols.LayoutReply GetLayout(int parent, int depth, List<String> names) {
            MenuNode node = menu.find(parent);
            if (node == null) throw new DBusExecutionException("Unknown menu item");
            return new Protocols.LayoutReply(new UInt32(revision), node.layout(depth, names));
        }
        public List<Protocols.ItemProperties> GetGroupProperties(List<Integer> ids, List<String> names) {
            List<Protocols.ItemProperties> result = new ArrayList<>();
            for (Integer id : ids) {
                MenuNode n = menu.find(id);
                if (n != null) result.add(new Protocols.ItemProperties(id, n.layout(0, names).properties));
            }
            return result;
        }
        public Variant<?> GetProperty(int id, String name) {
            MenuNode n = menu.find(id);
            if (n == null) throw new DBusExecutionException("Unknown menu item");
            return property(n.properties(), name);
        }
        public void Event(int id, String event, Variant<?> data, UInt32 time) {
            MenuNode n = menu.find(id);
            if ("clicked".equals(event) && n != null && n.enabled() && n.visible() && n.action() != null) n.action().run();
        }
        public List<Integer> EventGroup(List<Protocols.MenuEvent> events) {
            List<Integer> errors = new ArrayList<>();
            for (Protocols.MenuEvent e : events) { if (menu.find(e.id) == null) errors.add(e.id); else Event(e.id, e.event, e.data, e.timestamp); }
            return errors;
        }
        public boolean AboutToShow(int id) { return publishMenu(refreshMenu.get()); }
        public Protocols.ShowReply AboutToShowGroup(List<Integer> ids) {
            boolean changed = AboutToShow(0);
            return new Protocols.ShowReply(changed ? ids.stream().mapToInt(Integer::intValue).toArray() : new int[0], ids.stream().filter(id -> menu.find(id) == null).mapToInt(Integer::intValue).toArray());
        }
        @SuppressWarnings("unchecked") public <A> A Get(String iface, String name) { return (A) property(GetAll(iface), name); }
        public <A> void Set(String iface, String name, A value) { throw new DBusExecutionException("Properties are read-only"); }
        public Map<String, Variant<?>> GetAll(String iface) {
            return MENU_IFACE.equals(iface) ? Map.of("Version", new Variant<>(new UInt32(4)), "TextDirection", new Variant<>("ltr"),
                "Status", new Variant<>("normal"), "IconThemePath", new Variant<>(List.of(), "as")) : Map.of();
        }
    }
}
