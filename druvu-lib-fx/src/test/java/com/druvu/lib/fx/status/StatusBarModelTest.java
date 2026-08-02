package com.druvu.lib.fx.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.TaskEvent;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class StatusBarModelTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    /**
     * A model built after a task has already started sees the {@code Finished} it never saw begin. Left unguarded that
     * drives the count negative and it never recovers - the showcase app read "tasks: -1" for a whole session. Any app
     * that builds its status bar after kicking off a startup load reproduces this.
     */
    @Test
    public void countNeverGoesNegativeWhenSubscribedMidFlight() throws InterruptedException {
        final FxBus bus = new FxBus();
        final StatusBarModel model = new StatusBarModel(bus);
        final CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            // No matching Started: this model was created after the task began.
            bus.publish(new TaskEvent.Finished(7, "startup load", Duration.ofMillis(3)));
            Platform.runLater(() -> {
                assertThat(model.runningTasksProperty().get())
                        .as("unmatched Finished must floor at zero, not -1")
                        .isZero();
                assertThat(model.busyProperty().get()).isFalse();

                // And the count must still work afterwards - a floored model is not a stuck model.
                bus.publish(new TaskEvent.Started(8, "later"));
                Platform.runLater(() -> {
                    assertThat(model.runningTasksProperty().get()).isEqualTo(1);
                    assertThat(model.busyProperty().get()).isTrue();
                    model.close();
                    done.countDown();
                });
            });
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).as("assertions ran").isTrue();
    }

    @Test
    public void tracksRunningCountBusyAndMessage() throws InterruptedException {
        final FxBus bus = new FxBus();
        final StatusBarModel model = new StatusBarModel(bus);
        final CountDownLatch done = new CountDownLatch(1);

        // Publish on the FX thread, then assert from a runLater enqueued AFTER the deliveries:
        // Delivery.FX always hops via runLater, so FIFO ordering makes the check deterministic.
        Platform.runLater(() -> {
            bus.publish(new TaskEvent.Started(1, "load"));
            bus.publish(new TaskEvent.Started(2, "sync"));
            bus.publish(new TaskEvent.Finished(1, "load", Duration.ofMillis(5)));
            Platform.runLater(() -> {
                assertThat(model.runningTasksProperty().get()).isEqualTo(1);
                assertThat(model.busyProperty().get()).isTrue();
                assertThat(model.messageProperty().get()).isEqualTo("done: load");

                bus.publish(new TaskEvent.Failed(2, "sync", new RuntimeException("boom"), Duration.ofMillis(9)));
                Platform.runLater(() -> {
                    assertThat(model.runningTasksProperty().get()).isZero();
                    assertThat(model.busyProperty().get()).isFalse();
                    assertThat(model.messageProperty().get()).isEqualTo("failed: sync");
                    model.close();
                    done.countDown();
                });
            });
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void closeStopsTrackingFurtherEvents() throws InterruptedException {
        final FxBus bus = new FxBus();
        final StatusBarModel model = new StatusBarModel(bus);
        final CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            model.close();
            bus.publish(new TaskEvent.Started(1, "after-close"));
            Platform.runLater(() -> {
                assertThat(model.runningTasksProperty().get()).isZero();
                assertThat(model.messageProperty().get()).isEmpty();
                done.countDown();
            });
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    }
}
