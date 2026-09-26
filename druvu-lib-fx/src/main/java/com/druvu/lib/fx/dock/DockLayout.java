package com.druvu.lib.fx.dock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.geometry.Orientation;

/**
 * The shape of a {@link DockPane} workspace as a value: a tree of splits whose leaves are the docked nodes, named by
 * their JavaFX id. Immutable - every operation returns a new layout - and independent of the scene graph, so an app can
 * keep one in its preferences and a test can reason about one without a toolkit. {@link DockPane#dockLayout()} takes the
 * snapshot, {@link DockPane#apply} rebuilds a pane from one.
 *
 * <p>Text form, as stored: a leaf is its id; a split is {@code H{...}} (children side by side) or {@code V{...}}
 * (stacked) listing {@code share child} entries, the shares of one split summing to 1:
 *
 * <pre>V{0.700 H{0.350 connections,0.650 positions},0.300 orders}</pre>
 *
 * <p>Shares rather than pixels, so a restored layout scales with its window. Ids are {@code [A-Za-z0-9_.-]+} and
 * unique within a layout.
 *
 * @author Deniss Larka <br>
 *     on 26 Sep 2026
 */
public sealed interface DockLayout {

    /** A docked node, by its JavaFX id. */
    record Leaf(String id) implements DockLayout {
        public Leaf {
            DockLayoutText.requireId(id);
        }
    }

    /** One child of a {@link Split} and its share of the split's extent. */
    record Entry(DockLayout child, double share) {
        public Entry {
            Objects.requireNonNull(child, "child");
            if (!(share > 0) || !Double.isFinite(share)) {
                throw new IllegalArgumentException("share must be a positive number, got " + share);
            }
        }
    }

    /**
     * Two or more children side by side ({@link Orientation#HORIZONTAL}) or stacked ({@link Orientation#VERTICAL}).
     * Shares are normalised to sum to 1; an id may not appear twice under one split.
     */
    record Split(Orientation orientation, List<Entry> entries) implements DockLayout {
        public Split {
            Objects.requireNonNull(orientation, "orientation");
            if (entries.size() < 2) {
                throw new IllegalArgumentException("a split needs at least two entries, got " + entries.size());
            }
            entries = normalised(entries);
            final Set<String> seen = new LinkedHashSet<>();
            for (Entry entry : entries) {
                for (String id : entry.child().ids()) {
                    if (!seen.add(id)) {
                        throw new IllegalArgumentException("id appears twice in one layout: " + id);
                    }
                }
            }
        }

        private static List<Entry> normalised(List<Entry> entries) {
            double total = 0;
            for (Entry entry : entries) {
                total += entry.share();
            }
            final List<Entry> scaled = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                scaled.add(new Entry(entry.child(), entry.share() / total));
            }
            return Collections.unmodifiableList(scaled);
        }

