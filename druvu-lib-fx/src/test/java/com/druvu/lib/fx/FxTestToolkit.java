package com.druvu.lib.fx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit once for the whole test JVM. The toolkit cannot be restarted
 * after Platform.exit(), so no test may ever call exit - the surefire fork ends with
 * System.exit, which tears the FX thread down.
 */
public final class FxTestToolkit {

	private static final CountDownLatch STARTED = new CountDownLatch(1);

	private FxTestToolkit() {
	}

	public static synchronized void ensureStarted() {
		try {
			Platform.startup(STARTED::countDown);
		} catch (IllegalStateException alreadyRunning) {
			STARTED.countDown();
		}
		try {
			if (!STARTED.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("JavaFX toolkit did not start within 10s");
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while starting JavaFX toolkit", ex);
		}
	}
}
