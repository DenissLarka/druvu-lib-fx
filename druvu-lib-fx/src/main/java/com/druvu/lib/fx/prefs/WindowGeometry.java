package com.druvu.lib.fx.prefs;

import com.druvu.lib.fx.util.Debounce;
import java.util.List;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/**
 * Saves and restores a {@link Stage}'s position, size and maximized state to a {@link Prefs} store, so a window reopens
 * where the user left it. Call {@link #install} once, before showing the stage.
 *
 * <p>The geometry is saved whenever it changes (debounced, so a window drag costs one write, not hundreds) and once
 * more when the stage is hidden. Saving on hide alone is not enough: {@link javafx.application.Platform#exit()} - the
 * usual Quit menu item, and Cmd+Q on macOS - shuts the toolkit down without ever hiding the stage, so an app that quit
 * that way never remembered its window (measured on JavaFX 25; only the window's close button hides).
 *
 * <p>The saved geometry is applied again each time the stage is about to show: a JavaFX
 * {@link javafx.scene.control.Dialog} centres its window before every show ({@code HeavyweightDialog.show} calls
 * {@code centerOnScreen}), which would otherwise discard the restored position (measured on JavaFX 25).
 *
 * <p>A saved window never opens out of reach. Its saved bounds are checked against the screens there are now
 * ({@link WindowPlacement}): when its title bar or its bottom edge would be out of sight - a monitor unplugged since,
 * displays rearranged, a taskbar that grew - it moves onto a screen instead. A minimized window's bounds are not saved:
 * Windows parks a minimized window far off screen, and a quit while minimized would otherwise store that spot.
 *
 * <p>Keys are written under a prefix (default {@code "window"}), so one {@code Prefs} can remember several windows by
 * giving each a distinct prefix.
 */
public final class WindowGeometry {

    private static final String DEFAULT_PREFIX = "window";

    /** Long enough to fold a window drag into one write, short enough that a quit right after a move still lands. */
    private static final Duration SAVE_DELAY = Duration.millis(300);

    private WindowGeometry() {}

    /** Restores saved geometry now and saves it again whenever it changes and when the stage is hidden. */
    public static void install(Stage stage, Prefs prefs) {
        install(stage, prefs, DEFAULT_PREFIX);
    }

    /** As {@link #install(Stage, Prefs)}, with an explicit key prefix. */
    public static void install(Stage stage, Prefs prefs, String prefix) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(prefs, "prefs");
        Objects.requireNonNull(prefix, "prefix");
        restore(stage, prefs, prefix);
        // WINDOW_SHOWING fires inside show(), after a dialog's centerOnScreen and before the window appears.
        stage.addEventHandler(WindowEvent.WINDOW_SHOWING, event -> restore(stage, prefs, prefix));

        final Debounce pendingSave = new Debounce(SAVE_DELAY, () -> save(stage, prefs, prefix));
        final InvalidationListener geometryChanged = observable -> {
            // Only a showing stage has geometry worth keeping; whatever JavaFX does to the
            // properties of a hidden one is not the user's window.
            if (stage.isShowing()) {
                pendingSave.trigger();
            }
        };
        stage.xProperty().addListener(geometryChanged);
        stage.yProperty().addListener(geometryChanged);
        stage.widthProperty().addListener(geometryChanged);
        stage.heightProperty().addListener(geometryChanged);
        stage.maximizedProperty().addListener(geometryChanged);
        stage.showingProperty().addListener((observable, wasShowing, showing) -> {
            if (Boolean.FALSE.equals(showing)) {
                pendingSave.cancel();
                save(stage, prefs, prefix);
            }
        });
    }

    static void restore(Stage stage, Prefs prefs, String prefix) {
        if (prefs.contains(prefix + ".width") && prefs.contains(prefix + ".height")) {
            final double width = prefs.getDouble(prefix + ".width", stage.getWidth());
            final double height = prefs.getDouble(prefix + ".height", stage.getHeight());
            // A size that is not positive comes from a damaged or hand-edited file: ignored, the stage keeps its own.
            if (width > 0 && height > 0) {
                restoreBounds(stage, prefs, prefix, width, height);
            }
        }
        stage.setMaximized(prefs.getBoolean(prefix + ".maximized", false));
    }

    private static void restoreBounds(Stage stage, Prefs prefs, String prefix, double width, double height) {
        if (!prefs.contains(prefix + ".x") || !prefs.contains(prefix + ".y")) {
            stage.setWidth(width);
            stage.setHeight(height);
            return;
        }
        final Rectangle2D saved =
                new Rectangle2D(prefs.getDouble(prefix + ".x", 0), prefs.getDouble(prefix + ".y", 0), width, height);
        final Rectangle2D placed = WindowPlacement.placement(
                saved, usableAreas(), Screen.getPrimary().getVisualBounds());
        stage.setX(placed.getMinX());
        stage.setY(placed.getMinY());
        stage.setWidth(placed.getWidth());
        stage.setHeight(placed.getHeight());
    }

    /** Every screen's usable area: its bounds without the taskbar, the menu bar and the Dock. */
    private static List<Rectangle2D> usableAreas() {
        return Screen.getScreens().stream().map(Screen::getVisualBounds).toList();
    }

    static void save(Stage stage, Prefs prefs, String prefix) {
        prefs.putBoolean(prefix + ".maximized", stage.isMaximized());
        // When maximized or full-screen, the stage's x/y/width/height are the screen's bounds, and when minimized
        // Windows parks it far off screen; keep the last windowed bounds so a restore returns to a sensible window.
        if (!stage.isMaximized() && !stage.isFullScreen() && !stage.isIconified()) {
            prefs.putDouble(prefix + ".x", stage.getX());
            prefs.putDouble(prefix + ".y", stage.getY());
            prefs.putDouble(prefix + ".width", stage.getWidth());
            prefs.putDouble(prefix + ".height", stage.getHeight());
        }
    }
}
