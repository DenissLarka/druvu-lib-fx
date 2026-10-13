package com.druvu.lib.fx.notify;

import com.druvu.lib.fx.theme.KitStyles;
import com.druvu.lib.fx.util.FxThreads;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Transient "toast" notifications stacked at the bottom-right of a window. Each toast fades out after a few seconds;
 * several stack upward. Backed by a single {@link Popup} anchored to the owner window, so the app does not have to
 * reserve any layout space or restructure its scene.
 *
 * <p>All methods must be called on the JavaFX thread.
 */
public final class Notifications {

    /** Severity of a toast, which selects its colour. */
    public enum Level {
        INFO(KitStyles.TOAST_INFO),
        SUCCESS(KitStyles.TOAST_SUCCESS),
        WARNING(KitStyles.TOAST_WARNING),
        ERROR(KitStyles.TOAST_ERROR);

        private final String styleClass;

        Level(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private static final Duration DEFAULT_DURATION = Duration.seconds(4);
    private static final Duration FADE = Duration.millis(250);
    private static final Duration FADE_IN = Duration.millis(150);
    private static final double MARGIN = 16;

    private final Window owner;
    private final VBox stack = new VBox(8);
    private final Popup popup = new Popup();

    /** @param owner the window toasts appear over (typically the primary {@code Stage}) */
    public Notifications(Window owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        stack.setAlignment(Pos.BOTTOM_RIGHT);
        // The popup is its own scene graph, outside the app's Scene, so the toolkit stylesheet has to
        // be attached here - it does not inherit the app scene's stylesheets.
        KitStyles.install(stack);
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
     * @param message text to display
     * @param level severity (selects colour)
     * @param duration how long the toast stays fully visible before fading out
     */
    public void show(String message, Level level, Duration duration) {
        FxThreads.requireFx();
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(duration, "duration");

        final Node toast = buildToast(message, level);
        toast.setOpacity(0);
        stack.getChildren().add(toast);
        // Show first, size second. A CSS pass on the popup while it is hidden resolves without the theme once the
        // popup has been hidden before (the style manager forgets a hidden window's scene), and the failed lookups
        // stick: the toast then paints with no background and invisible text - measured on JavaFX 27 as "every
        // toast after the first one expired". Shown, the pass sees the theme. The popup is placed from the
        // content's bottom-right ANCHOR, so the content is sized right after showing and re-anchored; the entering
        // toast is at opacity 0 while that happens, so the window growing to its real size is never visible.
        position();
        if (popup.isShowing()) {
            stack.applyCss();
            stack.layout();
            position();
        }

        // Fade the toast in rather than popping to full opacity in one frame - the entry
        // counterpart of the existing exit fade.
        final FadeTransition in = new FadeTransition(FADE_IN, toast);
        in.setFromValue(0);
        in.setToValue(1);
        in.play();

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
        final Point2D anchor = anchorWithin(
                owner.getX() + owner.getWidth() - MARGIN,
                owner.getY() + owner.getHeight() - MARGIN,
                visualBoundsOf(owner));
        final double x = anchor.getX();
        final double y = anchor.getY();
        if (popup.isShowing()) {
            // Anchor setters, NOT Window.setX/setY: the plain setters place the window's TOP-LEFT
            // at the given point, bypassing the anchor translation - the popup then jumps
            // right-and-down by its own size. Only the anchor properties go through the
            // CONTENT_BOTTOM_RIGHT mapping that show(owner, x, y) used.
            popup.setAnchorX(x);
            popup.setAnchorY(y);
        } else if (!stack.getChildren().isEmpty()) {
            popup.show(owner, x, y);
        }
    }

    /**
     * Keeps the toast's bottom-right anchor inside a screen's visual bounds (the area without taskbar or dock). A
     * window that hangs below the taskbar - saved geometry from a larger screen, or simply dragged there - would
     * otherwise put every toast behind the taskbar, where it is never seen (measured: a 975px-tall window at y=114 on a
     * 1080px screen hid all of them). {@code null} bounds leave the anchor as wanted.
     */
    static Point2D anchorWithin(double wantedX, double wantedY, Rectangle2D visual) {
        if (visual == null) {
            return new Point2D(wantedX, wantedY);
        }
        final double x = Math.max(visual.getMinX() + MARGIN, Math.min(wantedX, visual.getMaxX() - MARGIN));
        final double y = Math.max(visual.getMinY() + MARGIN, Math.min(wantedY, visual.getMaxY() - MARGIN));
        return new Point2D(x, y);
    }

    /** The visual bounds of the screen holding the largest part of the window; null when no screen intersects it. */
    private static Rectangle2D visualBoundsOf(Window window) {
        final Rectangle2D frame = new Rectangle2D(window.getX(), window.getY(), window.getWidth(), window.getHeight());
        Screen best = null;
        double bestArea = 0;
        for (Screen screen : Screen.getScreensForRectangle(frame)) {
            final Rectangle2D bounds = screen.getBounds();
            final double width =
                    Math.min(frame.getMaxX(), bounds.getMaxX()) - Math.max(frame.getMinX(), bounds.getMinX());
            final double height =
                    Math.min(frame.getMaxY(), bounds.getMaxY()) - Math.max(frame.getMinY(), bounds.getMinY());
            final double area = Math.max(0, width) * Math.max(0, height);
            if (area > bestArea) {
                bestArea = area;
                best = screen;
            }
        }
        return best == null ? null : best.getVisualBounds();
    }

    private static Node buildToast(String message, Level level) {
        final Label label = new Label(message);
        label.setWrapText(true);
        label.setMaxWidth(320);

        final HBox box = new HBox(label);
        box.setPadding(new Insets(10, 14, 10, 14));
        // Colours come from druvu-kit.css, which resolves them against the applied theme - so a toast
        // is readable on a dark theme without Notifications knowing a theme exists.
        box.getStyleClass().addAll(KitStyles.TOAST, level.styleClass);
        return box;
    }
}
