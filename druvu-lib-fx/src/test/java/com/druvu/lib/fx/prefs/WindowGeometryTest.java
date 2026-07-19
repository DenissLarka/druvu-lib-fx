package com.druvu.lib.fx.prefs;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
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
}
