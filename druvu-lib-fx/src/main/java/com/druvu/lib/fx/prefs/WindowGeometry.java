package com.druvu.lib.fx.prefs;

import java.util.Objects;

import javafx.stage.Stage;

/**
 * Saves and restores a {@link Stage}'s position, size and maximized state to a {@link Prefs} store,
 * so a window reopens where the user left it. Call {@link #install} once, before showing the stage.
 *
 * <p>Keys are written under a prefix (default {@code "window"}), so one {@code Prefs} can remember
 * several windows by giving each a distinct prefix.
 */
public final class WindowGeometry {

	private static final String DEFAULT_PREFIX = "window";

	private WindowGeometry() {
	}

	/** Restores saved geometry now and saves it again whenever the stage is hidden. */
	public static void install(Stage stage, Prefs prefs) {
		install(stage, prefs, DEFAULT_PREFIX);
	}

	/** As {@link #install(Stage, Prefs)}, with an explicit key prefix. */
	public static void install(Stage stage, Prefs prefs, String prefix) {
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(prefs, "prefs");
		Objects.requireNonNull(prefix, "prefix");
		restore(stage, prefs, prefix);
		stage.showingProperty().addListener((observable, wasShowing, showing) -> {
			if (Boolean.FALSE.equals(showing)) {
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
		// When maximized, the stage's x/y/width/height are the maximized bounds; keep the last
		// non-maximized bounds so restoring un-maximized returns to a sensible window.
		if (!stage.isMaximized()) {
			prefs.putDouble(prefix + ".x", stage.getX());
			prefs.putDouble(prefix + ".y", stage.getY());
			prefs.putDouble(prefix + ".width", stage.getWidth());
			prefs.putDouble(prefix + ".height", stage.getHeight());
		}
	}
}