        /** This split with the entry at {@code index} replaced. */
        Split with(int index, Entry entry) {
            final List<Entry> copy = new ArrayList<>(entries);
            copy.set(index, entry);
            return new Split(orientation, copy);
        }
    }

    /**
     * A hidden node's remembered place: which {@code side} of the neighbours it sat next to ({@code anchors}, by id)
     * and the {@code share} of their joint extent it took, within (0, 1). Text form: {@code
     * BOTTOM 0.300 connections,positions}. Resolved against whatever the layout looks like when the node comes back:
     * the anchors that are still there define the spot, so panels rearranged in the meantime are respected.
     */
    record Placement(DockPos side, double share, Set<String> anchors) {
        public Placement {
            orientationOf(side);
            if (!(share > 0 && share < 1)) {
                throw new IllegalArgumentException("share must be within (0, 1), got " + share);
            }
            if (anchors.isEmpty()) {
                throw new IllegalArgumentException("a placement needs at least one anchor");
            }
            anchors.forEach(DockLayoutText::requireId);
            anchors = Collections.unmodifiableSet(new LinkedHashSet<>(anchors));
        }

        public static Placement parse(String text) {
            final String[] parts = text.strip().split("\\s+", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("expected 'side share anchors', got '" + text + "'");
            }
            return new Placement(
                    DockPos.valueOf(parts[0]),
                    Double.parseDouble(parts[1]),
                    new LinkedHashSet<>(List.of(parts[2].split(","))));
        }

        public String format() {
            return side + " " + DockLayoutText.share(share) + " " + String.join(",", anchors);
        }
    }

    /** Parses the text form; the inverse of {@link #format()}. */
    static DockLayout parse(String text) {
        return DockLayoutText.parse(text);
    }

    /** The text form, e.g. {@code V{0.700 H{0.350 connections,0.650 positions},0.300 orders}}. */
    default String format() {
        return DockLayoutText.format(this);
    }

    /** Every node id in this layout, in tree order. */
    default Set<String> ids() {
        final Set<String> ids = new LinkedHashSet<>();
        collectIds(this, ids);
        return Collections.unmodifiableSet(ids);
    }

    default boolean contains(String id) {
        return ids().contains(id);
    }

    /**
     * This layout without the node, its share going to the neighbour it sat next to (the previous one when it had one)
     * so that {@link #insert} with its {@link #placementOf placement} puts it back exactly.
     *
     * @return the remaining layout, or empty when the node was the last one
     * @throws IllegalArgumentException when the node is not in this layout
     */
    default Optional<DockLayout> remove(String id) {
        if (!contains(id)) {
            throw new IllegalArgumentException("not in this layout: " + id);
        }
        return Optional.ofNullable(removed(this, id));
    }

    /**
     * Where the node sits: which side of which neighbours, and how much of their joint extent it takes.
     *
     * @return the placement, or empty when the node is alone and so has no neighbours
     * @throws IllegalArgumentException when the node is not in this layout
     */
    default Optional<Placement> placementOf(String id) {
        if (!contains(id)) {
            throw new IllegalArgumentException("not in this layout: " + id);
        }
        return Optional.ofNullable(placement(this, id));
    }

    /**
     * This layout with the node added at its remembered place, beside the smallest subtree that holds every anchor
     * still present.
     *
     * @return the grown layout, or empty when none of the placement's anchors is here to place the node against
     * @throws IllegalArgumentException when the node is already in this layout
     */
    default Optional<DockLayout> insert(String id, Placement placement) {
        DockLayoutText.requireId(id);
        Objects.requireNonNull(placement, "placement");
        if (contains(id)) {
            throw new IllegalArgumentException("already in this layout: " + id);
        }
        final Set<String> present = new LinkedHashSet<>(placement.anchors());
        present.retainAll(ids());
        if (present.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(placed(this, new Leaf(id), placement, present));
    }

    private static void collectIds(DockLayout layout, Set<String> into) {
        switch (layout) {
            case Leaf leaf -> into.add(leaf.id());
            case Split split -> split.entries().forEach(entry -> collectIds(entry.child(), into));
        }
    }

    /** The subtree without the leaf, or null when the subtree was that leaf. */
    private static DockLayout removed(DockLayout layout, String id) {
        return switch (layout) {
            case Leaf leaf -> leaf.id().equals(id) ? null : leaf;
            case Split split -> {
                final List<Entry> entries = new ArrayList<>(split.entries());
                final int at = indexHolding(split, id);
                final Entry entry = entries.get(at);
                final DockLayout rest = removed(entry.child(), id);
                if (rest != null) {
                    entries.set(at, new Entry(rest, entry.share()));
                    yield new Split(split.orientation(), entries);
                }
                entries.remove(at);
                final int neighbour = at > 0 ? at - 1 : 0;
                final Entry keeps = entries.get(neighbour);
                entries.set(neighbour, new Entry(keeps.child(), keeps.share() + entry.share()));
                yield entries.size() == 1 ? entries.getFirst().child() : new Split(split.orientation(), entries);
            }
        };
    }

    private static Placement placement(DockLayout layout, String id) {
        if (!(layout instanceof Split split)) {
            return null;
        }
        final int at = indexHolding(split, id);
        final Entry entry = split.entries().get(at);
        if (!(entry.child() instanceof Leaf)) {
            return placement(entry.child(), id);
        }
        final boolean afterNeighbour = at > 0;
        final Entry neighbour = split.entries().get(afterNeighbour ? at - 1 : at + 1);
        final DockPos side = split.orientation() == Orientation.HORIZONTAL
                ? (afterNeighbour ? DockPos.RIGHT : DockPos.LEFT)
                : (afterNeighbour ? DockPos.BOTTOM : DockPos.TOP);
        return new Placement(side, entry.share() / (entry.share() + neighbour.share()), neighbour.child().ids());
    }

    /**
     * {@code tree} holds every id in {@code present}. Returns it with the leaf placed beside the smallest subtree that
     * holds them all: as one more entry of that subtree's parent when the parent already splits the placement's way,
     * otherwise as a new two-way split wrapping the subtree.
     */
    private static DockLayout placed(DockLayout tree, Leaf leaf, Placement placement, Set<String> present) {
        if (tree instanceof Split split) {
            final int at = soleEntryHolding(split, present);
            if (at >= 0) {
                final Entry entry = split.entries().get(at);
                if (entry.child() instanceof Split inner && soleEntryHolding(inner, present) >= 0) {
                    return split.with(at, new Entry(placed(inner, leaf, placement, present), entry.share()));
                }
                if (split.orientation() == orientationOf(placement.side())) {
                    return beside(split, at, leaf, placement);
                }
                return split.with(at, new Entry(wrapped(entry.child(), leaf, placement), entry.share()));
            }
        }
        return wrapped(tree, leaf, placement);
    }

    /** The leaf as a new entry next to entry {@code at}, carved out of that entry's share. */
    private static Split beside(Split split, int at, Leaf leaf, Placement placement) {
        final List<Entry> entries = new ArrayList<>(split.entries());
        final Entry anchor = entries.get(at);
        final double leafShare = placement.share() * anchor.share();
        entries.set(at, new Entry(anchor.child(), anchor.share() - leafShare));
        entries.add(leadsOn(placement.side()) ? at : at + 1, new Entry(leaf, leafShare));
        return new Split(split.orientation(), entries);
    }

    /** A new two-way split of the leaf and the subtree, the leaf on the placement's side. */
    private static Split wrapped(DockLayout tree, Leaf leaf, Placement placement) {
        final Entry leafEntry = new Entry(leaf, placement.share());
        final Entry treeEntry = new Entry(tree, 1 - placement.share());
        return new Split(
                orientationOf(placement.side()),
                leadsOn(placement.side()) ? List.of(leafEntry, treeEntry) : List.of(treeEntry, leafEntry));
    }

    /** The index of the entry holding the id; ids are unique, so there is exactly one. */
    private static int indexHolding(Split split, String id) {
        for (int i = 0; i < split.entries().size(); i++) {
            if (split.entries().get(i).child().contains(id)) {
                return i;
            }
        }
        throw new IllegalStateException("not under this split: " + id);
    }

    /** The index of the one entry holding every id, or -1 when they are spread over several entries. */
    private static int soleEntryHolding(Split split, Set<String> ids) {
        for (int i = 0; i < split.entries().size(); i++) {
            if (split.entries().get(i).child().ids().containsAll(ids)) {
                return i;
            }
        }
        return -1;
    }

    /** LEFT and TOP put the node before its anchor in reading order; RIGHT and BOTTOM after. */
    private static boolean leadsOn(DockPos side) {
        return side == DockPos.LEFT || side == DockPos.TOP;
    }

    private static Orientation orientationOf(DockPos side) {
        return switch (side) {
            case LEFT, RIGHT -> Orientation.HORIZONTAL;
            case TOP, BOTTOM -> Orientation.VERTICAL;
            case CENTER -> throw new IllegalArgumentException("CENTER is not a side; a placement is LEFT, RIGHT, TOP or BOTTOM");
        };
    }
}
