package com.druvu.lib.fx.prefs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.geometry.Rectangle2D;
import org.testng.annotations.Test;

/**
 * Unit tests for where a saved window reopens: kept where it was when reachable, moved onto a screen when its title bar
 * or bottom edge is out of sight, centred on the primary when it touches no screen. The screens mirror a laptop with an
 * external display to its left, as measured on a Mac (usable areas, so without menu bar and Dock).
 */
public class WindowPlacementTest {

    /** The laptop: the primary screen, a menu bar on top and the Dock at the bottom taken out. */
    private static final Rectangle2D LAPTOP = new Rectangle2D(0, 33, 1512, 868);

    /** The external display, to the laptop's left and a little higher. */
    private static final Rectangle2D EXTERNAL = new Rectangle2D(-1920, -68, 1920, 1050);

    private static final List<Rectangle2D> BOTH = List.of(LAPTOP, EXTERNAL);
    private static final List<Rectangle2D> LAPTOP_ONLY = List.of(LAPTOP);

    @Test
    public void aWindowFullyOnAScreenStaysWhereItWas() {
        Rectangle2D saved = new Rectangle2D(200, 100, 800, 600);
        assertThat(WindowPlacement.placement(saved, LAPTOP_ONLY, LAPTOP)).isSameAs(saved);
    }

    @Test
    public void aWindowOnTheUnpluggedDisplayOpensCentredOnThePrimary() {
        Rectangle2D savedOnExternal = new Rectangle2D(-891, 56, 813, 533);
        assertThat(WindowPlacement.placement(savedOnExternal, BOTH, LAPTOP)).isSameAs(savedOnExternal);
        assertThat(WindowPlacement.placement(savedOnExternal, LAPTOP_ONLY, LAPTOP))
                .isEqualTo(new Rectangle2D(349.5, 200.5, 813, 533));
    }

    @Test
    public void windowsParkingSpotForAMinimizedWindowOpensCentredOnThePrimary() {
        Rectangle2D parked = new Rectangle2D(-32000, -32000, 160, 28);
        assertThat(WindowPlacement.placement(parked, LAPTOP_ONLY, LAPTOP))
                .isEqualTo(new Rectangle2D(676, 453, 160, 28));
    }

    @Test
    public void aTitleBarAboveEveryScreenComesDownOntoTheScreen() {
        // Only the bottom 40px reach the laptop: the window is "on screen", but nothing can drag it.
        Rectangle2D saved = new Rectangle2D(100, -527, 800, 600);
        assertThat(WindowPlacement.placement(saved, LAPTOP_ONLY, LAPTOP)).isEqualTo(new Rectangle2D(100, 33, 800, 600));
    }

    @Test
    public void aSliverAtTheScreenEdgeIsPulledFullyIn() {
        Rectangle2D saved = new Rectangle2D(1492, 100, 800, 600);
        assertThat(WindowPlacement.placement(saved, LAPTOP_ONLY, LAPTOP))
                .isEqualTo(new Rectangle2D(712, 100, 800, 600));
    }

    @Test
    public void aBottomUnderTheTaskbarMovesUp() {
        // The laptop case from a Windows box: a window 975 tall at y 114 on a 1080 screen whose taskbar takes 48px.
        Rectangle2D workArea = new Rectangle2D(0, 0, 1920, 1032);
        Rectangle2D saved = new Rectangle2D(300, 114, 1200, 975);
        assertThat(WindowPlacement.placement(saved, List.of(workArea), workArea))
                .isEqualTo(new Rectangle2D(300, 57, 1200, 975));
    }

    @Test
    public void aWindowBiggerThanTheScreenShrinksToIt() {
        Rectangle2D saved = new Rectangle2D(-1900, -60, 1900, 1040); // fitted the external display
        assertThat(WindowPlacement.placement(saved, LAPTOP_ONLY, LAPTOP)).isEqualTo(new Rectangle2D(0, 33, 1512, 868));
    }

    @Test
    public void aWindowSpanningTwoScreensStaysWhereItWas() {
        // Multi-monitor users keep a window across both displays; as long as both are there, so is the window.
        Rectangle2D saved = new Rectangle2D(-600, 200, 1400, 600);
        assertThat(WindowPlacement.placement(saved, BOTH, LAPTOP)).isSameAs(saved);
    }

    @Test
    public void aWindowSnappedToTheScreenEdgeStaysWhereItWas() {
        // Windows reports a snapped window a few pixels outside the work area: its invisible resize border.
        Rectangle2D workArea = new Rectangle2D(0, 0, 1920, 1032);
        Rectangle2D snappedLeftHalf = new Rectangle2D(-7, 0, 974, 1039);
        assertThat(WindowPlacement.placement(snappedLeftHalf, List.of(workArea), workArea))
                .isSameAs(snappedLeftHalf);
    }

    @Test
    public void aTitleBarOnTheUpperOfTwoStackedScreensStaysWhereItWas() {
        Rectangle2D lower = new Rectangle2D(0, 0, 1920, 1040);
        Rectangle2D upper = new Rectangle2D(0, -1080, 1920, 1080);
        Rectangle2D saved = new Rectangle2D(400, -500, 900, 800); // title bar above, bottom edge below the seam
        assertThat(WindowPlacement.placement(saved, List.of(lower, upper), lower))
                .isSameAs(saved);
        assertThat(WindowPlacement.placement(saved, List.of(lower), lower))
                .isEqualTo(new Rectangle2D(400, 0, 900, 800));
    }

    @Test
    public void aNarrowWindowNeedsItsWholeTitleBarOnScreen() {
        Rectangle2D narrow = new Rectangle2D(1452, 100, 80, 300); // 60 of its 80px on the laptop
        assertThat(WindowPlacement.placement(narrow, LAPTOP_ONLY, LAPTOP))
                .isEqualTo(new Rectangle2D(1432, 100, 80, 300));
    }
}
