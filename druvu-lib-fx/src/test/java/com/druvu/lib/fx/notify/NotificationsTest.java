package com.druvu.lib.fx.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.theme.FxTheme;
import com.druvu.lib.fx.theme.ThemeManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class NotificationsTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    /**
     * A window hanging below the taskbar must not push its toasts behind it: the anchor stays inside the visual bounds.
     */
    @Test
    public void anchorStaysInsideTheScreensVisualBounds() {
        final Rectangle2D visual = new Rectangle2D(-1920, 0, 1920, 1032); // a 1080p screen minus its taskbar
        // the measured case: window at (-1272, 114), 977 x 975 -> wanted anchor y = 1073, below the taskbar's top
        final Point2D clamped = Notifications.anchorWithin(-1272 + 977 - 16, 114 + 975 - 16, visual);
        assertThat(clamped.getY()).isEqualTo(1032 - 16);
        assertThat(clamped.getX()).isEqualTo(-1272 + 977 - 16);

        final Point2D inside = Notifications.anchorWithin(-500, 500, visual);
        assertThat(inside).isEqualTo(new Point2D(-500, 500));

        assertThat(Notifications.anchorWithin(10, 20, null)).isEqualTo(new Point2D(10, 20));
    }

    /**
     * A toast shown after the popup hid itself (the previous one expired) must get its theme colours at once. Before
     * the fix the CSS pass ran on the hidden popup, every lookup failed, and the toast painted with no background.
     */
    @Test
    public void toastShownAfterThePopupHidResolvesItsColoursAtOnce() throws InterruptedException {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<String> verdict = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                new ThemeManager().apply(FxTheme.PRIMER_LIGHT);
                final Stage stage = new Stage();
                stage.setScene(new Scene(new Pane(), 320, 240));
                stage.show();
                final Notifications notifications = new Notifications(stage);
                notifications.show("first", Notifications.Level.SUCCESS, Duration.millis(80));
                final PauseTransition gone = new PauseTransition(Duration.millis(900)); // 80 + 250 fade: popup hidden
                gone.setOnFinished(e -> {
                    try {
                        notifications.show("second", Notifications.Level.SUCCESS, Duration.seconds(5));
                        final Popup popup = Window.getWindows().stream()
                                .filter(w -> w instanceof Popup p && p.getOwnerWindow() == stage)
                                .map(w -> (Popup) w)
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("toast popup not showing"));
                        final Parent stack = (Parent) popup.getContent().get(0);
                        final Region toast =
                                (Region) stack.getChildrenUnmodifiable().get(0);
                        final boolean painted = toast.getBackground() != null
                                && !toast.getBackground().getFills().isEmpty();
                        verdict.set(painted ? "painted" : "no background");
                    } catch (Throwable t) {
                        failure.set(t);
                    } finally {
                        stage.close();
                        done.countDown();
                    }
                });
                gone.play();
            } catch (Throwable t) {
                failure.set(t);
                done.countDown();
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS)).as("test finished").isTrue();
        assertThat(failure.get()).isNull();
        assertThat(verdict.get()).isEqualTo("painted");
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
