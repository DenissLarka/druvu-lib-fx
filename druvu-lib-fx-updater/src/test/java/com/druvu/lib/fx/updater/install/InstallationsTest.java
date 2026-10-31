package com.druvu.lib.fx.updater.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.testng.annotations.Test;

/** Recognising the three installed layouts, and the development runs that are none of them. */
public class InstallationsTest {

    @Test
    public void theAppImageRuntimeNamesTheRunningFile() {
        Optional<Installation> installation = Installations.detect(new Environment(
                "Linux",
                Map.of("APPIMAGE", "/home/deniss/Downloads/Letterblade.AppImage"),
                Path.of("/tmp/.mount_x/runtime"),
                7));

        assertThat(installation).get().isInstanceOf(AppImageInstallation.class);
        assertThat(installation.orElseThrow().assetSuffix()).isEqualTo(".AppImage");
        assertThat(installation.orElseThrow().location())
                .isEqualTo(Path.of("/home/deniss/Downloads/Letterblade.AppImage"));
    }

    @Test
    public void aMacBundleIsTheAppFolderAroundTheRuntime() {
        Optional<Installation> installation = Installations.detect(
                new Environment("Mac OS X", Map.of(), Path.of("/Applications/Letterblade.app/Contents/runtime"), 7));

        assertThat(installation).get().isInstanceOf(MacInstallation.class);
        assertThat(installation.orElseThrow().assetSuffix()).isEqualTo(".dmg");
        assertThat(installation.orElseThrow().location()).isEqualTo(Path.of("/Applications/Letterblade.app"));
    }

    @Test
    public void aJdkBundleOnMacIsADevelopmentRun() {
        Optional<Installation> installation = Installations.detect(new Environment(
                "Mac OS X",
                Map.of(),
                Path.of("/Users/x/.sdkman/candidates/java/27-zulu/zulu-27.jdk/Contents/Home"),
                7));

        assertThat(installation).isEmpty();
    }

    @Test
    public void aWindowsPackageLivesUnderWindowsApps() {
        Optional<Installation> installation = Installations.detect(new Environment(
                "Windows 11",
                Map.of(),
                Path.of("C:\\Program Files\\WindowsApps\\com.druvu.letterblade_1.1.1.0_x64__abc\\runtime"),
                7));

        assertThat(installation).get().isInstanceOf(MsixInstallation.class);
        assertThat(installation.orElseThrow().assetSuffix()).isEqualTo(".msix");
    }

    @Test
    public void aStagedOrDevelopmentRuntimeOnWindowsIsNotInstalled() {
        assertThat(Installations.detect(new Environment(
                        "Windows 11",
                        Map.of(),
                        Path.of("D:\\data\\JAVA\\letterblade-dist\\target\\dest\\msix-staging\\runtime"),
                        7)))
                .isEmpty();
        assertThat(Installations.detect(
                        new Environment("Windows 11", Map.of(), Path.of("D:\\scoop\\apps\\zulufx-jdk\\current"), 7)))
                .isEmpty();
    }

    @Test
    public void aPlainLinuxJdkIsADevelopmentRun() {
        assertThat(Installations.detect(
                        new Environment("Linux", Map.of("APPIMAGE", " "), Path.of("/usr/lib/jvm/zulu27"), 7)))
                .isEmpty();
    }
}
