package com.druvu.lib.fx.dock;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.stage.PopupWindow;
import javafx.stage.Window;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * An emptied workspace takes a panel back: a drop on an edge indicator, or anywhere on the pane, docks it as the new
 * root - whether the root is an empty split every panel has left or no root at all. Upstream died on the second case
 * (user-found 2026-10-10: every panel floated, the drag over the main window threw and no drop could land again), and
 * left its indicator overlay on screen after a drop or a leave over an empty pane (user-found the same day: the dashed
 * rectangle stayed where it was while the main window moved).
 */
public class EmptyDockPaneTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    /** Every panel undocked leaves an empty root split; the middle of the pane, no indicator there, still docks. */
    @Test
    public void aPanelDroppedAnywhereOnAnEmptiedPaneDocksAsItsRoot() throws InterruptedException {
        final DockWorkspace workspace = workspace();
        try {
            FxTestToolkit.runOnFx(() -> {
                workspace.all().forEach(DockNode::undock);
                workspace.layoutPass();
            });
            assertThat(FxTestToolkit.call(() -> workspace.pane.dockLayout())).isEmpty();

            dragAndDrop(workspace, workspace.positions, 0.5, 0.5);

            assertThat(FxTestToolkit.call(() -> workspace.positions.isDocked())).isTrue();
            assertThat(FxTestToolkit.call(() -> workspace.pane.dockLayout().map(DockLayout::format)))
                    .contains("positions");
            assertThat(FxTestToolkit.call(EmptyDockPaneTest::popupsShowing))
                    .as("the indicator overlay is gone after the drop")
                    .isZero();
        } finally {
            FxTestToolkit.runOnFx(workspace::dispose);
        }
    }

    /** A layout naming nobody leaves no root at all - the user's state; the top edge indicator must still take one. */
    @Test
    public void aPanelDroppedOnTheEdgeOfARootlessPaneDocks() throws InterruptedException {
        final DockWorkspace workspace = workspace();
        try {
            FxTestToolkit.runOnFx(() -> {
                workspace.pane.apply(DockLayout.parse("nobody"), workspace.all());
                workspace.layoutPass();
            });
            assertThat(FxTestToolkit.call(() -> workspace.pane.dockLayout())).isEmpty();
            assertThat(FxTestToolkit.call(() -> workspace.orders.isDocked())).isFalse();

            dragAndDrop(workspace, workspace.orders, 0.5, 0.03);

            assertThat(FxTestToolkit.call(() -> workspace.orders.isDocked())).isTrue();
            assertThat(FxTestToolkit.call(() -> workspace.pane.dockLayout().map(DockLayout::format)))
                    .contains("orders");
            assertThat(FxTestToolkit.call(EmptyDockPaneTest::popupsShowing))
                    .as("the indicator overlay is gone after the drop")
                    .isZero();
        } finally {
            FxTestToolkit.runOnFx(workspace::dispose);
        }
    }

    /** A drag that enters an empty pane and leaves again without dropping takes its overlay with it. */
    @Test
    public void aDragThatLeavesAnEmptiedPaneTakesTheOverlayWithIt() throws InterruptedException {
        final DockWorkspace workspace = workspace();
        try {
            FxTestToolkit.runOnFx(() -> {
                workspace.all().forEach(DockNode::undock);
                workspace.layoutPass();
            });
            final Point2D at = FxTestToolkit.call(
                    () -> workspace.pane.localToScreen(workspace.pane.getWidth() / 2, workspace.pane.getHeight() / 2));
            fire(workspace.pane, DockEvent.DOCK_ENTER, at, null);
            fire(workspace.pane, DockEvent.DOCK_OVER, at, null);
            assertThat(FxTestToolkit.call(EmptyDockPaneTest::popupsShowing))
                    .as("the overlay shows while the drag is over the pane")
                    .isPositive();

            fire(workspace.pane, DockEvent.DOCK_EXIT, at, null);

            assertThat(FxTestToolkit.call(EmptyDockPaneTest::popupsShowing)).isZero();
            assertThat(FxTestToolkit.call(() -> workspace.pane.dockLayout())).isEmpty();
        } finally {
            FxTestToolkit.runOnFx(workspace::dispose);
        }
    }

    /** @return how many popup windows are showing - the dock's two indicator popups are the only ones in these tests */
    private static int popupsShowing() {
        return (int) Window.getWindows().stream()
                .filter(window -> window instanceof PopupWindow)
                .count();
    }

    private static DockWorkspace workspace() throws InterruptedException {
        return FxTestToolkit.call(() -> {
            final DockWorkspace workspace = new DockWorkspace();
            workspace.dockDefaults();
            workspace.show();
            return workspace;
        });
    }

    /**
     * Fires the dock events a title-bar drag fires at the pane - enter, over, release - each in its own FX turn, so the
     * indicator overlay shown on enter is laid out before the over is judged. The point is a share of the pane's size.
     */
    private static void dragAndDrop(DockWorkspace workspace, DockNode node, double xShare, double yShare)
            throws InterruptedException {
        final Point2D at = FxTestToolkit.call(() ->
                workspace.pane.localToScreen(workspace.pane.getWidth() * xShare, workspace.pane.getHeight() * yShare));
        fire(workspace.pane, DockEvent.DOCK_ENTER, at, null);
        fire(workspace.pane, DockEvent.DOCK_OVER, at, null);
        fire(workspace.pane, DockEvent.DOCK_OVER, at, null);
        fire(workspace.pane, DockEvent.DOCK_RELEASED, at, node);
    }

    private static void fire(DockPane pane, EventType<DockEvent> type, Point2D at, Node contents)
            throws InterruptedException {
        FxTestToolkit.runOnFx(() ->
                Event.fireEvent(pane, new DockEvent(null, null, type, 0, 0, at.getX(), at.getY(), null, contents)));
    }
}
