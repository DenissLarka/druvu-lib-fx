package com.druvu.lib.fx.dock;

import static com.druvu.lib.fx.dock.DockWorkspace.WORKSPACE;
import static com.druvu.lib.fx.dock.DockWorkspace.assertSameShape;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.dock.DockLayout.Leaf;
import com.druvu.lib.fx.dock.DockLayout.Placement;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Covers DockPane's layout surface on a shown stage: snapshots, apply, redock to the remembered place, hidden
 * placements, folding of single-child splits on undock, the CENTER guard and the revision signal.
 */
public class DockPaneLayoutTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void snapshotOfTheDefaultDockingIsATree() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.dockDefaults();
                assertThat(w.pane.dockLayout())
                        .map(DockLayout::format)
                        .get()
                        .asString()
                        .matches("V\\{[0-9.]+ H\\{[0-9.]+ connections,[0-9.]+ positions\\},[0-9.]+ orders\\}");
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void snapshotRequiresIds() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockPane pane = new DockPane();
            new DockNode(new Label("x"), "Anonymous").dock(pane, DockPos.CENTER);
            assertThatThrownBy(pane::dockLayout)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Anonymous");
        });
    }

    @Test
    public void applyThenSnapshotRoundTrips() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.show();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);
                assertThat(w.all()).allMatch(DockNode::isDocked);
            } finally {
                w.dispose();
            }
        });
    }

    /** The user's complaint: a panel closed and shown again came back as a full-height split of the window. */
    @Test
    public void redockPutsAHiddenPanelBackWhereItWas() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.show();

                w.orders.close();
                assertThat(w.orders.isDocked()).isFalse();
                assertSameShape(w.pane.dockLayout().orElseThrow(), "H{0.350 connections,0.650 positions}");
                assertThat(w.pane.hiddenPlacements()).containsOnlyKeys("orders");
                assertThat(w.pane.hiddenPlacements().get("orders").side()).isEqualTo(DockPos.BOTTOM);

                // The fallback side is deliberately wrong: the remembered place must win.
                w.pane.redock(w.orders, DockPos.LEFT);
                w.layoutPass();
                assertThat(w.orders.isDocked()).isTrue();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);
                assertThat(w.pane.hiddenPlacements()).isEmpty();
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void redockFallsBackToTheSideWhenNothingItKnewIsLeft() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.show();
                w.orders.close();
                w.positions.close();
                w.connections.close();
                assertThat(w.pane.dockLayout()).isEmpty();

                // Orders sat under connections + positions; both are gone, so RIGHT of an empty pane it is.
                w.pane.redock(w.orders, DockPos.RIGHT);
                assertThat(w.pane.dockLayout()).contains(new Leaf("orders"));

                // Positions sat right of connections, which is still hidden: LEFT of the workspace.
                w.pane.redock(w.positions, DockPos.LEFT);
                assertThat(w.pane.dockLayout().orElseThrow().ids()).containsExactly("positions", "orders");
            } finally {
                w.dispose();
            }
        });
    }

    /** Upstream left a single-child SplitPane behind on every undock, one nesting level per hide/show cycle. */
    @Test
    public void undockFoldsTheWrapperItLeavesBehind() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.show();
                assertThat(splitDepth(w.connections)).as("inside H inside V").isEqualTo(2);

                w.positions.close();
                w.layoutPass();
                assertThat(splitDepth(w.connections))
                        .as("the emptied H folded away")
                        .isEqualTo(1);
                assertSameShape(w.pane.dockLayout().orElseThrow(), "V{0.700 connections,0.300 orders}");
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void centerIntoAnOccupiedPaneIsRefused() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.connections.dock(w.pane, DockPos.CENTER);
                assertThatThrownBy(() -> w.positions.dock(w.pane, DockPos.CENTER))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("CENTER");
                assertThat(w.positions.isDocked())
                        .as("refused before any state changed")
                        .isFalse();
                assertThat(w.pane.dockLayout().orElseThrow().ids()).containsExactly("connections");
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void revisionBumpsOnEveryShapeChange() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                final int fresh = w.pane.layoutRevisionProperty().get();
                w.dockDefaults();
                final int docked = w.pane.layoutRevisionProperty().get();
                assertThat(docked).isGreaterThan(fresh);

                w.show();
                final int shown = w.pane.layoutRevisionProperty().get();
                ((SplitPane) w.pane.getChildren().getFirst()).setDividerPosition(0, 0.42);
                final int dragged = w.pane.layoutRevisionProperty().get();
                assertThat(dragged).as("a divider move counts").isGreaterThan(shown);

                w.orders.close();
                assertThat(w.pane.layoutRevisionProperty().get())
                        .as("an undock counts")
                        .isGreaterThan(dragged);
            } finally {
                w.dispose();
            }
        });
    }

    /** What a persistence layer does at start-up for a panel that was hidden when the app last quit. */
    @Test
    public void rememberedPlacementFromAnEarlierRunIsHonoured() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse("H{0.350 connections,0.650 positions}"), w.all());
                w.show();
                w.pane.rememberPlacement("orders", Placement.parse("BOTTOM 0.300 connections,positions"));

                w.pane.redock(w.orders, DockPos.LEFT);
                w.layoutPass();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void applyLeavesOutUnknownLeavesAndHidesTheRest() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.dockDefaults();
                w.pane.apply(DockLayout.parse("V{0.5 connections,0.5 ghost}"), w.all());
                assertThat(w.pane.dockLayout()).contains(new Leaf("connections"));
                assertThat(w.connections.isDocked()).isTrue();
                assertThat(w.positions.isDocked()).isFalse();
                assertThat(w.orders.isDocked()).isFalse();
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void applyBringsAFloatingPanelBack() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.show();
                w.positions.setFloating(true);
                assertThat(w.positions.isFloating()).isTrue();
                assertThat(w.positions.isDocked()).isFalse();

                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.layoutPass();
                assertThat(w.positions.isFloating()).isFalse();
                assertThat(w.positions.isDocked()).isTrue();
                assertThat(w.positions.getStage().isShowing())
                        .as("floating stage closed")
                        .isFalse();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);
            } finally {
                w.dispose();
            }
        });
    }

    /**
     * JavaFX's SplitPane hands a size change to both sides of a divider equally, which moves the divider's share toward
     * 0.5 - and an app's scene is laid out at its default size before the window takes its restored one. Left alone, a
     * restored layout walks toward 50/50 on every start (measured in the wealth app: 0.35, 0.37, 0.39, 0.40, 0.41,
     * ...). The shares of an applied layout must hold across that resize.
     */
    @Test
    public void sharesSurviveAResizeAfterTheFirstLayout() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.stage.setScene(new javafx.scene.Scene(w.pane, 400, 300));
                w.stage.show();
                w.layoutPass();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);

                // The window grows (the restored geometry arriving after the first layout).
                w.pane.resize(600, 300);
                w.pane.layout();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);
                // Not just the value: the divider on screen holds too (the H split is the V split's first item).
                final SplitPane rows = (SplitPane) w.pane.getChildren().getFirst();
                final SplitPane columns = (SplitPane) rows.getItems().getFirst();
                assertThat(columns.getDividerPositions()[0]).isCloseTo(0.35, within(0.02));
                assertThat(rows.getDividerPositions()[0]).isCloseTo(0.70, within(0.02));
            } finally {
                w.dispose();
            }
        });
    }

    /** A divider the user moved is the new intent; a later resize keeps that, not the value the layout came with. */
    @Test
    public void aMovedDividerKeepsItsNewShareAcrossAResize() throws InterruptedException {
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.pane.apply(DockLayout.parse(WORKSPACE), w.all());
                w.stage.setScene(new javafx.scene.Scene(w.pane, 400, 300));
                w.stage.show();
                w.layoutPass();

                final SplitPane rows = (SplitPane) w.pane.getChildren().getFirst();
                rows.setDividerPosition(0, 0.5);
                w.pane.layout();
                w.pane.resize(400, 500);
                w.pane.layout();
                assertThat(rows.getDividerPositions()[0]).isCloseTo(0.5, within(0.02));
                assertSameShape(
                        w.pane.dockLayout().orElseThrow(),
                        "V{0.500 H{0.350 connections,0.650 positions},0.500 orders}");
            } finally {
                w.dispose();
            }
        });
    }

    /** SplitPane ancestors of a node: the nesting depth of its dock slot. */
    private static int splitDepth(Node node) {
        int depth = 0;
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof SplitPane) {
                depth++;
            }
        }
        return depth;
    }
}
