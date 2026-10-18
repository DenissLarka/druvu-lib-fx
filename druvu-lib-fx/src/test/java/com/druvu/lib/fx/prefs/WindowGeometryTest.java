package com.druvu.lib.fx.prefs;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WindowGeometryTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void savesThenRestoresBounds() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-window", ".properties");
        Files.delete(file);
        final Prefs prefs = new Prefs(file);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<double[]> restored = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                final Stage source = new Stage();
                source.setX(150);
                source.setY(160);
                source.setWidth(640);
                source.setHeight(480);
                WindowGeometry.save(source, prefs, "window");

                final Stage target = new Stage();
                target.setWidth(100);
                target.setHeight(100);
                WindowGeometry.restore(target, prefs, "window");
                restored.set(new double[] {target.getX(), target.getY(), target.getWidth(), target.getHeight()});
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw new AssertionError("window-geometry round-trip failed", failure.get());
        }
        Files.deleteIfExists(file);
        assertThat(restored.get()).containsExactly(150, 160, 640, 480);
    }

    /**
     * Platform.exit() - the Quit menu item, Cmd+Q on macOS - never hides the stage, so a save that waits for the hide
     * loses the window on every quit but the close button (measured on JavaFX 25). The geometry must reach the file
     * while the stage is still showing: move it, wait out the debounce, read the file back before any hide.
     */
    @Test
    public void savesWhileStillShowing() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-window", ".properties");
        Files.delete(file);
        final Prefs prefs = new Prefs(file);
        final AtomicReference<Stage> shown = new AtomicReference<>();
        final CountDownLatch moved = new CountDownLatch(1);

        Platform.runLater(() -> {
            final Stage stage = new Stage();
            stage.setWidth(320);
            stage.setHeight(240);
            WindowGeometry.install(stage, prefs, "window");
            stage.show();
            stage.setX(210);
            stage.setY(140);
            shown.set(stage);
            moved.countDown();
        });
        assertThat(moved.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            // A fresh Prefs reads the file the way the next run of the app would; poll past the debounce.
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (new Prefs(file).getDouble("window.x", -1) != 210 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            final Prefs reread = new Prefs(file);
            assertThat(reread.getDouble("window.x", -1)).isEqualTo(210);
            assertThat(reread.getDouble("window.y", -1)).isEqualTo(140);
            assertThat(reread.getDouble("window.width", -1)).isEqualTo(320);
            assertThat(reread.getDouble("window.height", -1)).isEqualTo(240);
        } finally {
            Platform.runLater(() -> shown.get().hide());
            Files.deleteIfExists(file);
        }
    }

    /**
     * A JavaFX Dialog centres its window before every show ({@code HeavyweightDialog.show}); the saved position must
     * still win. The test does what the dialog does - centerOnScreen, then show - for a first and a second opening.
     */
    @Test
    public void savedPositionSurvivesACentreBeforeEachShow() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-window", ".properties");
        Files.write(file, List.of("dialog.x=210", "dialog.y=180", "dialog.width=400", "dialog.height=300"));
        try {
            final double[][] positions = FxTestToolkit.call(() -> {
                final Stage stage = new Stage();
                WindowGeometry.install(stage, new Prefs(file), "dialog");
                stage.centerOnScreen();
                stage.show();
                final double[] first = {stage.getX(), stage.getY()};
                stage.hide();
                stage.centerOnScreen();
                stage.show();
                final double[] second = {stage.getX(), stage.getY()};
                stage.hide();
                return new double[][] {first, second};
            });
            assertThat(positions[0]).containsExactly(210, 180);
            assertThat(positions[1]).containsExactly(210, 180);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /**
     * A monitor unplugged since the save: the saved position lies on no screen, so the window opens centred on the
     * primary screen's usable area, at its saved size. The placement rules themselves are in WindowPlacementTest.
     */
    @Test
    public void positionOnNoScreenOpensCentredOnThePrimary() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-window", ".properties");
        Files.write(file, List.of("window.x=100000", "window.y=100000", "window.width=400", "window.height=300"));
        try {
            final double[] restored = FxTestToolkit.call(() -> {
                final Stage stage = new Stage();
                WindowGeometry.restore(stage, new Prefs(file), "window");
                return new double[] {stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()};
            });
            final Rectangle2D primary =
                    FxTestToolkit.call(() -> Screen.getPrimary().getVisualBounds());
            assertThat(restored)
                    .containsExactly(
                            primary.getMinX() + (primary.getWidth() - 400) / 2,
                            primary.getMinY() + (primary.getHeight() - 300) / 2,
                            400,
                            300);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Windows parks a minimized window far off screen; a quit while minimized must not save that spot. */
    @Test
    public void aMinimizedWindowKeepsItsLastBounds() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-window", ".properties");
        Files.write(file, List.of("window.x=210", "window.y=140", "window.width=400", "window.height=300"));
        try {
            final Prefs prefs = new Prefs(file);
            FxTestToolkit.runOnFx(() -> {
                final Stage stage = new Stage();
                stage.setIconified(true);
                stage.setX(-32000);
                stage.setY(-32000);
                WindowGeometry.save(stage, prefs, "window");
            });
            final Prefs reread = new Prefs(file);
            assertThat(reread.getDouble("window.x", -1)).isEqualTo(210);
            assertThat(reread.getDouble("window.y", -1)).isEqualTo(140);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
