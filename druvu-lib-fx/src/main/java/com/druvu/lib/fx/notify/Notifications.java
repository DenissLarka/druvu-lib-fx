package com.druvu.lib.fx.notify;

import java.util.Objects;

import com.druvu.lib.fx.util.FxThreads;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Transient "toast" notifications stacked at the bottom-right of a window. Each toast fades out after
 * a few seconds; several stack upward. Backed by a single {@link Popup} anchored to the owner window,
 * so the app does not have to reserve any layout space or restructure its scene.
 *
 * <p>All methods must be called on the JavaFX thread.
 */
public final class Notifications {

	/** Severity of a toast, which selects its colour. */
	public enum Level {
		INFO("#37474f"),
		SUCCESS("#1a7f37"),
		WARNING("#b26a00"),
		ERROR("#b00020");

		private final String color;

		Level(String color) {
			this.color = color;
		}
	}

	private static final Duration DEFAULT_DURATION = Duration.seconds(4);
	private static final Duration FADE = Duration.millis(250);
	private static final double MARGIN = 16;

	private final Window owner;
	private final VBox stack = new VBox(8);
	private final Popup popup = new Popup();

	/**
	 * @param owner the window toasts appear over (typically the primary {@code Stage})
	 */
	public Notifications(Window owner) {
		this.owner = Objects.requireNonNull(owner, "owner");
		stack.setAlignment(Pos.BOTTOM_RIGHT);
		popup.getContent().add(stack);
		popup.setAutoFix(false);
		// The anchor is the content's bottom-right corner, so the stack grows up-left from a fixed
		// bottom-right point and stays pinned there as toasts are added and removed.
		popup.setAnchorLocation(PopupWindow.AnchorLocation.CONTENT_BOTTOM_RIGHT);

		final InvalidationListener reposition = o -> position();
		owner.xProperty().addListener(reposition);
		owner.yProperty().addListener(reposition);
		owner.widthProperty().addListener(reposition);
		owner.heightProperty().addListener(reposition);
	}

	public void info(String message) {
		show(message, Level.INFO, DEFAULT_DURATION);
	}

	public void success(String message) {
		show(message, Level.SUCCESS, DEFAULT_DURATION);
	}

	public void warning(String message) {
		show(message, Level.WARNING, DEFAULT_DURATION);
	}

	public void error(String message) {
		show(message, Level.ERROR, DEFAULT_DURATION);
	}

	/**
	 * Shows a toast with an explicit level and lifetime.
	 *
	 * @param message  text to display
	 * @param level    severity (selects colour)
	 * @param duration how long the toast stays fully visible before fading out
	 */
	public void show(String message, Level level, Duration duration) {
		FxThreads.requireFx();
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(duration, "duration");

		final Node toast = buildToast(message, level);
		stack.getChildren().add(toast);
		position();

		final PauseTransition life = new PauseTransition(duration);
		life.setOnFinished(e -> fadeOut(toast));
		life.play();
	}

	/** Hides any visible toasts and detaches the popup. */
	public void close() {
		stack.getChildren().clear();
		popup.hide();
	}

	private void fadeOut(Node toast) {
		final FadeTransition fade = new FadeTransition(FADE, toast);
		fade.setFromValue(1);
		fade.setToValue(0);
		fade.setOnFinished(e -> {
			stack.getChildren().remove(toast);
			if (stack.getChildren().isEmpty()) {
				popup.hide();
			} else {
				position();
			}
		});
		fade.play();
	}

	private void position() {
		if (!owner.isShowing()) {
			return;
		}
		final double x = owner.getX() + owner.getWidth() - MARGIN;
		final double y = owner.getY() + owner.getHeight() - MARGIN;
		if (popup.isShowing()) {
			popup.setX(x);
			popup.setY(y);
		} else if (!stack.getChildren().isEmpty()) {
			popup.show(owner, x, y);
		}
	}

	private static Node buildToast(String message, Level level) {
		final Label label = new Label(message);
		label.setWrapText(true);
		label.setMaxWidth(320);
		label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

		final HBox box = new HBox(label);
		box.setPadding(new Insets(10, 14, 10, 14));
		box.setStyle("-fx-background-color: " + level.color + "; -fx-background-radius: 6;"
				+ " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 8, 0.2, 0, 2);");
		return box;
	}
}
