# Third-party notices

The shaded artifact includes:

- dbus-java-core and dbus-java-transport-native-unixsocket 5.2.2, MIT license. https://github.com/hypfvieh/dbus-java/blob/master/LICENSE
- SLF4J API and slf4j-simple 2.0.17, MIT license. https://www.slf4j.org/license.html

Maven embeds the dependency metadata in the JAR. The project does not bundle JDownloader, AppWork, JNA or JD2's theme assets. Those are supplied by the existing application. Source compatibility was checked against the installed JD2 binaries and the JD2 source mirror at https://github.com/mirror/jdownloader .

`tray-core` uses these dependencies normally. The JD2 adapter bundles the core and relocates these dependencies in its installable JAR. Only `jd2-adapter` compiles against the installed JD2/AppWork/JNA binaries.
