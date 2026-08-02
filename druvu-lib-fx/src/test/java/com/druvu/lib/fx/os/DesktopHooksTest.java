package com.druvu.lib.fx.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.druvu.lib.fx.FxTestToolkit;
import java.util.Locale;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The contract worth guarding here is that the same call site is safe everywhere: these hooks exist only on macOS, so
 * on Windows and Linux every method must decline quietly rather than throw. That is what lets an app register them
 * unconditionally, and it is also what makes this test class meaningful on the Linux CI runner, where every hook is
 * expected to be unavailable.
 *
 * <p>What cannot be tested here: the FX-thread hop and the quit veto. Both need the OS to deliver a real application
 * event, which no test can fire - they are guarded by review and by the manual macOS smoke instead.
 */
public class DesktopHooksTest {

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void rejectsNullActions() {
        assertThatNullPointerException().isThrownBy(() -> DesktopHooks.onAbout(null));
        assertThatNullPointerException().isThrownBy(() -> DesktopHooks.onPreferences(null));
        assertThatNullPointerException().isThrownBy(() -> DesktopHooks.onQuit(null));
        assertThatNullPointerException().isThrownBy(() -> DesktopHooks.onOpenFiles(null));
    }

    /** No method may throw on any platform - declining is reported by the return value, never by an exception. */
    @Test
    public void registrationNeverThrowsAndAgreesWithIsSupported() throws Exception {
        FxTestToolkit.runOnFx(() -> {
            final boolean about = DesktopHooks.onAbout(() -> {});
            final boolean preferences = DesktopHooks.onPreferences(() -> {});
            final boolean quit = DesktopHooks.onQuit(() -> true);
            final boolean openFiles = DesktopHooks.onOpenFiles(paths -> {});

            assertThat(about || preferences || quit || openFiles)
                    .as("isSupported() must agree with whether any hook actually installed")
                    .isEqualTo(DesktopHooks.isSupported());
        });
    }

    /** The JDK keeps one handler per event, so re-registering is a replace: the answer must not drift. */
    @Test
    public void registrationIsRepeatable() throws Exception {
        FxTestToolkit.runOnFx(() -> {
            final boolean first = DesktopHooks.onAbout(() -> {});
            final boolean second = DesktopHooks.onAbout(() -> {});
            assertThat(second).isEqualTo(first);
        });
    }

    /** isSupported() is a pure query - asking must not install anything or change the answer. */
    @Test
    public void isSupportedIsStable() {
        assertThat(DesktopHooks.isSupported()).isEqualTo(DesktopHooks.isSupported());
    }

    /**
     * On macOS the application menu is real, so the hooks must actually take. Elsewhere the cross-platform assertions
     * above are the whole contract and there is nothing platform-specific left to check.
     */
    @Test
    public void applicationMenuIsAvailableOnMacOs() throws Exception {
        if (!MAC) {
            return;
        }
        FxTestToolkit.runOnFx(() -> {
            assertThat(DesktopHooks.isSupported())
                    .as("macOS has an application menu")
                    .isTrue();
            assertThat(DesktopHooks.onAbout(() -> {})).as("About").isTrue();
            assertThat(DesktopHooks.onPreferences(() -> {})).as("Preferences").isTrue();
            assertThat(DesktopHooks.onQuit(() -> true)).as("Quit").isTrue();
            assertThat(DesktopHooks.onOpenFiles(paths -> {})).as("Open files").isTrue();
        });
    }
}
