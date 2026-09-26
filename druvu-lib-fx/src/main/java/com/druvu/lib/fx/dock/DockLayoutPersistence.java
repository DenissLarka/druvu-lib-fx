package com.druvu.lib.fx.dock;

import com.druvu.lib.fx.dock.DockLayout.Placement;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.util.Debounce;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps a {@link DockPane}'s workspace across runs in a {@link Prefs} store, the way
 * {@link com.druvu.lib.fx.prefs.WindowGeometry} keeps a window. Call {@link #install} once, right after the app has
 * docked its default layout: a saved layout takes over, and from then on every change - a panel hidden, shown or
 * dragged elsewhere, a divider moved - is saved, debounced so a drag costs one write.
 *
 * <p>Keys under a prefix (default {@code "dock"}): {@code dock.layout} holds the workspace in {@link DockLayout} text
 * form, {@code dock.hidden.<id>} where each hidden panel goes back to. Panels are matched by their JavaFX id, so every
 * node handed in needs one. A value that does not parse (the file is hand-editable) is logged and skipped, and the
 * app's default layout stands.
 *
 * @author Deniss Larka <br>
 *     on 26 Sep 2026
 */
public final class DockLayoutPersistence {

    private static final Logger LOG = LoggerFactory.getLogger(DockLayoutPersistence.class);

    private static final String DEFAULT_PREFIX = "dock";

    /** Long enough to fold a divider drag into one write. */
    private static final Duration SAVE_DELAY = Duration.millis(300);

    private DockLayoutPersistence() {}

    /** Restores the saved workspace now, if there is one, and saves it again on every change. FX thread. */
    public static void install(DockPane pane, Prefs prefs, Collection<? extends DockNode> nodes) {
        install(pane, prefs, DEFAULT_PREFIX, nodes);
    }

    /**
     * As {@link #install(DockPane, Prefs, Collection)}, with an explicit key prefix.
     *
     * @throws IllegalArgumentException when a node has no id, or two share one
     */
    public static void install(DockPane pane, Prefs prefs, String prefix, Collection<? extends DockNode> nodes) {
        Objects.requireNonNull(pane, "pane");
        Objects.requireNonNull(prefs, "prefs");
        Objects.requireNonNull(prefix, "prefix");
        final List<DockNode> panels = new ArrayList<>(nodes);
        final Set<String> ids = new HashSet<>();
        for (DockNode panel : panels) {
            if (panel.getId() == null || panel.getId().isBlank()) {
                throw new IllegalArgumentException(
                        "dock node '" + panel.getTitle() + "' has no id; every persisted panel needs setId(...)");
            }
            if (!ids.add(panel.getId())) {
                throw new IllegalArgumentException("two dock nodes share the id " + panel.getId());
            }
        }
        restore(pane, prefs, prefix, panels);
        final Debounce pendingSave = new Debounce(SAVE_DELAY, () -> save(pane, prefs, prefix, panels));
        pane.layoutRevisionProperty().addListener(observable -> pendingSave.trigger());
    }

    static void restore(DockPane pane, Prefs prefs, String prefix, List<DockNode> panels) {
        for (DockNode panel : panels) {
            final String key = hiddenKey(prefix, panel);
            final String text = prefs.get(key, null);
            if (text != null) {
                try {
                    pane.rememberPlacement(panel.getId(), Placement.parse(text));
                } catch (IllegalArgumentException malformed) {
                    LOG.warn("Ignoring {} = '{}': {}", key, text, malformed.getMessage());
                }
            }
        }
        final String key = layoutKey(prefix);
        final String text = prefs.get(key, null);
        if (text != null) {
            try {
                pane.apply(DockLayout.parse(text), panels);
            } catch (IllegalArgumentException malformed) {
                LOG.warn("Ignoring {} = '{}': {}", key, text, malformed.getMessage());
            }
        }
    }

    static void save(DockPane pane, Prefs prefs, String prefix, List<DockNode> panels) {
        final Optional<DockLayout> layout = pane.dockLayout();
        if (layout.isPresent()) {
            prefs.put(layoutKey(prefix), layout.get().format());
        } else {
            prefs.remove(layoutKey(prefix));
        }
        final Map<String, Placement> hidden = pane.hiddenPlacements();
        for (DockNode panel : panels) {
            final Placement placement = hidden.get(panel.getId());
            if (placement != null) {
                prefs.put(hiddenKey(prefix, panel), placement.format());
            } else {
                prefs.remove(hiddenKey(prefix, panel));
            }
        }
    }

    private static String layoutKey(String prefix) {
        return prefix + ".layout";
    }

    private static String hiddenKey(String prefix, DockNode panel) {
        return prefix + ".hidden." + panel.getId();
    }
}
