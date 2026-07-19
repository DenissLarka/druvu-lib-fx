package com.druvu.lib.fx.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.exec.FxExec;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class LoginPaneTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void successInvokesOnSuccessWithPrincipalOffThenBackOnFx() throws InterruptedException {
        try (FxExec exec = new FxExec()) {
            final AtomicReference<String> principal = new AtomicReference<>();
            final AtomicBoolean onFxThread = new AtomicBoolean(false);
            final CountDownLatch done = new CountDownLatch(1);

            final Authenticator<String> auth = (user, pass) -> {
                // runs off the FX thread
                if ("neo".equals(user) && "matrix".equals(new String(pass))) {
                    return "principal:" + user;
                }
                throw new IllegalArgumentException("Invalid username or password");
            };

            Platform.runLater(() -> {
                final LoginPane<String> pane = new LoginPane<>(exec, auth, p -> {
                    onFxThread.set(Platform.isFxApplicationThread());
                    principal.set(p);
                    done.countDown();
                });
                pane.submit("neo", "matrix".toCharArray());
            });

            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(principal.get()).isEqualTo("principal:neo");
            assertThat(onFxThread.get()).isTrue();
        }
    }

    @Test
    public void rejectionDoesNotCallOnSuccessAndClearsBusy() throws InterruptedException {
        try (FxExec exec = new FxExec()) {
            final AtomicBoolean succeeded = new AtomicBoolean(false);
            final AtomicReference<LoginPane<String>> ref = new AtomicReference<>();
            final CountDownLatch built = new CountDownLatch(1);

            final Authenticator<String> auth = (user, pass) -> {
                throw new IllegalStateException("nope");
            };

            Platform.runLater(() -> {
                final LoginPane<String> pane = new LoginPane<>(exec, auth, p -> succeeded.set(true));
                ref.set(pane);
                pane.submit("who", "ever".toCharArray());
                built.countDown();
            });
            assertThat(built.await(10, TimeUnit.SECONDS)).isTrue();

            // Poll until the async rejection has settled (busy back to false), then verify.
            final CountDownLatch settled = new CountDownLatch(1);
            pollBusyCleared(ref, settled);
            assertThat(settled.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(succeeded.get()).isFalse();
        }
    }

    private void pollBusyCleared(AtomicReference<LoginPane<String>> ref, CountDownLatch settled) {
        Platform.runLater(() -> {
            if (ref.get() != null && !ref.get().busyProperty().get()) {
                settled.countDown();
            } else {
                pollBusyCleared(ref, settled);
            }
        });
    }
}
