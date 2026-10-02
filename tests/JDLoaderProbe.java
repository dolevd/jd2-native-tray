import java.net.*;
import java.nio.file.*;
import javax.swing.*;
import org.appwork.utils.Application;
import org.jdownloader.extensions.*;
public class JDLoaderProbe {
    @SuppressWarnings("unchecked") public static void main(String[] args) throws Exception {
        Application.setApplication(".jdtray-fixture");
        org.jdownloader.updatev2.gui.LAFOptions.init("javax.swing.plaf.metal.MetalLookAndFeel");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()}, JDLoaderProbe.class.getClassLoader())) {
            Class<AbstractExtension<?,?>> cls = (Class<AbstractExtension<?,?>>)(Class<?>)loader.loadClass("org.jdownloader.extensions.nativetray.NativeTrayExtension");
            LazyExtension lazy = LazyExtension.create(args[0],cls);
            if (!lazy.getName().equals("Native Tray") || lazy.getVersion() != 2 || !lazy.isLinuxRunnable() || lazy.isWindowsRunnable()) throw new AssertionError("Extension metadata");
            lazy.init();
            AbstractExtension<?,?> extension = lazy._getExtension();
            boolean unsupported = args.length > 2 && args[2].equals("--unsupported");
            if (unsupported) extension.getSettings().setEnabled(true); // A saved enable flag from a previous Plasma session.
            extension.init();
            if (extension.getConfigPanel() == null || extension.isEnabled()) throw new AssertionError("Settings panel / enable default");
            SwingUtilities.invokeAndWait(() -> {
                var panel = extension.getConfigPanel(); panel.setSize(900,650); panel.doLayout();
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(900,650,java.awt.image.BufferedImage.TYPE_INT_RGB);
                image.getGraphics().setColor(java.awt.Color.LIGHT_GRAY); image.getGraphics().fillRect(0,0,900,650);
                for (java.awt.Component component : panel.getComponents()) if (component instanceof java.awt.Container nested) nested.doLayout();
                panel.printAll(image.getGraphics());
                try { javax.imageio.ImageIO.write(image,"png",Path.of(args[1]).toFile()); } catch(Exception e) { throw new RuntimeException(e); }
            });
            if (unsupported) {
                if (extension.getSettings().isEnabled()) throw new AssertionError("Unsupported startup left the saved enable flag set");
                extension.getSettings().setEnabled(true); // Attempt a direct Advanced Settings write too.
                if (extension.getSettings().isEnabled()) throw new AssertionError("Advanced Settings bypassed the compatibility guard");
                try { extension.setEnabled(true); throw new AssertionError("Unsupported host enabled the extension"); }
                catch (StartException expected) {
                    if (!expected.getMessage().contains("unsupported")) throw new AssertionError(expected);
                }
                if (extension.isEnabled() || extension.getSettings().isEnabled()) throw new AssertionError("Rejected enable did not roll back");
                var serviceField = cls.getDeclaredField("service"); serviceField.setAccessible(true);
                if (serviceField.get(extension) != null) throw new AssertionError("Unsupported system started the tray");
                SwingUtilities.invokeAndWait(() -> {
                    if (extension.getConfigPanel().getComponentCount() < 3) throw new AssertionError("No compatibility explanation panel");
                });
                System.out.println("PASS: unsupported desktop startup and manual enable rejected, settings retained and explanation visible");
                System.exit(0);
            }
            jd.SecondLevelLaunch.INIT_COMPLETE.setReached();
            jd.SecondLevelLaunch.EXTENSIONS_LOADED.setReached();
            Class<?> adapterClass = loader.loadClass("org.jdownloader.extensions.nativetray.JDMenuAdapter");
            var constructor = adapterClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            Object adapter = constructor.newInstance(extension);
            var snapshot = adapterClass.getDeclaredMethod("snapshot"); snapshot.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Object menu = snapshot.invoke(adapter);
                    var children = (java.util.List<?>)menu.getClass().getMethod("children").invoke(menu);
                    if (children.size() < 10) throw new AssertionError("Too few JD menu entries: " + children.size());
                    System.out.println("PASS: JD customized menu snapshot: " + children.size() + " entries");
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            System.out.println("PASS: JD2 LazyExtension discovery, metadata, config schema and settings panel");
        }
        System.exit(0);
    }
}
