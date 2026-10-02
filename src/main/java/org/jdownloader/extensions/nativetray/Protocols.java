// SPDX-License-Identifier: AGPL-3.0-only
package org.jdownloader.extensions.nativetray;

import java.util.List;
import java.util.Map;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.interfaces.DBusSerializable;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperties;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

/** Wire types are kept explicit so Java package relocation cannot change D-Bus names. */
public final class Protocols {
    private Protocols() { }
    @DBusInterfaceName("org.kde.StatusNotifierWatcher")
    public interface Watcher extends DBusInterface {
        void RegisterStatusNotifierItem(String service);
    }
    @DBusProperties({
        @DBusProperty(name="Category", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="Id", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="Title", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="Status", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="WindowId", type=UInt32.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="IconName", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="IconPixmap", type=Pixmap[].class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="AttentionIconName", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="AttentionIconPixmap", type=Pixmap[].class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="AttentionMovieName", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="OverlayIconName", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="OverlayIconPixmap", type=Pixmap[].class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="IconThemePath", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="ToolTip", type=ToolTip.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="ItemIsMenu", type=Boolean.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="Menu", type=DBusPath.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE)
    })
    @DBusInterfaceName("org.kde.StatusNotifierItem")
    public interface Item extends DBusInterface {
        void Activate(int x, int y);
        void SecondaryActivate(int x, int y);
        void ContextMenu(int x, int y);
        void Scroll(int delta, String orientation);
        void ProvideXdgActivationToken(String token);
        class NewIcon extends DBusSignal {
            public NewIcon(String path) throws DBusException { super(path); }
        }
        class NewToolTip extends DBusSignal {
            public NewToolTip(String path) throws DBusException { super(path); }
        }
        class NewStatus extends DBusSignal {
            public final String status;
            public NewStatus(String path, String status) throws DBusException { super(path, status); this.status = status; }
        }
    }
    @DBusProperties({
        @DBusProperty(name="Version", type=UInt32.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="TextDirection", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="Status", type=String.class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE),
        @DBusProperty(name="IconThemePath", type=String[].class, access=DBusProperty.Access.READ, emitChangeSignal=PropertiesEmitsChangedSignal.EmitChangeSignal.FALSE)
    })
    @DBusInterfaceName("com.canonical.dbusmenu")
    public interface Menu extends DBusInterface {
        LayoutReply GetLayout(int parentId, int recursionDepth, List<String> propertyNames);
        List<ItemProperties> GetGroupProperties(List<Integer> ids, List<String> propertyNames);
        Variant<?> GetProperty(int id, String name);
        void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);
        List<Integer> EventGroup(List<MenuEvent> events);
        boolean AboutToShow(int id);
        ShowReply AboutToShowGroup(List<Integer> ids);
        class LayoutUpdated extends DBusSignal {
            public final UInt32 revision;
            public final int parent;
            public LayoutUpdated(String path, UInt32 revision, int parent) throws DBusException { super(path, revision, parent); this.revision=revision; this.parent=parent; }
        }
    }
    public static final class Pixmap extends Struct {
        @Position(0) public final int width;
        @Position(1) public final int height;
        @Position(2) public final byte[] pixels;
        public Pixmap(int width, int height, byte[] pixels) { this.width=width; this.height=height; this.pixels=pixels; }
    }
    public static final class ToolTip extends Struct {
        @Position(0) public final String iconName;
        @Position(1) public final List<Pixmap> icons;
        @Position(2) public final String title;
        @Position(3) public final String description;
        public ToolTip(String iconName, List<Pixmap> icons, String title, String description) { this.iconName=iconName; this.icons=icons; this.title=title; this.description=description; }
        public ToolTip(String title, String description) { this("", List.of(), title, description); }
    }
    public static final class Layout extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> properties;
        @Position(2) public final List<Variant<?>> children;
        public Layout(int id, Map<String, Variant<?>> properties, List<Variant<?>> children) { this.id=id; this.properties=properties; this.children=children; }
    }
    /** Explicit multi-value reply, including correct introspection without generic Tuple inference. */
    public static final class LayoutReply extends Struct implements DBusSerializable {
        @Position(0) public UInt32 revision;
        @Position(1) public Layout layout;
        public LayoutReply() { }
        public LayoutReply(UInt32 revision, Layout layout) { deserialize(revision,layout); }
        public void deserialize(UInt32 revision, Layout layout) { this.revision=revision; this.layout=layout; }
        public Object[] serialize() { return new Object[]{revision,layout}; }
    }
    public static final class ItemProperties extends Struct {
        @Position(0) public final int id;
        @Position(1) public final Map<String, Variant<?>> properties;
        public ItemProperties(int id, Map<String, Variant<?>> properties) { this.id=id; this.properties=properties; }
    }
    public static final class MenuEvent extends Struct {
        @Position(0) public final int id;
        @Position(1) public final String event;
        @Position(2) public final Variant<?> data;
        @Position(3) public final UInt32 timestamp;
        public MenuEvent(int id, String event, Variant<?> data, UInt32 timestamp) { this.id=id; this.event=event; this.data=data; this.timestamp=timestamp; }
    }
    public static final class ShowReply extends Struct implements DBusSerializable {
        @Position(0) public int[] updates;
        @Position(1) public int[] errors;
        public ShowReply() { }
        public ShowReply(int[] updates, int[] errors) { deserialize(updates,errors); }
        public void deserialize(int[] updates, int[] errors) { this.updates=updates; this.errors=errors; }
        public Object[] serialize() { return new Object[]{updates,errors}; }
    }
}
