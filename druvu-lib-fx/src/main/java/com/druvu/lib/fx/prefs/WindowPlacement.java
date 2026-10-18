package com.druvu.lib.fx.prefs;

import java.util.List;
import java.util.Objects;
import javafx.geometry.Rectangle2D;

/**
 * Decides where a saved window may reopen, given the screens there are now. A window saved on a display that has since
 * been unplugged, or moved around it, must never reopen where the user cannot reach it.
 *
 * <p>The rule: a saved window reopens exactly where it was when both its title bar and its bottom edge lie on a screen
 * - the title bar so it can be dragged, the bottom edge so the window's own status line is not hidden under the taskbar
 * or the Dock. Otherwise it moves fully onto the screen that holds most of it, shrunk to that screen when it is bigger;
 * a window that touches no screen at all opens centred on the primary one.
 *
 * <p>Screens are given by their usable areas ({@link javafx.stage.Screen#getVisualBounds()}: without the taskbar, the
 * menu bar and the Dock). Pure arithmetic over rectangles, so every case is testable without a second display.
 *
 * @author Deniss Larka <br>
 *     on 18 Oct 2026
 */
final class WindowPlacement {

    /** The strip along a window's top edge that the user drags it by. */
    static final double TITLE_BAR = 30;

    /** How much of an edge strip must lie on one screen for the edge to count as on screen. */
    static final double GRIP = 100;

    private WindowPlacement() {}

    /**
     * Where to put a window saved at {@code saved}.
     *
     * @param saved the window's saved bounds
     * @param usableAreas the usable area of every screen there is now
     * @param primaryUsable the primary screen's usable area, for a window that touches no screen
     * @return {@code saved} itself when the window is reachable where it was, otherwise the bounds to use instead
     */
    static Rectangle2D placement(Rectangle2D saved, List<Rectangle2D> usableAreas, Rectangle2D primaryUsable) {
        Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(usableAreas, "usableAreas");
        Objects.requireNonNull(primaryUsable, "primaryUsable");
        if (onScreen(topStrip(saved), usableAreas) && onScreen(bottomStrip(saved), usableAreas)) {
            return saved;
        }
        final Rectangle2D home = mostOf(saved, usableAreas);
        return home == null ? centredOn(saved, primaryUsable) : movedInto(saved, home);
    }

    /**
     * An edge strip is on screen when one screen shows a grip-wide stretch of it (the whole strip for a narrow window)
     * at least half its height. Half, not all: Windows reports a window snapped to a screen edge a few pixels outside
     * it - its invisible resize border - and such a window is where the user put it.
     */
    private static boolean onScreen(Rectangle2D strip, List<Rectangle2D> usableAreas) {
        final double neededWidth = Math.min(GRIP, strip.getWidth());
        for (Rectangle2D area : usableAreas) {
            final Rectangle2D shown = intersection(strip, area);
            if (shown != null && shown.getWidth() >= neededWidth && shown.getHeight() >= strip.getHeight() / 2) {
                return true;
            }
        }
        return false;
    }

    private static Rectangle2D topStrip(Rectangle2D window) {
        return new Rectangle2D(
                window.getMinX(), window.getMinY(), window.getWidth(), Math.min(TITLE_BAR, window.getHeight()));
    }

    private static Rectangle2D bottomStrip(Rectangle2D window) {
        final double height = Math.min(TITLE_BAR, window.getHeight());
        return new Rectangle2D(window.getMinX(), window.getMaxY() - height, window.getWidth(), height);
    }

    /** The usable area that shows the largest part of the window, or null when no screen shows any of it. */
    private static Rectangle2D mostOf(Rectangle2D window, List<Rectangle2D> usableAreas) {
        Rectangle2D best = null;
        double bestShown = 0;
        for (Rectangle2D area : usableAreas) {
            final Rectangle2D shown = intersection(window, area);
            final double shownArea = shown == null ? 0 : shown.getWidth() * shown.getHeight();
            if (shownArea > bestShown) {
                best = area;
                bestShown = shownArea;
            }
        }
        return best;
    }

    /** The window moved the least distance that puts it fully inside {@code area}, shrunk to it first if bigger. */
    private static Rectangle2D movedInto(Rectangle2D window, Rectangle2D area) {
        final double width = Math.min(window.getWidth(), area.getWidth());
        final double height = Math.min(window.getHeight(), area.getHeight());
        final double x = clamp(window.getMinX(), area.getMinX(), area.getMaxX() - width);
        final double y = clamp(window.getMinY(), area.getMinY(), area.getMaxY() - height);
        return new Rectangle2D(x, y, width, height);
    }

    private static Rectangle2D centredOn(Rectangle2D window, Rectangle2D area) {
        final double width = Math.min(window.getWidth(), area.getWidth());
        final double height = Math.min(window.getHeight(), area.getHeight());
        return new Rectangle2D(
                area.getMinX() + (area.getWidth() - width) / 2,
                area.getMinY() + (area.getHeight() - height) / 2,
                width,
                height);
    }

    /** The overlap of two rectangles, or null when they do not overlap. */
    private static Rectangle2D intersection(Rectangle2D a, Rectangle2D b) {
        final double minX = Math.max(a.getMinX(), b.getMinX());
        final double minY = Math.max(a.getMinY(), b.getMinY());
        final double maxX = Math.min(a.getMaxX(), b.getMaxX());
        final double maxY = Math.min(a.getMaxY(), b.getMaxY());
        return maxX > minX && maxY > minY ? new Rectangle2D(minX, minY, maxX - minX, maxY - minY) : null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
