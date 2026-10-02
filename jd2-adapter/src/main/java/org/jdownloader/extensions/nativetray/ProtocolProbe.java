// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import org.jdownloader.extensions.nativetray.core.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.freedesktop.dbus.connections.impl.*;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

/** Standalone packaged-wire smoke test; no JDownloader classes or data are loaded. */
public final class ProtocolProbe {
    public static final class Watcher implements Protocols.Watcher, Properties {
        final BlockingQueue<String> names = new LinkedBlockingQueue<>();
        volatile boolean hostRegistered = true, exposeHostProperty = true;
        public String getObjectPath() { return "/StatusNotifierWatcher"; }
        public void RegisterStatusNotifierItem(String service) { names.add(service); }
        public <A> A Get(String iface, String name) { return null; }
        public <A> void Set(String iface, String name, A value) { }
        public Map<String,Variant<?>> GetAll(String iface) {
            return exposeHostProperty ? Map.of("IsStatusNotifierHostRegistered", new Variant<>(hostRegistered)) : Map.of();
        }
    }
    public static void main(String[] args) throws Exception {
        boolean desktop = args.length > 0 && args[0].equals("--desktop");
        if (desktop) require(DesktopSupport.current().supported(), DesktopSupport.current().message());
        if (!desktop && !"1".equals(System.getenv("JD_TRAY_PRIVATE_BUS")))
            throw new IllegalStateException("Run this self-test inside dbus-run-session with JD_TRAY_PRIVATE_BUS=1");
        AtomicInteger activated = new AtomicInteger(), clicked = new AtomicInteger();
        BlockingQueue<Boolean> availability = new LinkedBlockingQueue<>();
        MenuNode model = MenuNode.root(List.of(new MenuNode(1, Map.of("label", new Variant<>("Test action")), List.of(), clicked::incrementAndGet),
            new MenuNode(2, Map.of("label", new Variant<>("Disabled"), "enabled", new Variant<>(false)), List.of(), clicked::incrementAndGet)));
        try (DBusConnection watcherBus = DBusConnectionBuilder.forSessionBus().withShared(false).build();
             DBusConnection client = DBusConnectionBuilder.forSessionBus().withShared(false).build();
             SniService tray = new SniService(TrayIdentity.create(System.getenv().getOrDefault("FLATPAK_ID", "org.jdownloader.JDownloader") + ".NativeTray.Probe", "protocol-probe", "Protocol probe"), availability::add, System.out::println, secondary -> activated.incrementAndGet(), token -> {}, () -> model)) {
            Watcher watcher = new Watcher();
            if (!desktop) { watcherBus.exportObject(watcher.getObjectPath(), watcher); watcherBus.requestBusName(SniService.WATCHER); }
            BufferedImage icon = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = icon.createGraphics(); g.setColor(new Color(48,170,235)); g.fillOval(6,6,52,52);
            g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,25)); g.drawString("JD",13,41); g.dispose();
            tray.update(IconPixels.sizes(icon,false), "Native tray protocol test", false); tray.setMenu(model); tray.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!tray.isAvailable() && System.nanoTime() < deadline) Thread.sleep(50);
            require(tray.isAvailable(), "Tray registered");
            Properties props = client.getRemoteObject(tray.busName, SniService.ITEM_PATH, Properties.class);
            Map<String,Variant<?>> all = props.GetAll(SniService.ITEM_IFACE);
            require(all.get("IconPixmap").getSig().equals("a(iiay)"), "Pixmap signature");
            require(props.Get(SniService.ITEM_IFACE, "Id").toString().contains("protocol-probe"), "Properties.Get");
            String itemXml = client.getRemoteObject(tray.busName,SniService.ITEM_PATH,org.freedesktop.dbus.interfaces.Introspectable.class).Introspect();
            require(itemXml.contains("name=\"IconPixmap\"") && itemXml.contains("type=\"a(iiay)\""),"SNI property introspection");
            if (!"1".equals(System.getenv("JD_TRAY_SKIP_BUSCTL"))) {
            Process wire = new ProcessBuilder("busctl","--user","call","--",tray.busName,SniService.MENU_PATH,SniService.MENU_IFACE,"GetLayout","iias","0","-1","0").redirectErrorStream(true).start();
            String output = new String(wire.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            require(wire.waitFor() == 0 && output.startsWith("u(ia{sv}av)"), "Native dbusmenu layout: " + output);
            }
            Protocols.Menu menu = client.getRemoteObject(tray.busName,SniService.MENU_PATH,Protocols.Menu.class);
            require(menu.GetLayout(0,-1,List.of()).layout.children.size() == 2, "Recursive layout");
            require(menu.GetGroupProperties(List.of(1,2),List.of()).size() == 2,"Group properties");
            require(menu.AboutToShowGroup(List.of(0,999)).errors.length == 1,"Group reply");
            String xml = client.getRemoteObject(tray.busName,SniService.MENU_PATH,org.freedesktop.dbus.interfaces.Introspectable.class).Introspect();
            String layoutXml = xml.substring(xml.indexOf("<method name=\"GetLayout\""));
            layoutXml = layoutXml.substring(0,layoutXml.indexOf("</method>"));
            require(layoutXml.split("direction=\"out\"",-1).length == 3,"Exact introspection reply signature");
            menu.Event(1,"clicked",new Variant<>(0),new org.freedesktop.dbus.types.UInt32(0));
            menu.Event(2,"clicked",new Variant<>(0),new org.freedesktop.dbus.types.UInt32(0));
            require(clicked.get() == 1, "Enabled action only");
            Protocols.Item item = client.getRemoteObject(tray.busName,SniService.ITEM_PATH,Protocols.Item.class);
            item.Activate(0,0); require(activated.get() == 1,"Activation");
            if (!desktop) {
                watcher.hostRegistered = false;
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (tray.isAvailable() && System.nanoTime() < deadline) Thread.sleep(50);
                require(!tray.isAvailable(), "Watcher without a tray host cannot hide windows");
                watcher.hostRegistered = true;
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!tray.isAvailable() && System.nanoTime() < deadline) Thread.sleep(50);
                require(tray.isAvailable(), "Tray host recovery");
                watcher.exposeHostProperty = false;
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (tray.isAvailable() && System.nanoTime() < deadline) Thread.sleep(50);
                require(!tray.isAvailable(), "Malformed watcher cannot claim availability");
                watcher.exposeHostProperty = true;
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!tray.isAvailable() && System.nanoTime() < deadline) Thread.sleep(50);
                require(tray.isAvailable(), "Compatible watcher recovery");
                watcher.names.clear(); watcherBus.releaseBusName(SniService.WATCHER);
                deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (tray.isAvailable() && System.nanoTime() < deadline) Thread.sleep(50);
                require(!tray.isAvailable(), "Watcher loss detected");
                watcherBus.requestBusName(SniService.WATCHER);
                require(watcher.names.poll(8,TimeUnit.SECONDS) != null,"Watcher restart registered");
            }
            System.out.println("PASS: packaged SNI, properties, dbusmenu, activation, action dispatch" + (desktop ? " on Plasma" : ", watcher restart"));
        }
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
