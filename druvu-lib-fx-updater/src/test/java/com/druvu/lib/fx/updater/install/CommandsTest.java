package com.druvu.lib.fx.updater.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.Test;

/** The command lines the three installations hand to the operating system, and the quoting that keeps paths literal. */
public class CommandsTest {

    @Test
    public void theMsixPackageIsAddedWithoutClosingTheApp() {
        String command =
                MsixInstallation.addPackageCommand(Path.of("C:\\Users\\O'Brien\\druvu-update-Letterblade.msix"));

        assertThat(command)
                .startsWith("Add-AppxPackage -Path 'C:\\Users\\O''Brien\\druvu-update-Letterblade.msix'")
                .endsWith("-DeferRegistrationWhenPackagesAreInUse");
    }

    @Test
    public void theMsixRelaunchWaitsForThisProcessThenOpensThePackageThroughTheAppsFolder() {
        String command =
                MsixInstallation.relaunchCommand(4242, Path.of("C:\\Program Files\\WindowsApps\\pkg\\runtime"));

        assertThat(command)
                .contains("Get-AppxPackage")
                .contains("'C:\\Program Files\\WindowsApps\\pkg\\runtime'.StartsWith($_.InstallLocation")
                .contains("Wait-Process -Id 4242")
                .contains("Start-Process ('shell:AppsFolder\\' + $package.PackageFamilyName + '!' + $application)");
    }

    /**
     * The sh scripts quote a Unix path; on Windows, Path would rewrite it with backslashes, so these two stay
     * POSIX-only.
     */
    @Test
    public void theAppImageRelaunchRunsTheSamePathOnceThisProcessIsGone() {
        requirePosix();
        assertThat(AppImageInstallation.relaunchScript(42, Path.of("/opt/It's.AppImage")))
                .isEqualTo("while kill -0 42 2>/dev/null; do sleep 0.2; done; exec '/opt/It'\\''s.AppImage'");
    }

    @Test
    public void theMacRelaunchOpensTheBundleAgain() {
        requirePosix();
        assertThat(MacInstallation.relaunchScript(42, Path.of("/Applications/Letterblade.app")))
                .endsWith("open -n '/Applications/Letterblade.app'");
    }

    @Test
    public void theMacInstructionsNameTheBundleAndTheVersion() {
        assertThat(MacInstallation.instructions(Version.parse("1.2.0"), Path.of("/Applications/Letterblade.app")))
                .contains("Version 1.2.0")
                .contains("Drag Letterblade.app to the Applications folder");
    }

    @Test
    public void quotingKeepsEveryCharacterLiteral() {
        assertThat(Shell.singleQuoted("a'b $c")).isEqualTo("'a'\\''b $c'");
        assertThat(Shell.powerShellQuoted("a'b $c")).isEqualTo("'a''b $c'");
    }

    @Test
    public void aCommandsOutputComesBackAndItsFailureCarriesIt() throws IOException {
        requirePosix();

        assertThat(Shell.run(List.of("sh", "-c", "echo hello"))).isEqualTo("hello\n");
        assertThatThrownBy(() -> Shell.run(List.of("sh", "-c", "echo bad >&2; exit 3")))
                .isInstanceOf(IOException.class)
                .hasMessage("sh failed (exit 3): bad");
    }

    private static void requirePosix() {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            throw new SkipException("needs sh and POSIX permissions");
        }
    }
}
