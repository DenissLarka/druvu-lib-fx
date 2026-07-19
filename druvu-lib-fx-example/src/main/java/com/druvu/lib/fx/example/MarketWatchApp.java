package com.druvu.lib.fx.example;

import com.druvu.lib.fx.auth.Authenticator;
import com.druvu.lib.fx.auth.LoginPane;
import com.druvu.lib.fx.bus.Delivery;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.bus.Subscription;
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
import com.druvu.lib.fx.prefs.AppHome;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.prefs.WindowGeometry;
import com.druvu.lib.fx.status.StatusBarModel;
import com.druvu.lib.fx.util.FxThreads;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
 * the panel (re-docks it), releasing it hides the panel (undocks it); the toggle also follows the panel's own close
 * button.
 */
public final class MarketWatchApp extends Application {

    private static final Color ICON_FILL = Color.web("#d7e0ff"); // two-tone glyph fill
    private static final Color ICON_STROKE = Color.web("#4147d5"); // two-tone glyph outline
    private static final double ICON_SCALE = 1.83; // glyphs use a 14px viewBox
    private static final double TOGGLE_SIZE = 48;

    private final FxBus bus = new FxBus();
    private final FxExec exec = new FxExec(bus);
    private final MarketFeed feed = new MarketFeed(bus, exec);
    private final List<Subscription> shellSubscriptions = new ArrayList<>();
    private final List<Panel> panels = new ArrayList<>();

    private final Prefs prefs = Prefs.in(AppHome.of("market-watch"));

    private DockPane dockPane;
    private StatusBarModel statusBarModel;
    private Notifications notifications;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        FxThreads.requireFx();
        notifications = new Notifications(stage);

        final Scene scene = new Scene(new BorderPane(), 1280, 800);
        scene.setRoot(buildLoginPane(scene));
        stage.setTitle("Market Watch - druvu-lib-fx showcase");
        stage.setScene(scene);
        // Restore the window's saved position/size (and save it again when the app closes).
        WindowGeometry.install(stage, prefs);
        stage.show();
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
        dockPane = new DockPane();
        buildDockLayout();

        final BorderPane shell = new BorderPane(dockPane);
        shell.setTop(buildToolbar());
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
     * Docks the panels. The show position of each panel is deliberately a side (never CENTER): {@link DockPane#dock}
     * only honours CENTER as the very first dock, so a panel that is toggled off and back on re-docks to a side to
     * avoid being silently dropped.
     */
    private void buildDockLayout() {
        // Each panel gets a two-tone SVG glyph (14px viewBox) in the toolbar.
        panels.add(new Panel("Dashboard", new DashboardPage(bus).node(), DockPos.LEFT, MarketWatchApp::dashboardIcon));
        panels.add(new Panel("Instruments", loadInstrumentsPage(), DockPos.RIGHT, MarketWatchApp::instrumentsIcon));
        panels.add(new Panel(
                "Tasks", new TasksPage(bus, exec, notifications).node(), DockPos.BOTTOM, MarketWatchApp::tasksIcon));

        // Dashboard is docked FIRST, so CENTER is valid; the rest go to their sides.
        panels.get(0).node.dock(dockPane, DockPos.CENTER);
        for (int i = 1; i < panels.size(); i++) {
            final Panel panel = panels.get(i);
            panel.node.dock(dockPane, panel.showPos);
        }
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
            liveLabel.setStyle(live ? "-fx-text-fill: #1a7f37; -fx-font-weight: bold;" : "");
        }));

        toolBar.getItems().addAll(feedToggle, new Separator(), liveLabel);
        return toolBar;
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
                    // If it was dragged out to float, dispose that window first, so we re-dock the
                    // node cleanly instead of leaving an empty floating stage.
                    if (panel.node.isFloating()) {
                        panel.node.close();
                    }
                    panel.node.dock(dockPane, panel.showPos);
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
        statusBarModel = new StatusBarModel(bus);

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

    /** A dock panel plus the side it re-docks to when shown (never CENTER) and its toolbar glyph. */
    private static final class Panel {
        private final String title;
        private final DockNode node;
        private final DockPos showPos;
        private final Supplier<Node> iconFactory;

        Panel(String title, Node content, DockPos showPos, Supplier<Node> iconFactory) {
            this.title = title;
            this.node = new DockNode(content, title);
            this.showPos = showPos;
            this.iconFactory = iconFactory;
        }

        Node icon() {
            return iconFactory.get();
        }
    }
}
