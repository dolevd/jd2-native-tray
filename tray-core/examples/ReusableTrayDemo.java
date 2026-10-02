// SPDX-License-Identifier: AGPL-3.0-only
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.freedesktop.dbus.types.Variant;
import org.jdownloader.extensions.nativetray.core.*;

/** Independent consumer: the classpath contains only tray-core and its runtime dependencies. */
public class ReusableTrayDemo {
    public static void main(String[] args) throws InterruptedException {
        String namespace = args.length > 0 ? args[0] : "org.example.ReusableTrayDemo";
        CountDownLatch done = new CountDownLatch(1);
        MenuNode menu = MenuNode.root(List.of(
            new MenuNode(1, Map.of("label", new Variant<>("Say hello")), List.of(), () -> System.out.println("Hello from another application")),
            new MenuNode(2, Map.of("label", new Variant<>("Exit demo")), List.of(), done::countDown)));
        BufferedImage icon = new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        try { g.setColor(new Color(55,165,100)); g.fillOval(4,4,56,56); } finally { g.dispose(); }
        try (SniService tray = new SniService(TrayIdentity.create(namespace, "reusable-tray-demo", "Reusable tray demo"),
                available -> System.out.println("Tray available: " + available), System.out::println,
                secondary -> System.out.println(secondary ? "Secondary activation" : "Primary activation"),
                token -> System.out.println("Activation token received"), () -> menu)) {
            tray.update(IconPixels.sizes(icon,false), "An application-independent tray", false);
            tray.setMenu(menu); tray.start();
            done.await();
        }
    }
}
