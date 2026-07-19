package com.druvu.lib.fx.prefs;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import org.testng.annotations.Test;

public class AppHomeTest {

    @Test
    public void defaultsToDruvuVendorDir() {
        assertThat(AppHome.computeRoot(null, "/home/alice", "market-watch"))
                .isEqualTo(Paths.get("/home/alice", ".druvu.com", "market-watch"));
    }

    @Test
    public void blankEnvValueIsIgnored() {
        assertThat(AppHome.computeRoot("  ", "/home/alice", "app"))
                .isEqualTo(Paths.get("/home/alice", ".druvu.com", "app"));
    }

    @Test
    public void envOverrideWins() {
        assertThat(AppHome.computeRoot("/custom/dir", "/home/alice", "app")).isEqualTo(Paths.get("/custom/dir"));
    }
}
