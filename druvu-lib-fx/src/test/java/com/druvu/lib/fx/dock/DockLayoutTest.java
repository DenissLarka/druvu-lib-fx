package com.druvu.lib.fx.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.druvu.lib.fx.dock.DockLayout.Leaf;
import com.druvu.lib.fx.dock.DockLayout.Placement;
import java.util.Set;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Covers the DockLayout value model: text round trips, remove/insert symmetry, placement derivation and rejections. */
public class DockLayoutTest {

    /** The wealth app's default workspace: Connections | Positions over Orders. */
    private static final String WORKSPACE = "V{0.700 H{0.350 connections,0.650 positions},0.300 orders}";

    @Test
    public void textFormRoundTrips() {
        assertThat(DockLayout.parse(WORKSPACE).format()).isEqualTo(WORKSPACE);
    }

    @Test
    public void sharesAreNormalisedOnParse() {
        assertThat(DockLayout.parse("H{1 a,3 b}").format()).isEqualTo("H{0.250 a,0.750 b}");
    }

    @Test
    public void leafIdsMayStartLikeASplit() {
        assertThat(DockLayout.parse("H{0.5 History,0.5 Volume}").ids()).containsExactly("History", "Volume");
        assertThat(DockLayout.parse("V").format()).isEqualTo("V");
    }

    @DataProvider
    public Object[][] malformed() {
        return new Object[][] {
            {"H{0.5 a}"}, {"H{0.5 a,0.5 a}"}, {"X{0.5 a,0.5 b}"}, {"H{0.5 a,0.5 b"}, {"H{0.5 a,0.5 b}x"},
            {"H{a,b}"}, {""}, {"H{0.5 a b,0.5 c}"}, {"H{0 a,1 b}"}, {"H{0.5 a,,0.5 b}"},
        };
    }

    @Test(dataProvider = "malformed")
    public void parseRejectsMalformedText(String text) {
        assertThatThrownBy(() -> DockLayout.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void removeGivesTheShareToThePreviousNeighbour() {
        assertThat(DockLayout.parse("H{0.2 a,0.5 b,0.3 c}").remove("b"))
                .map(DockLayout::format)
                .contains("H{0.700 a,0.300 c}");
    }

    @Test
    public void removeOfTheFirstGivesTheShareToTheNext() {
        assertThat(DockLayout.parse("H{0.2 a,0.5 b,0.3 c}").remove("a"))
                .map(DockLayout::format)
                .contains("H{0.700 b,0.300 c}");
    }

    @Test
    public void removeCollapsesATwoEntrySplit() {
        assertThat(DockLayout.parse(WORKSPACE).remove("positions"))
                .map(DockLayout::format)
                .contains("V{0.700 connections,0.300 orders}");
    }

    @Test
    public void removeOfTheLastNodeLeavesNothing() {
        assertThat(new Leaf("a").remove("a")).isEmpty();
    }

    @Test
    public void removeRejectsAnUnknownId() {
        assertThatThrownBy(() -> DockLayout.parse(WORKSPACE).remove("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    public void placementIsRelativeToTheNeighbour() {
        final DockLayout workspace = DockLayout.parse(WORKSPACE);

        final Placement orders = workspace.placementOf("orders").orElseThrow();
        assertThat(orders.side()).isEqualTo(DockPos.BOTTOM);
        assertThat(orders.share()).isCloseTo(0.3, within(1e-9));
        assertThat(orders.anchors()).containsExactly("connections", "positions");

        final Placement positions = workspace.placementOf("positions").orElseThrow();
        assertThat(positions.side()).isEqualTo(DockPos.RIGHT);
        assertThat(positions.share()).isCloseTo(0.65, within(1e-9));
        assertThat(positions.anchors()).containsExactly("connections");

        final Placement connections = workspace.placementOf("connections").orElseThrow();
        assertThat(connections.side()).isEqualTo(DockPos.LEFT);
        assertThat(connections.share()).isCloseTo(0.35, within(1e-9));
        assertThat(connections.anchors()).containsExactly("positions");
    }

    @Test
    public void aLoneNodeHasNoPlacement() {
        assertThat(new Leaf("a").placementOf("a")).isEmpty();
    }

    /** The contract the dock relies on: hide a panel, show it again, the workspace is what it was. */
    @Test
    public void removeThenInsertRestoresTheLayout() {
        final DockLayout workspace = DockLayout.parse(WORKSPACE);
        for (String id : workspace.ids()) {
            final Placement placement = workspace.placementOf(id).orElseThrow();
            final DockLayout without = workspace.remove(id).orElseThrow();
            assertThat(without.insert(id, placement))
                    .as("%s put back", id)
                    .map(DockLayout::format)
                    .contains(WORKSPACE);
        }
    }

    @Test
    public void insertBesideTheAnchorWhenOrientationsMatch() {
        final Placement rightOfA = new Placement(DockPos.RIGHT, 0.4, Set.of("a"));
        assertThat(DockLayout.parse("H{0.5 a,0.5 b}").insert("c", rightOfA))
                .map(DockLayout::format)
                .contains("H{0.300 a,0.200 c,0.500 b}");
    }

    @Test
    public void insertWrapsTheAnchorWhenOrientationsDiffer() {
        final Placement belowA = new Placement(DockPos.BOTTOM, 0.25, Set.of("a"));
        assertThat(DockLayout.parse("H{0.5 a,0.5 b}").insert("c", belowA))
                .map(DockLayout::format)
                .contains("H{0.500 V{0.750 a,0.250 c},0.500 b}");
    }

    @Test
    public void insertAgainstAnchorsSpreadOverASplitWrapsThatSplit() {
        final Placement belowBoth = new Placement(DockPos.BOTTOM, 0.3, Set.of("a", "b"));
        assertThat(DockLayout.parse("H{0.5 a,0.5 b}").insert("c", belowBoth))
                .map(DockLayout::format)
                .contains("V{0.700 H{0.500 a,0.500 b},0.300 c}");
    }

    @Test
    public void insertIgnoresAnchorsThatAreGone() {
        final Placement belowAAndGone = new Placement(DockPos.BOTTOM, 0.25, Set.of("a", "gone"));
        assertThat(DockLayout.parse("H{0.5 a,0.5 b}").insert("c", belowAAndGone))
                .map(DockLayout::format)
                .contains("H{0.500 V{0.750 a,0.250 c},0.500 b}");
    }

    @Test
    public void insertWithoutAnySurvivingAnchorIsEmpty() {
        final Placement nowhere = new Placement(DockPos.LEFT, 0.5, Set.of("gone"));
        assertThat(DockLayout.parse("H{0.5 a,0.5 b}").insert("c", nowhere)).isEmpty();
    }

    @Test
    public void insertRejectsAnIdAlreadyPresent() {
        final Placement leftOfB = new Placement(DockPos.LEFT, 0.5, Set.of("b"));
        assertThatThrownBy(() -> DockLayout.parse("H{0.5 a,0.5 b}").insert("a", leftOfB))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already");
    }

    @Test
    public void placementTextRoundTrips() {
        final String text = "BOTTOM 0.300 connections,positions";
        assertThat(Placement.parse(text).format()).isEqualTo(text);
    }

    @Test
    public void placementRejectsCenterAndBadShares() {
        assertThatThrownBy(() -> new Placement(DockPos.CENTER, 0.5, Set.of("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Placement(DockPos.LEFT, 1.0, Set.of("a")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Placement(DockPos.LEFT, 0.5, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
