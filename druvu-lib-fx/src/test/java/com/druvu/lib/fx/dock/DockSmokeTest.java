package com.druvu.lib.fx.dock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.druvu.lib.fx.FxTestToolkit;

import javafx.application.Platform;
import javafx.scene.control.Label;

/**
 * Smoke test for the vendored dock: proves the default stylesheet resource resolves and that a
 * basic dock graph builds on the FX thread on Java 25 (guards the com.sun.* removals in
 * {@link DockEvent} and {@link DockPane} - a regression there fails the graph build, not just
 * styling).
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
}
