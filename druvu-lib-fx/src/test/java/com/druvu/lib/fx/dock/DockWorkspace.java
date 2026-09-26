package com.druvu.lib.fx.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.druvu.lib.fx.dock.DockLayout.Leaf;
import com.druvu.lib.fx.dock.DockLayout.Split;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/** Test fixture: a dock pane with three identified panels in a shown 400 x 300 window, plus a shape assertion. */
final class DockWorkspace {

    /** The wealth app's default workspace: Connections | Positions over Orders. */
    static final String WORKSPACE = "V{0.700 H{0.350 connections,0.650 positions},0.300 orders}";

    /** Dividers land on pixels, so shares come back a little off; 400 x 300 keeps them close. */
    private static final double SHARE_TOLERANCE = 0.02;

    final DockPane pane = new DockPane();
    final DockNode connections = panel("connections");
    final DockNode positions = panel("positions");
    final DockNode orders = panel("orders");
    final Stage stage = new Stage();

    List<DockNode> all() {
        return List.of(connections, positions, orders);
    }

    /** Docks the panels the way the app does on a fresh install. */
    void dockDefaults() {
        connections.dock(pane, DockPos.CENTER);
        positions.dock(pane, DockPos.RIGHT);
        orders.dock(pane, DockPos.BOTTOM);
    }

    void show() {
        stage.setScene(new Scene(pane, 400, 300));
        stage.show();
        layoutPass();
    }

    void layoutPass() {
        pane.applyCss();
        pane.layout();
    }

    void dispose() {
        for (DockNode node : all()) {
            if (node.isFloating()) {
                node.close();
            }
        }
        stage.hide();
    }

    private static DockNode panel(String id) {
        final DockNode node = new DockNode(new Label(id), id);
        node.setId(id);
        return node;
    }

    /** Same tree, same ids, shares within {@link #SHARE_TOLERANCE} of the expected text form. */
    static void assertSameShape(DockLayout actual, String expectedText) {
        assertSameShape(actual, DockLayout.parse(expectedText), "");
    }

    private static void assertSameShape(DockLayout actual, DockLayout expected, String path) {
        switch (expected) {
            case Leaf leaf -> assertThat(actual).as(path).isEqualTo(leaf);
            case Split split -> {
                assertThat(actual).as(path).isInstanceOf(Split.class);
                final Split actualSplit = (Split) actual;
                assertThat(actualSplit.orientation()).as(path + " orientation").isEqualTo(split.orientation());
                assertThat(actualSplit.entries()).as(path + " entries").hasSameSizeAs(split.entries());
                for (int i = 0; i < split.entries().size(); i++) {
                    assertThat(actualSplit.entries().get(i).share())
                            .as(path + "[" + i + "] share")
                            .isCloseTo(split.entries().get(i).share(), within(SHARE_TOLERANCE));
                    assertSameShape(
                            actualSplit.entries().get(i).child(),
                            split.entries().get(i).child(),
                            path + "[" + i + "]");
                }
            }
        }
    }
}
