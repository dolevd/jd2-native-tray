import java.net.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
/** Ensure ServiceLoader finds shaded transport providers in JD's child extension loader. */
public class ChildLoaderProbe {
    public static void main(String[] args) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},ChildLoaderProbe.class.getClassLoader())) {
            Class<?> node = loader.loadClass("org.jdownloader.extensions.nativetray.MenuNode");
            Object root = node.getMethod("root",List.class).invoke(null,List.of());
            Class<?> serviceClass = loader.loadClass("org.jdownloader.extensions.nativetray.SniService");
            AtomicBoolean available = new AtomicBoolean();
            Consumer<Boolean> state = available::set;
            Consumer<String> diagnostic = System.out::println;
            Consumer<Boolean> activation = ignored -> {};
            Consumer<String> token = ignored -> {};
            Supplier<Object> menu = () -> root;
            try (AutoCloseable service = (AutoCloseable)serviceClass.getConstructors()[0].newInstance(state,diagnostic,activation,token,menu)) {
                serviceClass.getMethod("start").invoke(service);
                long deadline = System.nanoTime() + 15_000_000_000L;
                while (!available.get() && System.nanoTime() < deadline) Thread.sleep(50);
                if (!available.get()) throw new AssertionError("Child loader native transport registration");
                System.out.println("PASS: shaded D-Bus transport in a child extension class loader");
                Thread.sleep(500); // Let Plasma finish its initial property reads before removing the test export.
            }
        }
    }
}
