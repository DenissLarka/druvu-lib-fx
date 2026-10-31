package com.druvu.lib.fx.updater;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.util.List;
import org.testng.annotations.Test;

/** Picking the asset for an operating system by its file-name ending, and the value rules of assets. */
public class ReleaseTest {

    private static final Asset MSIX =
            new Asset("Example.msix", URI.create("https://x/Example.msix"), 10, "AB".repeat(32));
    private static final Asset DMG = new Asset("Example.dmg", URI.create("https://x/Example.dmg"), 10, "cd".repeat(32));

    @Test
    public void findsTheAssetByItsEndingWhateverTheCase() {
        Release release = new Release(Version.parse("1.2.0"), URI.create("https://x"), "", List.of(MSIX, DMG));

        assertThat(release.asset(".MSIX")).contains(MSIX);
        assertThat(release.asset(".dmg")).contains(DMG);
        assertThat(release.asset(".AppImage")).isEmpty();
    }

    @Test
    public void anAssetKeepsItsDigestInLowerCaseHex() {
        assertThat(MSIX.sha256()).isEqualTo("ab".repeat(32));
        assertThat(MSIX.verifiable()).isTrue();
        assertThat(new Asset("x", URI.create("https://x"), -1, "").verifiable()).isFalse();
    }

    @Test
    public void progressKnowsItsShare() {
        assertThat(new Progress(50, 100).fraction()).isEqualTo(0.5);
        assertThat(new Progress(150, 100).fraction()).isEqualTo(1.0);
        assertThat(new Progress(50, -1).fraction()).isEqualTo(-1.0);
    }

    @Test
    public void theRunningVersionComesFromTheModuleDescriptor() {
        assertThat(AppVersion.of(String.class)).isPresent();
        assertThat(AppVersion.of(ReleaseTest.class))
                .as("tests run on the class path")
                .isEmpty();
    }
}
