package com.druvu.lib.fx.dock;

import static com.druvu.lib.fx.dock.DockWorkspace.WORKSPACE;
import static com.druvu.lib.fx.dock.DockWorkspace.assertSameShape;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.prefs.Prefs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import javafx.scene.control.Label;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Covers DockLayoutPersistence: a saved layout and hidden placements take over at install, every change is saved
 * without waiting for a hide, malformed text leaves the default layout, and every panel needs an id.
 */
public class DockLayoutPersistenceTest {

    private static final String DEFAULT_SHAPE =
            "V\\{[0-9.]+ H\\{[0-9.]+ connections,[0-9.]+ positions\\},[0-9.]+ orders\\}";

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void aSavedLayoutTakesOverFromTheDefaults() throws Exception {
        final String saved = "H{0.300 orders,0.700 V{0.500 connections,0.500 positions}}";
        final Path file = prefsFile("dock.layout=" + saved);
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.dockDefaults();
                DockLayoutPersistence.install(w.pane, new Prefs(file), w.all());
                w.show();
                assertSameShape(w.pane.dockLayout().orElseThrow(), saved);
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void hiddenPanelsRememberWhereTheyGoBackTo() throws Exception {
        final Path file = prefsFile(
                "dock.layout=H{0.350 connections,0.650 positions}",
                "dock.hidden.orders=BOTTOM 0.300 connections,positions");
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.dockDefaults();
                DockLayoutPersistence.install(w.pane, new Prefs(file), w.all());
                w.show();
                assertThat(w.orders.isDocked())
                        .as("hidden when the app last quit")
                        .isFalse();

                w.pane.redock(w.orders, DockPos.LEFT);
                w.layoutPass();
                assertSameShape(w.pane.dockLayout().orElseThrow(), WORKSPACE);
            } finally {
                w.dispose();
            }
        });
    }

    /** Platform.exit() hides nothing, so the file must already hold the change when the app quits. */
    @Test
    public void everyChangeIsSavedWithoutWaitingForAHide() throws Exception {
        final Path file = prefsFile();
        final DockWorkspace w = FxTestToolkit.call(() -> {
            final DockWorkspace workspace = new DockWorkspace();
            workspace.dockDefaults();
            DockLayoutPersistence.install(workspace.pane, new Prefs(file), workspace.all());
            workspace.show();
            return workspace;
        });
        try {
            FxTestToolkit.runOnFx(w.orders::close);
            awaitFile(file, prefs -> prefs.contains("dock.hidden.orders"));
            Prefs reread = new Prefs(file);
            assertThat(reread.get("dock.layout", "")).matches("H\\{[0-9.]+ connections,[0-9.]+ positions\\}");
            assertThat(reread.get("dock.hidden.orders", "")).startsWith("BOTTOM ");

            FxTestToolkit.runOnFx(() -> w.pane.redock(w.orders, DockPos.LEFT));
            awaitFile(file, prefs -> !prefs.contains("dock.hidden.orders"));
            reread = new Prefs(file);
            assertThat(reread.get("dock.layout", "")).matches(DEFAULT_SHAPE);
        } finally {
            FxTestToolkit.runOnFx(w::dispose);
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void malformedTextLeavesTheDefaultLayout() throws Exception {
        final Path file = prefsFile("dock.layout=V{0.5 connections", "dock.hidden.orders=SIDEWAYS 0.3 connections");
        FxTestToolkit.runOnFx(() -> {
            final DockWorkspace w = new DockWorkspace();
            try {
                w.dockDefaults();
                DockLayoutPersistence.install(w.pane, new Prefs(file), w.all());
                assertThat(w.pane.dockLayout())
                        .map(DockLayout::format)
                        .get()
                        .asString()
                        .matches(DEFAULT_SHAPE);
                assertThat(w.pane.hiddenPlacements()).isEmpty();
            } finally {
                w.dispose();
            }
        });
    }

    @Test
    public void everyPanelNeedsAnId() throws Exception {
        final Path file = prefsFile();
        FxTestToolkit.runOnFx(() -> {
            final DockPane pane = new DockPane();
            final DockNode anonymous = new DockNode(new Label("x"), "Anonymous");
            assertThatThrownBy(() -> DockLayoutPersistence.install(pane, new Prefs(file), List.of(anonymous)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Anonymous");
        });
    }

    private static Path prefsFile(String... lines) throws IOException {
        final Path file = Files.createTempFile("druvu-dock", ".properties");
        Files.write(file, List.of(lines));
        return file;
    }

    /** Polls past the save debounce; a fresh Prefs reads the file the way the next run of the app would. */
    private static void awaitFile(Path file, Predicate<Prefs> condition) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.test(new Prefs(file)) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(condition.test(new Prefs(file)))
                .as("file caught up within 5s")
                .isTrue();
    }
}
