package com.druvu.lib.fx.dock;

import java.util.Arrays;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.scene.control.SplitPane;

/**
 * Keeps a {@link SplitPane}'s dividers at their shares across resizes (a druvu addition, not from DockFX).
 *
 * <p>JavaFX's SplitPaneSkin hands a size change to its panels round-robin ("adds/subtracts a little to each panel on
 * every resize"), which walks every divider toward the middle; an app whose scene is laid out at its default size
 * before the window takes its restored one walked a restored layout a step toward 50/50 on every start (measured in
 * the wealth app). The skin honours an explicitly set position for exactly the next layout, so a size change
 * re-asserts the current shares right before that layout. Every other position change - a drag, the app, the skin
 * writing back the pixel-snapped result of a layout - is the new share to keep.
 *
 * @author Deniss Larka <br>
 *     on 26 Sep 2026
 */
final class SplitShares {

    private final SplitPane split;
    private final Runnable changed;
    private double[] shares;
    private boolean reasserting;

    private SplitShares(SplitPane split, Runnable changed) {
        this.split = split;
        this.changed = changed;
        this.shares = split.getDividerPositions();
        final InvalidationListener resized = observable -> reassert();
        split.widthProperty().addListener(resized);
        split.heightProperty().addListener(resized);
        split.getDividers().forEach(this::watch);
        split.getDividers().addListener((ListChangeListener<SplitPane.Divider>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(this::watch);
            }
            adopt();
        });
    }

    /** Attaches once per split; the split carries the keeper in its properties, so their lifetimes match. */
    static void attach(SplitPane split, Runnable changed) {
        if (!split.getProperties().containsKey(SplitShares.class)) {
            split.getProperties().put(SplitShares.class, new SplitShares(split, changed));
        }
    }

    /** The kept shares when the split has a keeper, else the actual divider positions. */
    static double[] positions(SplitPane split) {
        return split.getProperties().get(SplitShares.class) instanceof SplitShares keeper
                ? keeper.shares.clone()
                : split.getDividerPositions();
    }

    private void watch(SplitPane.Divider divider) {
        divider.positionProperty().addListener(observable -> adopt());
    }

    private void reassert() {
        if (shares.length != split.getDividers().size()) {
            return;
        }
        reasserting = true;
        try {
            // The skin marks a divider explicit only when its position listener sees a change; an
            // equal value would leave the divider to the resize distribution. Nudge, then set.
            for (int i = 0; i < shares.length; i++) {
                split.setDividerPosition(i, Math.nextUp(shares[i]));
                split.setDividerPosition(i, shares[i]);
            }
        } finally {
            reasserting = false;
        }
    }

    private void adopt() {
        if (reasserting) {
            return;
        }
        final double[] now = split.getDividerPositions();
        if (!Arrays.equals(now, shares)) {
            shares = now;
            changed.run();
        }
    }
}
