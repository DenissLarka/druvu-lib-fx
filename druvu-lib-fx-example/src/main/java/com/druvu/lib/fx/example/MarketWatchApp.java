package com.druvu.lib.fx.example;

import com.druvu.lib.fx.auth.Authenticator;
import com.druvu.lib.fx.auth.LoginPane;
import com.druvu.lib.fx.bus.Delivery;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.bus.Subscription;
import com.druvu.lib.fx.dock.DockLayout;
import com.druvu.lib.fx.dock.DockLayoutPersistence;
import com.druvu.lib.fx.dock.DockNode;
import com.druvu.lib.fx.dock.DockPane;
import com.druvu.lib.fx.dock.DockPos;
import com.druvu.lib.fx.example.instruments.InstrumentsController;
import com.druvu.lib.fx.example.market.MarketFeed;
import com.druvu.lib.fx.example.pages.DashboardPage;
import com.druvu.lib.fx.example.pages.TasksPage;
import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.exec.TaskEvent;
import com.druvu.lib.fx.notify.Notifications;
import com.druvu.lib.fx.os.DesktopHooks;
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.prefs.WindowGeometry;
import com.druvu.lib.fx.status.StatusBarModel;
import com.druvu.lib.fx.theme.FxTheme;
import com.druvu.lib.fx.theme.ThemeManager;
import com.druvu.lib.fx.util.FxThreads;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.stage.Stage;

/**
 * "Market Watch" - the druvu-lib-fx showcase: a fake-data market console laid out as a docking workspace.
 *
 * <p>The app OWNS Application, Stage, Scene and layout - the toolkit only supplies bus, exec, thread primitives, the
 * vendored dock, the {@link LoginPane} and the {@link StatusBarModel}. It opens on a login screen (demo / demo); a
 * successful sign-in swaps the scene root to the docking workspace. Pages demonstrate both styles: code-first
 * (Dashboard, Tasks) and FXML (Instruments). The top toolbar carries one icon toggle per dock panel: pressing it shows
 * the panel again where it was, releasing it hides the panel; the toggle also follows the panel's own close button.
 *
 * <p>The app remembers itself between launches, all in {@code ~/.druvu.com/market-watch/preferences.properties}: the
 * window's position and size ({@link WindowGeometry}), the dock workspace - splits, divider positions, hidden panels
 * ({@link DockLayoutPersistence}) - and the theme ({@link ThemeManager}).
 */
public final class MarketWatchApp extends Application {

    private static final Color ICON_FILL = Color.web("#d7e0ff"); // two-tone glyph fill
    private static final Color ICON_STROKE = Color.web("#4147d5"); // two-tone glyph outline
    private static final double ICON_SCALE = 1.83; // glyphs use a 14px viewBox
    private static final double TOGGLE_SIZE = 48;

    /**
     * The fresh-install workspace as a value: Dashboard beside Instruments, Tasks below. A layout saved by an earlier
     * run takes over right after it is applied (see {@link #buildDockLayout}).
     */
    private static final DockLayout DEFAULT_LAYOUT =
            DockLayout.parse("V{0.700 H{0.600 dashboard,0.400 instruments},0.300 tasks}");

    private final FxBus bus = new FxBus();
    private final FxExec exec = new FxExec(bus);
    private final MarketFeed feed = new MarketFeed(bus, exec);
    private final List<Subscription> shellSubscriptions = new ArrayList<>();
    private final List<Panel> panels = new ArrayList<>();

    private final Prefs prefs = Prefs.in(AppHome.of("market-watch"));
    private final ThemeManager themeManager = new ThemeManager(prefs);

    private DockPane dockPane;
    private StatusBarModel statusBarModel;
    private Notifications notifications;

    // Filled in by start(); the desktop hooks are registered before any instance exists (see main).
    private static final AtomicReference<MarketWatchApp> RUNNING = new AtomicReference<>();

    public static void main(String[] args) {
        // BEFORE launch() on purpose. On macOS the application menu goes to whoever builds it first: register here and
        // AWT installs About/Settings into it; register in start() and Glass has already built a bare Hide/Quit menu,
        // after which the entries never appear even though registration "succeeded". See DesktopHooks.
        DesktopHooks.onAbout(() -> onRunningApp(MarketWatchApp::showAbout));
        DesktopHooks.onPreferences(
                () -> onRunningApp(app -> app.notifications.info("Preferences: the theme picker is in the toolbar.")));
        DesktopHooks.onOpenFiles(paths -> onRunningApp(app ->
                app.notifications.info("Asked to open " + paths.getFirst().getFileName())));
        // Returning false here would cancel the quit - where an app with unsaved work puts its prompt.
        DesktopHooks.onQuit(() -> true);

        launch(args);
    }

