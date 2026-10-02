// SPDX-License-Identifier: AGPL-3.0-only
import org.jdownloader.extensions.nativetray.DesktopSupport;

/** Verify the effective environment inside a real Flatpak, without starting JD2. */
public class DesktopSupportProbe {
    public static void main(String[] args) {
        var result = DesktopSupport.current();
        boolean expected = args.length == 0 || !args[0].equals("--unsupported");
        if (result.supported() != expected) throw new AssertionError(result.message());
        System.out.println("PASS: " + result.message());
    }
}
