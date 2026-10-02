// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.util.*;
import org.freedesktop.dbus.types.Variant;
class BehaviorTest {
    @Test void pixelsUseNetworkArgbAndRetainTransparency() {
        BufferedImage image = new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0x80402010); image.setRGB(1,0,0x00112233);
        assertArrayEquals(new byte[]{(byte)0x80,0x40,0x20,0x10,0,0x11,0x22,0x33},IconPixels.argb(image));
        for (Protocols.Pixmap p : IconPixels.sizes(image,true)) {
            assertEquals(p.width*p.height*4,p.pixels.length);
            assertEquals(p.pixels[1],p.pixels[2]); assertEquals(p.pixels[2],p.pixels[3]);
            assertTrue((p.pixels[0]&255)<255);
        }
    }
    @Test void doubleActivationTogglesExactlyOncePerPair() {
        ActivationPolicy policy = new ActivationPolicy();
        assertFalse(policy.accept(false,100,500)); assertTrue(policy.accept(false,200,500));
        assertFalse(policy.accept(false,250,500)); assertTrue(policy.accept(false,300,500));
        assertFalse(policy.accept(false,1000,500)); assertFalse(policy.accept(false,1700,500));
        assertTrue(policy.accept(true,1800,500)); assertFalse(policy.accept(false,1900,500));
    }
    @Test void menuDepthFilteringAndDisabledSemantics() {
        MenuNode leaf = new MenuNode(2,Map.of("label",new Variant<>("Leaf"),"enabled",new Variant<>(false)),List.of(),()->{});
        MenuNode root = MenuNode.root(List.of(new MenuNode(1,Map.of("label",new Variant<>("Submenu")),List.of(leaf),null)));
        assertSame(leaf,root.find(2)); assertFalse(leaf.enabled()); assertTrue(leaf.visible()); assertNull(root.find(3));
        assertTrue(root.layout(0,List.of()).children.isEmpty());
        Protocols.Layout child=(Protocols.Layout)root.layout(1,List.of("label")).children.get(0).getValue();
        assertTrue(child.children.isEmpty()); assertEquals(Set.of("label"),child.properties.keySet());
        assertEquals(1,root.layout(-1,List.of()).children.size());
    }
}
