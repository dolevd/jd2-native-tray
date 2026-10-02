// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray.core;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.types.Variant;

/** Immutable menu snapshot. Callers dispatch actions to their own UI thread if necessary. */
public record MenuNode(int id, Map<String, Variant<?>> properties, List<MenuNode> children, Runnable action) {
    public MenuNode { properties=Map.copyOf(properties); children=List.copyOf(children); }
    public static MenuNode root(List<MenuNode> children) { return new MenuNode(0, Map.of("children-display",new Variant<>("submenu")), children, null); }
    public MenuNode find(int wanted) {
        if (id == wanted) return this;
        for (MenuNode child: children) { MenuNode found=child.find(wanted); if(found!=null) return found; }
        return null;
    }
    public boolean enabled() { Variant<?> v=properties.get("enabled"); return v==null || Boolean.TRUE.equals(v.getValue()); }
    public boolean visible() { Variant<?> v=properties.get("visible"); return v==null || Boolean.TRUE.equals(v.getValue()); }
    public Protocols.Layout layout(int depth, List<String> names) {
        Map<String,Variant<?>> filtered=new java.util.LinkedHashMap<>();
        properties.forEach((k,v)->{if(names.isEmpty() || names.contains(k))filtered.put(k,v);});
        List<Variant<?>> nested=depth==0 ? List.of() : children.stream()
            .<Variant<?>>map(n->new Variant<>(n.layout(depth<0?-1:depth-1,names),"(ia{sv}av)")).toList();
        return new Protocols.Layout(id,filtered,nested);
    }
}
