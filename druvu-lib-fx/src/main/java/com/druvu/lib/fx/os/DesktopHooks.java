package com.druvu.lib.fx.os;

import com.druvu.lib.fx.util.FxThreads;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wires an app into the desktop environment's <em>application</em> events - on macOS, the bold app-name menu (About,
 * Settings…, Quit) and the Finder/Dock "open this document with me" event.
 *
 * <h2>Why this exists</h2>
 *
 * <p>JavaFX has no API for the application menu, and the third-party libraries that offer one (NSMenuFX and friends)
 * reach it through JNA - an automatic module with bundled native libraries, which {@code jlink} refuses. That rules
 * them out for any app shipped through the druvu installer pipeline. The JDK has covered this since Java 9 (JEP 272)
 * via {@link java.awt.Desktop}; this class is a thin, FX-safe front for the parts a desktop app actually wants.
 *
 * <p>What it absorbs, and why you want it absorbed:
 *
 * <ul>
 *   <li><b>Thread hop.</b> AWT delivers these callbacks on its own event thread, never the FX thread. Every action
 *       registered here is re-dispatched through {@link FxThreads#onFx}, so handlers may touch the scene graph.
 *   <li><b>The quit trap.</b> {@code setQuitHandler} hands you a response object that <em>must</em> be answered; forget
 *       it and Cmd-Q silently does nothing. {@link #onQuit(BooleanSupplier)} always answers.
 *   <li><b>Absent support.</b> Every method is a no-op returning {@code false} where the platform (or the module graph)
 *       does not provide the hook, so the same call site is safe on Windows and Linux.
 * </ul>
 *
 * <h2>The menu bar itself is not here</h2>
 *
 * <p>Moving an app's own menus into the macOS screen menu bar needs no toolkit help - it is one standard JavaFX call,
 * and a harmless no-op on platforms that keep menus in-window:
 *
 * <pre>{@code
 * MenuBar menuBar = new MenuBar(fileMenu, viewMenu, helpMenu);
 * menuBar.setUseSystemMenuBar(true); // macOS: screen menu bar; elsewhere: stays in the window
 * }</pre>
 *
 * <p>The bar must be in the scene graph for macOS to pick it up - {@code setUseSystemMenuBar} on a detached
 * {@code MenuBar} does nothing.
 *
 * <p><b>And that leaves a trap.</b> Because the node stays in the graph, it keeps its layout box: measured in a real
 * themed app, an adopted {@code MenuBar} still occupies <b>8&nbsp;px</b> at the top of the window and paints the
 * theme's {@code .menu-bar} background and bottom border - a stray line a millimetre under the title bar that looks
 * like a border on whatever sits below it. The height comes from the theme's padding, so a bare {@code MenuBar} outside
 * a theme reports {@code height = 0.0} and hides the problem. Collapse it where the menus were adopted, but never
 * remove it:
 *
 * <pre>{@code
 * if (macOS) {
 *     menuBar.getStyleClass().add("system-menu-bar-host"); // CSS: padding 0, transparent, min/pref/max height 0
 * }
 * }</pre>
 *
 * <h2>The app's name in the menu is not here either</h2>
 *
 * <p>An unbundled JVM shows up as <b>"java"</b> in the macOS menu bar, and nothing here changes that - the title comes
 * from the application bundle, not from any API. {@code jpackage} writes {@code CFBundleName} from the app name, so a
 * packaged app is correct with no code and no flags.
 *
 * <p>There is no development-time equivalent: on JavaFX 25 both {@code -Xdock:name} and
 * {@code -Dapple.awt.application.name} were measured to leave the menu title as "java" (they address the Dock tile and
 * AWT respectively, while the menu bar is built by Glass from the process name). A {@code mvn javafx:run} session
 * therefore always says "java" - cosmetic, and gone the moment the app is packaged.
 *
 * <h2>Module requirement</h2>
 *
 * <p>The toolkit declares {@code requires static java.desktop}: the compile-time dependency without the runtime one,
 * because {@code java.desktop} adds roughly <b>34 MB</b> to a {@code jlink} image and most apps should not pay that. An
 * app that wants these hooks opts in from its own descriptor:
 *
 * <pre>{@code
 * module com.example.app {
 *     requires com.druvu.lib.fx;
 *     requires java.desktop; // opt in to DesktopHooks
 * }
 * }</pre>
 *
 * <p>Without it every method here returns {@code false} and logs one warning - the same degraded-case contract
 * {@code ThemeManager} uses for an unapplied theme.
 *
 * <h2>The application menu appears only in a PACKAGED app - not in a dev run</h2>
 *
 * <p>The macOS application menu belongs to whoever builds it first, and that differs between a development run and a
 * shipped bundle. <b>Measured on JavaFX 25, same code, same machine:</b>
 *
 * <pre>
 * mvn javafx:run / java --module   -&gt; Hide X, Hide Others, Show All, Quit X        (JavaFX Glass built it)
 * packaged .app bundle             -&gt; About X, Services, Hide X, ..., Quit X       (AWT built it)
 * </pre>
 *
 * <p>The bundled figure is from a real druvu app shipped through the installer pipeline, whose launcher touches no AWT
 * API and passes no JVM options - so <b>the AWT application menu, About included, comes for free once the app is a
 * bundle</b>. {@link #onAbout} and {@link #onPreferences} are therefore worth registering: they are dormant during
 * development and live in the product.
 *
 * <p>Plan the UI around that. Registering About here and <em>also</em> adding it to a Help menu would show it twice in
 * the shipped app; leaving it only here means it is unreachable while developing. Either is defensible - just choose
 * deliberately, and do not conclude from a dev run that the hook is broken.
 *
 * <p>One naming wart to expect: the entries read "About <i>MainClass</i>" (AWT names them after the main class), while
 * the menu bar title itself comes from {@code CFBundleName}. Pass {@code -Dapple.awt.application.name=Your App} as a
 * packaging JVM option to make them agree.
 *
 * <p>{@link #onQuit} and {@link #onOpenFiles} are different in kind - they are application <em>events</em>, not menu
 * items, so they do not depend on who owns the menu.
 *
 * <h2>Usage</h2>
 *
 * <p>Register from {@code main} before {@code Application.launch}, so a document handed over at launch is not missed.
 * The {@code Application} instance does not exist yet at that point, so route through a holder that {@code start} fills
 * in:
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     DesktopHooks.onOpenFiles(paths -> running(app -> app.open(paths)));   // BEFORE launch
 *     launch(args);
 * }
 * }</pre>
 *
 * <p>Registering replaces any previously registered handler for the same event - the JDK keeps exactly one per event,
 * not a listener list. Actions are delivered on the FX thread, so an action must not run before the toolkit is up.
 *
 * <p>One more caveat for {@link #onOpenFiles}: macOS only sends open-file events to a real application <b>bundle</b>.
 * An unbundled {@code mvn javafx:run} process is never a Finder file-association target, so that hook cannot be
 * exercised until the app is packaged - it is a dist-phase verification, not a dev-run one.
 */
public final class DesktopHooks {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopHooks.class);

    private static final String JAVA_DESKTOP = "java.desktop";

    // The missing-module warning is a configuration mistake, not a per-call event: say it once.
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private DesktopHooks() {}

    /**
     * Whether the desktop hooks can be installed at all - a cheap pre-flight for callers that want to decide up front
     * (say, whether to build an in-window Help menu) instead of reacting to each {@code false}.
     *
     * <p>False on Windows and Linux, where the application-menu concept does not exist, and false when the app has not
     * opted in to {@code java.desktop}.
     *
     * @return true when at least one of the application-menu hooks is available
     */
    public static boolean isSupported() {
        return desktopReadable() && Hooks.anyAppEventSupported();
    }

    /**
     * Handles "About <i>App</i>" from the application menu.
     *
     * @param action what to show; runs on the FX thread. Must not be null.
     * @return true when the handler was installed, false when the platform has no application menu
     */
    public static boolean onAbout(Runnable action) {
        Objects.requireNonNull(action, "action");
        return desktopReadable() && Hooks.about(action);
    }

    /**
     * Handles "Settings…" / "Preferences…" from the application menu.
     *
     * @param action what to open; runs on the FX thread. Must not be null.
     * @return true when the handler was installed, false when the platform has no application menu
     */
    public static boolean onPreferences(Runnable action) {
        Objects.requireNonNull(action, "action");
        return desktopReadable() && Hooks.preferences(action);
    }

    /**
     * Handles "Quit <i>App</i>" (Cmd-Q) and the OS asking the app to shut down, with a veto.
     *
     * <p>The supplier runs on the FX thread and decides: {@code true} lets the quit proceed, {@code false} cancels it -
     * which is how an unsaved-changes prompt gets to say "no". Either way the OS is answered, so the app never hangs
     * mid-quit.
     *
     * <p>Note this is the OS-initiated path only. It does not fire for a window close, so it complements
     * {@code Stage.setOnCloseRequest} rather than replacing it; an app with something to lose usually points both at
     * the same check.
     *
     * @param allowQuit decides whether to quit; runs on the FX thread. Must not be null.
     * @return true when the handler was installed, false when the platform has no quit event
     */
    public static boolean onQuit(BooleanSupplier allowQuit) {
        Objects.requireNonNull(allowQuit, "allowQuit");
        return desktopReadable() && Hooks.quit(allowQuit);
    }

    /**
     * Handles documents opened from outside the app - double-clicked in Finder, dropped on the Dock icon, or passed by
     * "Open With".
     *
     * <p>This is what makes a file association feel native rather than only launching the app. It needs the association
     * itself to be declared by the installer; the handler is the app-side half.
     *
     * @param action receives the files, never empty; runs on the FX thread. Must not be null.
     * @return true when the handler was installed, false when the platform delivers files as launch arguments instead
     */
    public static boolean onOpenFiles(Consumer<List<Path>> action) {
        Objects.requireNonNull(action, "action");
        return desktopReadable() && Hooks.openFiles(action);
    }

    /**
     * Whether this module may touch {@code java.awt.*} at runtime.
     *
     * <p>{@code requires static java.desktop} gives no runtime readability edge on its own: the app must resolve
     * {@code java.desktop} too. Checking that before loading {@link Hooks} is what keeps this class safe to load in an
     * image built without AWT - {@code &&} short-circuits, so the AWT-referencing code is never linked.
     */
    private static boolean desktopReadable() {
        final Optional<Module> desktop = ModuleLayer.boot().findModule(JAVA_DESKTOP);
        // On the class path this module is unnamed, which reads everything - so this is really the modular check.
        final boolean readable =
                desktop.isPresent() && DesktopHooks.class.getModule().canRead(desktop.get());
        if (!readable && WARNED.compareAndSet(false, true)) {
            LOG.warn(
                    "Desktop hooks unavailable: module {} is not readable from {}. Add 'requires java.desktop'"
                            + " to your module-info to enable the application menu and open-file events.",
                    JAVA_DESKTOP,
                    DesktopHooks.class.getModule().getName());
        }
        return readable;
    }

    /**
     * Everything that names an AWT type, isolated in its own class file.
     *
     * <p>Class loading resolves references lazily, so keeping the {@code java.awt} vocabulary out of
     * {@link DesktopHooks} means that class stays loadable where AWT is absent. Only a caller that has already passed
     * {@link #desktopReadable()} ever triggers this one.
     */
    private static final class Hooks {

        private Hooks() {}

        static boolean anyAppEventSupported() {
            return supports(java.awt.Desktop.Action.APP_ABOUT)
                    || supports(java.awt.Desktop.Action.APP_PREFERENCES)
                    || supports(java.awt.Desktop.Action.APP_QUIT_HANDLER)
                    || supports(java.awt.Desktop.Action.APP_OPEN_FILE);
        }

        static boolean about(Runnable action) {
            if (!supports(java.awt.Desktop.Action.APP_ABOUT)) {
                return false;
            }
            java.awt.Desktop.getDesktop().setAboutHandler(event -> FxThreads.onFx(action));
            return true;
        }

        static boolean preferences(Runnable action) {
            if (!supports(java.awt.Desktop.Action.APP_PREFERENCES)) {
                return false;
            }
            java.awt.Desktop.getDesktop().setPreferencesHandler(event -> FxThreads.onFx(action));
            return true;
        }

        static boolean quit(BooleanSupplier allowQuit) {
            if (!supports(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
                return false;
            }
            java.awt.Desktop.getDesktop()
                    .setQuitHandler((event, response) -> FxThreads.onFx(() -> {
                        // The response must be answered on some thread or the OS waits forever; answering from the FX
                        // thread is fine, QuitResponse is not thread-confined.
                        if (allowQuit.getAsBoolean()) {
                            response.performQuit();
                        } else {
                            response.cancelQuit();
                        }
                    }));
            return true;
        }

        static boolean openFiles(Consumer<List<Path>> action) {
            if (!supports(java.awt.Desktop.Action.APP_OPEN_FILE)) {
                return false;
            }
            java.awt.Desktop.getDesktop().setOpenFileHandler(event -> {
                // Snapshot off the AWT thread: the event is not guaranteed to outlive the callback.
                final List<Path> paths =
                        event.getFiles().stream().map(java.io.File::toPath).toList();
                if (!paths.isEmpty()) {
                    FxThreads.onFx(() -> action.accept(paths));
                }
            });
            return true;
        }

        /**
         * Both halves matter: a headless JVM has no {@code Desktop} at all, and a {@code Desktop} that exists still
         * reports per-action support (the {@code APP_*} events are macOS-only in practice).
         */
        private static boolean supports(java.awt.Desktop.Action action) {
            return java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(action);
        }
    }
}
