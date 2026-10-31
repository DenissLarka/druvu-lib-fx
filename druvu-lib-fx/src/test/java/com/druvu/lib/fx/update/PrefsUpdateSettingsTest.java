package com.druvu.lib.fx.update;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.prefs.Prefs;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * The user's update choices in the preferences file: the default, the write-through of the switch, the skipped version.
 */
public class PrefsUpdateSettingsTest {

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void checksAtStartByDefaultAndWritesTheSwitchThrough() throws IOException {
        Path file = Files.createTempDirectory("update-settings").resolve("preferences.properties");
        PrefsUpdateSettings settings = new PrefsUpdateSettings(new Prefs(file));

        assertThat(settings.checksAtStart()).isTrue();
        settings.checkAtStartProperty().set(false);

        assertThat(settings.checksAtStart()).isFalse();
        assertThat(new Prefs(file).getBoolean(PrefsUpdateSettings.CHECK_KEY, true))
                .as("saved")
                .isFalse();
        assertThat(new PrefsUpdateSettings(new Prefs(file)).checksAtStart()).isFalse();
    }

    @Test
    public void remembersOneSkippedVersion() throws Exception {
        Path file = Files.createTempDirectory("update-settings").resolve("preferences.properties");
        PrefsUpdateSettings settings = new PrefsUpdateSettings(new Prefs(file));
        Version skipped = Version.parse("1.2.0");

        assertThat(settings.isSkipped(skipped)).isFalse();
        FxTestToolkit.runOnFx(() -> settings.skip(skipped));

        assertThat(settings.isSkipped(skipped)).isTrue();
        assertThat(settings.isSkipped(Version.parse("1.3.0"))).isFalse();
        assertThat(new Prefs(file).get(PrefsUpdateSettings.SKIP_KEY, "")).isEqualTo("1.2.0");
    }
}
