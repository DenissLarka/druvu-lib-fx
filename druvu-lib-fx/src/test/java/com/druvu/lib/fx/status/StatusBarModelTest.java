package com.druvu.lib.fx.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.bus.FxBus;
import com.druvu.lib.fx.exec.TaskEvent;

import javafx.application.Platform;

public class StatusBarModelTest {

	@BeforeClass
	public void startToolkit() {
		FxTestToolkit.ensureStarted();
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

				bus.publish(new TaskEvent.Failed(2, "sync", new RuntimeException("boom"),
						Duration.ofMillis(9)));
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
