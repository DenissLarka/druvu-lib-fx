package com.druvu.lib.fx.dock;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.theme.KitStyles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Smoke test for the vendored dock: proves the default stylesheet resource resolves and that a basic dock graph builds
 * on the FX thread on Java 25 (guards the com.sun.* removals in {@link DockEvent} and {@link DockPane} - a regression
 * there fails the graph build, not just styling).
 */
public class DockSmokeTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void defaultStylesheetResolves() {
        final String css = DockPane.defaultStylesheet();
        assertThat(css).isNotNull().endsWith("default.css");
        assertThat(DockPane.class.getResource("default.css")).isNotNull();
    }

    @Test
    public void dockGraphBuildsOnFxThread() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                final DockPane dockPane = new DockPane();

                // CENTER is only valid as the FIRST dock (root == null); side positions thereafter.
                final DockNode dashboard = new DockNode(new Label("dashboard"), "Dashboard");
                dashboard.dock(dockPane, DockPos.CENTER);

                final DockNode instruments = new DockNode(new Label("instruments"), "Instruments");
                instruments.dock(dockPane, DockPos.RIGHT);

                final DockNode tasks = new DockNode(new Label("tasks"), "Tasks");
                tasks.dock(dockPane, DockPos.BOTTOM);

                assertThat(dockPane.getChildren()).isNotEmpty();
                assertThat(dockPane.getStylesheets()).contains(DockPane.defaultStylesheet());
                assertThat(dashboard.isDocked()).isTrue();
                assertThat(instruments.isDocked()).isTrue();
                assertThat(tasks.isDocked()).isTrue();

                // A pinned node (non-closable + non-floatable) is the reopen-menu pattern: the
                // floatable flag must be honoured so a title-bar drag cannot detach it.
                dashboard.setClosable(false);
                dashboard.setFloatable(false);
                assertThat(dashboard.isClosable()).isFalse();
                assertThat(dashboard.isFloatable()).isFalse();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("dock graph build failed on FX thread", failure.get());
        }
    }

    /**
     * A floated node lives in its own Scene. The user-agent theme still applies there (it is JVM-global), but
     * stylesheets the application attached to its scene or to the dock pane would silently stop - the float rendered
     * themed-but-unstyled (found in the field: druvu-wealth-app panels lost font, P&L colours and pills on detach). The
     * float must carry those stylesheets over, dock stylesheet first, without duplicating them.
     */
    @Test
    public void floatingNodeCarriesApplicationStylesheets() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            Stage stage = null;
            DockNode panel = null;
            try {
                // A real, resolvable stylesheet distinct from the dock's own (JavaFX warns on fakes).
                final String appStylesheet =
                        KitStyles.class.getResource("druvu-kit.css").toExternalForm();

                final DockPane dockPane = new DockPane();
                panel = new DockNode(new Label("positions"), "Positions");
                panel.dock(dockPane, DockPos.CENTER);

                final Scene scene = new Scene(dockPane, 300, 200);
                scene.getStylesheets().add(appStylesheet);
                // The wealth-app pattern: the app sheet is ALSO appended to the DockPane to win the
                // nearer-parent precedence contest; the float must not end up with it twice.
                dockPane.getStylesheets().add(appStylesheet);
                stage = new Stage();
                stage.setScene(scene);
                stage.show();

                panel.setFloating(true);

                assertThat(panel.getScene())
                        .as("floating node has its own scene")
                        .isNotNull();
                assertThat(panel.getScene()).isNotSameAs(scene);
                final var floatSheets = panel.getScene().getRoot().getStylesheets();
                assertThat(floatSheets.getFirst())
                        .as("dock stylesheet stays first so app rules win ties")
                        .isEqualTo(DockPane.defaultStylesheet());
                assertThat(floatSheets)
                        .as("application stylesheet carried over, exactly once")
                        .containsOnlyOnce(appStylesheet);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                if (panel != null && panel.isFloating()) {
                    panel.close();
                }
                if (stage != null) {
                    stage.hide();
                }
                done.countDown();
            }
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("floating stylesheet carry-over failed", failure.get());
        }
    }
}
