package com.druvu.lib.fx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Application;
import javafx.application.Platform;

/**
 * Starts the JavaFX toolkit once for the whole test JVM. The toolkit cannot be restarted after Platform.exit(), so no
 * test may ever call exit - the surefire fork ends with System.exit, which tears the FX thread down.
 */
public final class FxTestToolkit {

    private static final CountDownLatch STARTED = new CountDownLatch(1);

    private FxTestToolkit() {}

    public static synchronized void ensureStarted() {
        try {
            Platform.startup(STARTED::countDown);
        } catch (IllegalStateException alreadyRunning) {
            STARTED.countDown();
        }
        // A test that shows then hides a Stage would otherwise drop the window count to zero and
        // trigger implicit Platform.exit(), killing this un-restartable toolkit for the whole JVM
        // and hanging every later FX test. Keep the toolkit alive regardless of window count.
        Platform.setImplicitExit(false);
        try {
            if (!STARTED.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("JavaFX toolkit did not start within 10s");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting JavaFX toolkit", ex);
        }
    }

    /**
     * Runs an action on the FX thread and waits for it, rethrowing whatever it threw. Failures surface as test failures
     * instead of vanishing into the FX thread's uncaught handler.
     */
    public static void runOnFx(Runnable action) throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).as("FX action completed").isTrue();
        if (failure.get() != null) {
            throw new AssertionError("FX action failed", failure.get());
        }
    }

    /**
     * Puts the JVM back on the default stylesheet. The user agent stylesheet is process-global, so a test class that
     * applies a theme must undo it or every later FX test renders under whatever it left behind.
     */
    public static void resetUserAgentStylesheet() throws InterruptedException {
        runOnFx(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }
}
