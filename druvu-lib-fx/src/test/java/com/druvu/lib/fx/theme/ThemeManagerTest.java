package com.druvu.lib.fx.theme;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.prefs.Prefs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ThemeManagerTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void everyThemeResolvesItsStylesheet() {
        for (FxTheme theme : FxTheme.values()) {
            assertThat(theme.stylesheet()).as("stylesheet of %s", theme).isNotBlank();
            assertThat(getClass().getClassLoader().getResource(stripJarPrefix(theme.stylesheet())))
                    .as("stylesheet resource of %s exists", theme)
                    .isNotNull();
        }
    }

    @Test
    public void byNameIsCaseInsensitiveAndTotal() {
        assertThat(FxTheme.byName("dracula")).contains(FxTheme.DRACULA);
        assertThat(FxTheme.byName("  PRIMER_DARK ")).contains(FxTheme.PRIMER_DARK);
        assertThat(FxTheme.byName("no-such-theme")).isEmpty();
        assertThat(FxTheme.byName(null)).isEmpty();
    }

    @Test
    public void appliedThemeIsPersistedAndRestored() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-theme", ".properties");
        Files.delete(file);
        final Prefs prefs = new Prefs(file);

        final AtomicReference<FxTheme> restored = new AtomicReference<>();
        final AtomicReference<Boolean> dark = new AtomicReference<>();
        FxTestToolkit.runOnFx(() -> {
            final ThemeManager manager = new ThemeManager(prefs);
            manager.apply(FxTheme.NORD_DARK);

            // A fresh manager over the same store is the next app start.
            final ThemeManager nextSession = new ThemeManager(prefs);
            restored.set(nextSession.applyStored());
            dark.set(nextSession.isDark());
        });

        assertThat(prefs.get(ThemeManager.PREF_KEY, null)).isEqualTo("NORD_DARK");
        assertThat(restored.get()).isEqualTo(FxTheme.NORD_DARK);
        assertThat(dark.get()).isTrue();
        Files.deleteIfExists(file);
    }

    @Test
    public void unknownStoredThemeFallsBackWithoutThrowing() throws IOException, InterruptedException {
        final Path file = Files.createTempFile("druvu-theme-bad", ".properties");
        Files.delete(file);
        final Prefs prefs = new Prefs(file);
        prefs.put(ThemeManager.PREF_KEY, "SOME_THEME_THAT_WAS_REMOVED");

        final AtomicReference<FxTheme> applied = new AtomicReference<>();
        FxTestToolkit.runOnFx(() -> applied.set(new ThemeManager(prefs).applyStored(FxTheme.CUPERTINO_LIGHT)));

        assertThat(applied.get()).isEqualTo(FxTheme.CUPERTINO_LIGHT);
        Files.deleteIfExists(file);
    }

    @Test
    public void currentPropertyNotifiesOnChange() throws InterruptedException {
        final AtomicReference<FxTheme> observed = new AtomicReference<>();
        FxTestToolkit.runOnFx(() -> {
            final ThemeManager manager = new ThemeManager();
            manager.currentProperty().addListener((_, _, value) -> observed.set(value));
            manager.apply(FxTheme.PRIMER_DARK);
            assertThat(manager.stored()).isEmpty(); // non-persisting manager
        });
        assertThat(observed.get()).isEqualTo(FxTheme.PRIMER_DARK);
    }

    @AfterClass
    public void resetTheme() throws InterruptedException {
        FxTestToolkit.resetUserAgentStylesheet();
    }

    /** AtlantaFX hands back a classpath-relative path; strip the leading slash for ClassLoader lookup. */
    private static String stripJarPrefix(String stylesheet) {
        return stylesheet.startsWith("/") ? stylesheet.substring(1) : stylesheet;
    }
}
