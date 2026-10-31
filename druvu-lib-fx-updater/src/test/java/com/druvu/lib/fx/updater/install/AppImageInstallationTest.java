package com.druvu.lib.fx.updater.install;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.updater.Installed;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** The AppImage swap on real files: the running file ends up with the new bytes, executable, nothing staged left. */
public class AppImageInstallationTest {

    @Test
    public void replacesTheRunningFileInPlaceAndMakesItExecutable() throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("needs POSIX permissions");
        }
        Path folder = Files.createTempDirectory("appimage-test");
        Path running = Files.writeString(folder.resolve("Letterblade.AppImage"), "old version");
        Path download = Files.writeString(folder.resolve("druvu-update-Letterblade.AppImage"), "new version");

        Installed installed = new AppImageInstallation(running, 7).apply(download, Version.parse("1.2.0"));

        assertThat(installed).isEqualTo(new Installed.Replaced(Version.parse("1.2.0")));
        assertThat(running).hasContent("new version");
        assertThat(Files.isExecutable(running)).isTrue();
        assertThat(folder.resolve("Letterblade.AppImage.new")).doesNotExist();
        assertThat(download).as("the updater deletes it at the next start").exists();
    }
}
