package com.druvu.lib.fx.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.druvu.lib.fx.FxTestToolkit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;

public class NotificationsTest {

	@BeforeClass
	public void startToolkit() {
		FxTestToolkit.ensureStarted();
	}

	@Test
	public void showsAllLevelsOverAWindowWithoutError() throws InterruptedException {
		final CountDownLatch done = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		Platform.runLater(() -> {
			Stage stage = null;
			try {
				stage = new Stage();
				stage.setScene(new Scene(new Pane(), 320, 240));
				stage.show();

				final Notifications notifications = new Notifications(stage);
				notifications.info("info");
				notifications.success("success");
				notifications.warning("warning");
				notifications.error("error");
				notifications.show("custom", Notifications.Level.INFO, Duration.millis(50));
				notifications.close(); // clears the stack and hides the popup
			} catch (Throwable t) {
				failure.set(t);
			} finally {
				if (stage != null) {
					stage.hide();
				}
				done.countDown();
			}
		});

		assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		if (failure.get() != null) {
			throw new AssertionError("notifications smoke failed", failure.get());
		}
	}
}
