package com.druvu.lib.fx.prefs;

import com.druvu.lib.fx.util.Debounce;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.stage.Stage;
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
            stage.setWidth(prefs.getDouble(prefix + ".width", stage.getWidth()));
            stage.setHeight(prefs.getDouble(prefix + ".height", stage.getHeight()));
            if (prefs.contains(prefix + ".x") && prefs.contains(prefix + ".y")) {
                stage.setX(prefs.getDouble(prefix + ".x", 0));
                stage.setY(prefs.getDouble(prefix + ".y", 0));
            }
        }
        stage.setMaximized(prefs.getBoolean(prefix + ".maximized", false));
    }

    static void save(Stage stage, Prefs prefs, String prefix) {
        prefs.putBoolean(prefix + ".maximized", stage.isMaximized());
        // When maximized or full-screen, the stage's x/y/width/height are the screen's bounds; keep
        // the last windowed bounds so restoring un-maximized returns to a sensible window.
        if (!stage.isMaximized() && !stage.isFullScreen()) {
            prefs.putDouble(prefix + ".x", stage.getX());
            prefs.putDouble(prefix + ".y", stage.getY());
            prefs.putDouble(prefix + ".width", stage.getWidth());
            prefs.putDouble(prefix + ".height", stage.getHeight());
        }
    }
}
