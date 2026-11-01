/**
 * druvu-lib-fx - JavaFX application toolkit.
 *
 * <p>JavaFX modules are resolved from the JDK (a JavaFX-bundled build such as Azul Zulu FX); there are deliberately no
 * org.openjfx artifact dependencies.
 */
module com.druvu.lib.fx {
    // The updater is toolkit-free; com.druvu.lib.fx.update wraps it, and an app names its release source itself.
    requires transitive com.druvu.lib.fx.updater;
    requires transitive javafx.graphics;
    requires transitive javafx.controls;
    // Transitive on purpose: com.druvu.lib.fx.theme is only a menu over AtlantaFX, so apps must be
    // able to reach atlantafx.base.theme.Styles/Tweaks without re-declaring the dependency.
    requires transitive atlantafx.base;

    // java.awt.Desktop backs com.druvu.lib.fx.os.DesktopHooks (macOS application menu + open-file
    // events). STATIC on purpose: java.desktop costs ~34 MB in a jlink image, so an app opts in with
    // its own 'requires java.desktop' instead of every consumer paying for AWT. DesktopHooks degrades
    // to a warn-once no-op when the app has not.
    requires static java.desktop;

    // The JDK's own logging backend behind System.Logger: the kit requires it so that every runtime image built on the
    // kit carries it, and AppLogs sets it up. No logging dependency ships with an app.
    requires java.logging;

    exports com.druvu.lib.fx.auth;
    exports com.druvu.lib.fx.bus;
    exports com.druvu.lib.fx.dock;
    exports com.druvu.lib.fx.exec;
    exports com.druvu.lib.fx.notify;
    exports com.druvu.lib.fx.os;
    exports com.druvu.lib.fx.prefs;
    exports com.druvu.lib.fx.status;
    exports com.druvu.lib.fx.theme;
    exports com.druvu.lib.fx.update;
    exports com.druvu.lib.fx.util;
}