    /** Runs an action against the started app, or drops it if the UI is not up yet. */
    private static void onRunningApp(Consumer<MarketWatchApp> action) {
        final MarketWatchApp app = RUNNING.get();
        if (app != null && app.notifications != null) {
            action.accept(app);
        }
    }

    private void showAbout() {
        notifications.info("Market Watch - the druvu-lib-fx showcase.");
    }

    @Override
    public void start(Stage stage) {
        FxThreads.requireFx();
        // Theme first, so the login screen already wears last session's choice. The toolkit never
        // applies a theme by itself - setUserAgentStylesheet restyles the whole JVM, so it is the
        // app's call, not the library's.
        themeManager.applyStored();
        notifications = new Notifications(stage);

        final Scene scene = new Scene(new BorderPane(), 1280, 800);
        scene.setRoot(buildLoginPane(scene));
        stage.setTitle("Market Watch - druvu-lib-fx showcase");
        stage.setScene(scene);
        // The desktop hooks were registered in main(); hand them a live app to talk to.
        RUNNING.set(this);

        // Restore the window's saved position/size; the kit saves it again on every move or resize.
        WindowGeometry.install(stage, prefs);
        stage.show();
    }

    /**
     * Menus in the OS menu bar. {@code setUseSystemMenuBar(true)} is the whole trick on macOS and a harmless no-op
     * elsewhere, where the bar simply stays inside the window - so there is no platform branch to write, only the
     * question of where About belongs.
     */
    private MenuBar buildMenuBar() {
        final MenuItem quit = new MenuItem("Quit");
        quit.setOnAction(event -> Platform.exit());
        final Menu file = new Menu("File", null, quit);

        // About goes in Help on every platform: AWT's application-menu About does not surface in a JavaFX app, so this
        // is the only place it is actually reachable. See DesktopHooks' known-limitation note.
        final MenuItem about = new MenuItem("About Market Watch");
        about.setOnAction(event -> showAbout());
        final Menu help = new Menu("Help", null, about);

        final MenuBar menuBar = new MenuBar(file, help);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    @Override
    public void stop() {
        feed.stop();
        if (notifications != null) {
            notifications.close();
        }
        if (statusBarModel != null) {
            statusBarModel.close();
        }
        shellSubscriptions.forEach(Subscription::close);
        exec.close();
    }

    /**
     * The login gate. The demo {@link Authenticator} accepts {@code demo / demo} after a short pretend-network delay
     * (it runs off the FX thread, so blocking here is fine); anything else is rejected with a message the pane shows.
     * On success the toolkit calls {@link #showWorkspace}.
     */
    private Parent buildLoginPane(Scene scene) {
        final Authenticator<String> authenticator = (username, password) -> {
            Thread.sleep(400);
            if ("demo".equals(username) && "demo".equals(new String(password))) {
                return username;
            }
            throw new IllegalArgumentException("Invalid credentials - try demo / demo");
        };
        return new LoginPane<>(exec, authenticator, user -> showWorkspace(scene, user));
    }

    /** Builds the docking workspace and swaps it in as the scene root once the user has signed in. */
    private void showWorkspace(Scene scene, String user) {
        // Before buildDockLayout(), which starts the instruments load: a model built afterwards would miss that
        // task's Started and still see its Finished. The kit floors the count at zero either way, but the honest
        // fix is to be listening before anything can fire - otherwise the strip just under-reports.
        statusBarModel = new StatusBarModel(bus);

        dockPane = new DockPane();
        buildDockLayout();

        final BorderPane shell = new BorderPane(dockPane);
        // The menu bar must be in the scene graph for macOS to adopt it into the screen menu bar; on other platforms
        // this VBox is what shows it above the toolbar.
        shell.setTop(new VBox(buildMenuBar(), buildToolbar()));
        shell.setBottom(buildStatusStrip(user));
        scene.setRoot(shell);

        // Surface failed background tasks as error toasts (try the Tasks page's "Failing task").
        shellSubscriptions.add(bus.subscribe(TaskEvent.class, Delivery.FX, event -> {
            if (event instanceof TaskEvent.Failed failed) {
                notifications.error(
                        failed.title() + " failed: " + failed.error().getMessage());
            }
        }));
        notifications.success("Signed in as " + user);
    }

    /**
     * Docks the panels: the declared {@link #DEFAULT_LAYOUT} first, then whatever an earlier run saved takes over.
     * {@link DockLayoutPersistence} keeps the whole workspace in the app's preferences under {@code dock.*}, saving
     * every change (debounced, so a divider drag costs one write). Panels are matched by their JavaFX ids, set in
     * {@link Panel}.
     */
    private void buildDockLayout() {
        // Each panel gets a two-tone SVG glyph (14px viewBox) in the toolbar.
        panels.add(new Panel(
                "dashboard", "Dashboard", new DashboardPage(bus).node(), DockPos.LEFT, MarketWatchApp::dashboardIcon));
        panels.add(new Panel(
                "instruments", "Instruments", loadInstrumentsPage(), DockPos.RIGHT, MarketWatchApp::instrumentsIcon));
        panels.add(new Panel(
                "tasks",
                "Tasks",
                new TasksPage(bus, exec, notifications).node(),
                DockPos.BOTTOM,
                MarketWatchApp::tasksIcon));

        // The fresh-install layout; the saved one, if any, takes over right below.
        final List<DockNode> nodes = panels.stream().map(panel -> panel.node).toList();
        dockPane.apply(DEFAULT_LAYOUT, nodes);
        DockLayoutPersistence.install(dockPane, prefs, nodes);
    }

    private ToolBar buildToolbar() {
        final ToolBar toolBar = new ToolBar();
        for (Panel panel : panels) {
            toolBar.getItems().add(buildPanelToggle(panel));
        }
        toolBar.getItems().add(new Separator());

        final ToggleButton feedToggle = new ToggleButton("Market feed");
        feedToggle.setOnAction(e -> {
            if (feedToggle.isSelected()) {
                feed.start();
            } else {
                feed.stop();
            }
        });

        final Label liveLabel = new Label("idle");
        shellSubscriptions.add(bus.subscribe(TaskEvent.class, Delivery.FX, event -> {
            if (!MarketFeed.TASK_TITLE.equals(event.title())) {
                return;
            }
            final boolean live = event instanceof TaskEvent.Started;
            liveLabel.setText(live ? "LIVE" : "idle");
            // A theme colour, not a literal green: inline styles resolve looked-up colours too.
            liveLabel.setStyle(live ? "-fx-text-fill: -color-success-fg; -fx-font-weight: bold;" : "");
        }));

        toolBar.getItems().addAll(feedToggle, new Separator(), liveLabel, new Separator(), buildThemePicker());
        return toolBar;
    }

    /**
     * The theme picker. Switching is instant and process-wide, and {@link ThemeManager} writes the choice straight to
     * the preference file - so the next launch opens on the same theme (see {@link #start}).
     */
    private ComboBox<FxTheme> buildThemePicker() {
        final ComboBox<FxTheme> picker = new ComboBox<>();
        picker.getItems().setAll(FxTheme.values());
        picker.setValue(themeManager.current());
        picker.valueProperty().addListener((_, _, theme) -> {
            if (theme != null) {
                themeManager.apply(theme);
            }
        });
        picker.setTooltip(new Tooltip("Theme (remembered between launches)"));
        return picker;
    }

    /**
     * A toolbar toggle that shows/hides one dock panel. Selected == panel visible. The listener on the panel's
     * {@code dockedProperty} keeps the toggle honest when the panel is closed by its own title-bar button or dragged
     * out to float (both undock it).
     */
    private ToggleButton buildPanelToggle(Panel panel) {
        final ToggleButton toggle = new ToggleButton();
        toggle.setGraphic(panel.icon());
        toggle.setPrefSize(TOGGLE_SIZE, TOGGLE_SIZE);
        toggle.setMinSize(TOGGLE_SIZE, TOGGLE_SIZE);
        toggle.setSelected(panel.node.isDocked());
        // The button is icon-only now, so the tooltip carries the panel name.
        toggle.setTooltip(new Tooltip("Show/hide " + panel.title));

        panel.node.dockedProperty().addListener((_, _, docked) -> toggle.setSelected(docked));

        toggle.setOnAction(e -> {
            if (toggle.isSelected()) {
                if (!panel.node.isDocked()) {
                    // If it was dragged out to float, dispose that window first, so it re-docks cleanly
                    // instead of leaving an empty floating stage.
                    if (panel.node.isFloating()) {
                        panel.node.close();
                    }
                    // Back where it was, beside its old neighbours with its old share; the panel's own
                    // side only when none of them is docked any more.
                    dockPane.redock(panel.node, panel.showPos);
                }
            } else if (panel.node.isDocked()) {
                panel.node.close();
            }
        });
        return toggle;
    }

    // Toolbar glyphs. Each is a two-tone icon (14px viewBox) assembled from SVG <path> data: filled
    // shapes in ICON_FILL, outlines/lines in ICON_STROKE. Icon provenance is tracked for open-sourcing.

    private static Node dashboardIcon() {
        return icon(
                filledStroke("M13.25 13.5h-2.5v-7a.5.5 0 0 1 .5-.5h1.5a.5.5 0 0 1 .5.5zm-5 0h-2.5V8a.5.5"
                        + " 0 0 1 .5-.5h1.5a.5.5 0 0 1 .5.5zm-5 0H.75v-4a.5.5 0 0 1 .5-.5h1.5a.5.5 0 0 1 .5.5z"),
                stroke("M1.24 6.54l11.5-5.23M10.59.5l2.15.81l-.8 2.15"));
    }

    private static Node instrumentsIcon() {
        return icon(stroke("M1 3a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1m3.5-.5h9M1 7.5a.5.5 0 1 0 0-1a.5.5 0 0 0"
                + " 0 1M4.5 7h9M1 12a.5.5 0 1 0 0-1a.5.5 0 0 0 0 1m3.5-.5h9"));
    }

    private static Node tasksIcon() {
        return icon(
                fill("M11.719 12.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1h6l4 4z"),
                stroke("M11.719 12.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1h5.586a1 1 0 0 1"
                        + " .707.293l3.414 3.414a1 1 0 0 1 .293.707zM6.777 6.375h2.5m-2.5 3.469h2.5"),
                stroke("m2.91 9.787l.838.838L5.145 8.67M2.91 6.256l.838.838l1.397-1.955"));
    }

    /** Groups a glyph's paths and scales the 14px artwork up to toolbar size. */
    private static Node icon(SVGPath... parts) {
        // A Group so the scaled size shows in layout bounds (setScale alone leaves them unscaled).
        final Group group = new Group(parts);
        group.setScaleX(ICON_SCALE);
        group.setScaleY(ICON_SCALE);
        return group;
    }

    private static SVGPath path(String content) {
        final SVGPath path = new SVGPath();
        path.setContent(content);
        return path;
    }

    /** A filled shape with no outline (e.g. the document body). */
    private static SVGPath fill(String content) {
        final SVGPath path = path(content);
        path.setFill(ICON_FILL);
        return path;
    }

    /** An outline or line with no fill, round caps and joins. */
    private static SVGPath stroke(String content) {
        final SVGPath path = path(content);
        path.setFill(Color.TRANSPARENT);
        path.setStroke(ICON_STROKE);
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return path;
    }

    /** A shape that is both filled and outlined (e.g. the chart bars). */
    private static SVGPath filledStroke(String content) {
        final SVGPath path = stroke(content);
        path.setFill(ICON_FILL);
        return path;
    }

    /**
     * The status strip binds straight to a kit {@link StatusBarModel} - the task-counting and last-activity logic that
     * used to live here is now the model's job; the view just binds.
     */
    private HBox buildStatusStrip(String user) {
        final Label userLabel = new Label("signed in: " + user);
        final Label tasksLabel = new Label();
        tasksLabel.textProperty().bind(statusBarModel.runningTasksProperty().asString("tasks: %d"));
        final Label lastLabel = new Label();
        lastLabel.textProperty().bind(statusBarModel.messageProperty());

        final HBox strip = new HBox(16, userLabel, new Separator(Orientation.VERTICAL), tasksLabel, lastLabel);
        strip.setPadding(new Insets(4, 8, 4, 8));
        return strip;
    }

    private Parent loadInstrumentsPage() {
        try {
            final FXMLLoader loader = new FXMLLoader(InstrumentsController.class.getResource("instruments.fxml"));
            final Parent root = loader.load();
            loader.<InstrumentsController>getController().init(exec);
            return root;
        } catch (IOException ex) {
            throw new UncheckedIOException("instruments.fxml failed to load", ex);
        }
    }

    /**
     * A dock panel: the id its place is saved under, its title, the side it goes back to when none of its old
     * neighbours is docked any more, and its toolbar glyph.
     */
    private static final class Panel {
        private final String title;
        private final DockNode node;
        private final DockPos showPos;
        private final Supplier<Node> iconFactory;

        Panel(String id, String title, Node content, DockPos showPos, Supplier<Node> iconFactory) {
            this.title = title;
            this.node = new DockNode(content, title);
            // The key the kit files this panel's place under (dock.* in the preferences); never rename it lightly,
            // or a saved workspace loses the panel.
            this.node.setId(id);
            this.showPos = showPos;
            this.iconFactory = iconFactory;
        }

        Node icon() {
            return iconFactory.get();
        }
    }
}
