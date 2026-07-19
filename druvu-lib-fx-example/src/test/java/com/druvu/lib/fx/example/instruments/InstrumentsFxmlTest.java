package com.druvu.lib.fx.example.instruments;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * FXML smoke test: catches fx:id/controller/handler wiring mistakes at build time instead of at app startup. Loads the
 * page off-screen; no Stage is shown.
 */
public class InstrumentsFxmlTest {

    @BeforeClass
    public void startToolkit() throws InterruptedException {
        final CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testFxmlLoadsAndWiresController() throws IOException {
        final FXMLLoader loader = new FXMLLoader(InstrumentsController.class.getResource("instruments.fxml"));
        final Parent root = loader.load();

        assertThat(root).isNotNull();
        assertThat(loader.<InstrumentsController>getController()).isNotNull();
        assertThat(root.lookup("#table")).isNotNull();
        assertThat(root.lookup("#searchField")).isNotNull();
        assertThat(root.lookup("#maxPrice")).isNotNull();
    }
}
