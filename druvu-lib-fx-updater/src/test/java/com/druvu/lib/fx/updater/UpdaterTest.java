package com.druvu.lib.fx.updater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.druvu.lib.fx.updater.UpdateFailure.Step;
import com.druvu.lib.fx.updater.fixtures.Assets;
import com.druvu.lib.fx.updater.fixtures.CannedSource;
import com.druvu.lib.fx.updater.fixtures.FakeInstallation;
import com.druvu.lib.fx.updater.fixtures.MemorySettings;
import com.druvu.lib.fx.updater.fixtures.ScriptedHost;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The update conversation end to end over fakes: the automatic and the user-triggered check, the user's three answers,
 * a verified install, a tampered download, an unreachable source, a package without a digest, the restart.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public class UpdaterTest {

    private static final Version CURRENT = Version.parse("1.1.1");
    private static final String SUFFIX = ".AppImage";

    private Path folder;
    private Path downloads;
    private Path newPackage;

    @BeforeMethod
    public void aPackageAndAnEmptyDownloadFolder() throws IOException {
        folder = Files.createTempDirectory("updater-test");
        downloads = Files.createDirectories(folder.resolve("downloads"));
        newPackage = Assets.aPackage(folder, "Example" + SUFFIX, "the bytes of version 1.2.0");
    }

    @AfterMethod
    public void deleteTheFolder() throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    public void anAutomaticCheckStaysHomeWhenTheUserSwitchedItOff() {
        CannedSource source = CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage)));
        Updater updater = anUpdater(source, MemorySettings.defaults().withChecksAtStart(false), install());

        assertThat(updater.checkAtStart()).isEqualTo(new Outcome.Disabled());
        assertThat(source.calls()).isZero();
    }

    @Test
    public void aUserCheckRunsEvenWhenAutomaticChecksAreOff() {
        ScriptedHost host = ScriptedHost.answering(Decision.NOT_NOW);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults().withChecksAtStart(false),
                host);

        assertThat(updater.checkNow()).isEqualTo(new Outcome.Declined(Version.parse("1.2.0"), Decision.NOT_NOW));
        assertThat(host.offers()).hasSize(1);
    }

    @Test
    public void nothingNewerIsSaidOnlyWhenTheUserAsked() {
        ScriptedHost host = install();
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.1.1", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults(),
                host);

        assertThat(updater.checkAtStart()).isEqualTo(new Outcome.UpToDate(CURRENT));
        assertThat(host.upToDates()).isEmpty();

        assertThat(updater.checkNow()).isEqualTo(new Outcome.UpToDate(CURRENT));
        assertThat(host.upToDates()).containsExactly(CURRENT);
        assertThat(host.offers()).isEmpty();
    }

    @Test
    public void aSkippedVersionStaysQuietAutomaticallyAndIsOfferedWhenTheUserAsks() {
        ScriptedHost host = ScriptedHost.answering(Decision.NOT_NOW);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults().skipping("1.2.0"),
                host);

        assertThat(updater.checkAtStart()).isEqualTo(new Outcome.Skipped(Version.parse("1.2.0")));
        assertThat(host.offers()).isEmpty();

        updater.checkNow();
        assertThat(host.offers()).hasSize(1);
    }

    @Test
    public void notNowDeclinesWithoutRememberingAnything() {
        MemorySettings settings = MemorySettings.defaults();
        FakeInstallation installation = FakeInstallation.replacing(SUFFIX);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                settings,
                ScriptedHost.answering(Decision.NOT_NOW),
                installation);

        assertThat(updater.checkAtStart()).isEqualTo(new Outcome.Declined(Version.parse("1.2.0"), Decision.NOT_NOW));
        assertThat(settings.skipped()).isEmpty();
        assertThat(installation.applied()).isEmpty();
    }

    @Test
    public void skipVersionIsRemembered() {
        MemorySettings settings = MemorySettings.defaults();
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                settings,
                ScriptedHost.answering(Decision.SKIP_VERSION));

        assertThat(updater.checkAtStart())
                .isEqualTo(new Outcome.Declined(Version.parse("1.2.0"), Decision.SKIP_VERSION));
        assertThat(settings.skipped()).containsExactly(Version.parse("1.2.0"));
    }

    @Test
    public void installDownloadsVerifiesAppliesAndAsksAboutTheRestart() throws IOException {
        ScriptedHost host = ScriptedHost.answering(Decision.INSTALL, RestartChoice.LATER);
        FakeInstallation installation = FakeInstallation.replacing(SUFFIX);
        Asset asset = Assets.anAssetOf(newPackage);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", asset)), MemorySettings.defaults(), host, installation);

        Outcome outcome = updater.checkAtStart();

        assertThat(outcome)
                .isEqualTo(new Outcome.Applied(new Installed.Replaced(Version.parse("1.2.0")), RestartChoice.LATER));
        assertThat(host.offers()).singleElement().satisfies(update -> {
            assertThat(update.version()).isEqualTo(Version.parse("1.2.0"));
            assertThat(update.notes()).isEqualTo("Notes of 1.2.0");
            assertThat(update.asset()).isEqualTo(asset);
        });
        Path download = installation.applied().orElseThrow();
        assertThat(download).hasParent(downloads).hasSameBinaryContentAs(newPackage);
        assertThat(host.progresses()).isNotEmpty();
        assertThat(host.progresses().getLast()).isEqualTo(new Progress(Files.size(newPackage), Files.size(newPackage)));
        assertThat(host.installs()).containsExactly(new Installed.Replaced(Version.parse("1.2.0")));
        assertThat(installation.relaunches()).isZero();
    }

    @Test
    public void aTamperedDownloadIsDeletedAndReportedEvenAutomatically() throws IOException {
        ScriptedHost host = ScriptedHost.answering(Decision.INSTALL);
        FakeInstallation installation = FakeInstallation.replacing(SUFFIX);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.aTamperedAssetOf(newPackage))),
                MemorySettings.defaults(),
                host,
                installation);

        Outcome outcome = updater.checkAtStart();

        assertThat(outcome).isInstanceOf(Outcome.Failed.class);
        UpdateFailure failure = ((Outcome.Failed) outcome).failure();
        assertThat(failure.step()).isEqualTo(Step.VERIFY);
        assertThat(failure.message()).contains("SHA-256").contains("deleted");
        assertThat(host.failures()).containsExactly(failure);
        assertThat(installation.applied()).isEmpty();
        try (Stream<Path> left = Files.list(downloads)) {
            assertThat(left).isEmpty();
        }
    }

    @Test
    public void anUnreachableSourceIsSilentAutomaticallyAndReportedWhenTheUserAsks() {
        ScriptedHost host = install();
        Updater updater = anUpdater(CannedSource.unreachable("no route to host"), MemorySettings.defaults(), host);

        Outcome automatic = updater.checkAtStart();
        assertThat(automatic)
                .isEqualTo(new Outcome.Failed(
                        new UpdateFailure(Step.CHECK, "Cannot check for updates: no route to host")));
        assertThat(host.failures()).isEmpty();

        updater.checkNow();
        assertThat(host.failures()).hasSize(1);
    }

    @Test
    public void aReleaseWithoutAPackageForThisInstallationFails() {
        ScriptedHost host = install();
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults(),
                host,
                FakeInstallation.replacing(".msix"));

        Outcome outcome = updater.checkNow();

        assertThat(outcome)
                .isEqualTo(new Outcome.Failed(new UpdateFailure(Step.CHECK, "Version 1.2.0 has no .msix package")));
        assertThat(host.offers()).isEmpty();
        assertThat(host.failures()).hasSize(1);
    }

    @Test
    public void aPackageWithoutADigestIsNeverOffered() {
        ScriptedHost host = install();
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anUnverifiableAssetOf(newPackage))),
                MemorySettings.defaults(),
                host);

        Outcome outcome = updater.checkNow();

        assertThat(outcome).isInstanceOf(Outcome.Failed.class);
        assertThat(((Outcome.Failed) outcome).failure().message()).contains("no SHA-256");
        assertThat(host.offers()).isEmpty();
    }

    @Test
    public void restartNowRelaunchesTheInstallation() {
        FakeInstallation installation = FakeInstallation.replacing(SUFFIX);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults(),
                ScriptedHost.answering(Decision.INSTALL, RestartChoice.RESTART_NOW),
                installation);

        Outcome outcome = updater.checkAtStart();

        assertThat(outcome)
                .isEqualTo(
                        new Outcome.Applied(new Installed.Replaced(Version.parse("1.2.0")), RestartChoice.RESTART_NOW));
        assertThat(installation.relaunches()).isEqualTo(1);
    }

    @Test
    public void manualStepsNeverRelaunch() {
        FakeInstallation installation = FakeInstallation.withManualSteps(SUFFIX);
        ScriptedHost host = ScriptedHost.answering(Decision.INSTALL, RestartChoice.RESTART_NOW);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults(),
                host,
                installation);

        Outcome outcome = updater.checkAtStart();

        assertThat(outcome)
                .isEqualTo(new Outcome.Applied(
                        new Installed.ManualSteps(Version.parse("1.2.0"), "Drag it over"), RestartChoice.LATER));
        assertThat(host.installs())
                .singleElement()
                .satisfies(installed -> assertThat(installed.restartable()).isFalse());
        assertThat(installation.relaunches()).isZero();
    }

    @Test
    public void aFailedApplyIsAlwaysReported() {
        ScriptedHost host = ScriptedHost.answering(Decision.INSTALL);
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults(),
                host,
                FakeInstallation.replacing(SUFFIX).failingToApplyWith("hdiutil said no"));

        Outcome outcome = updater.checkAtStart();

        assertThat(outcome)
                .isEqualTo(new Outcome.Failed(
                        new UpdateFailure(Step.APPLY, "Cannot install version 1.2.0: hdiutil said no")));
        assertThat(host.failures()).hasSize(1);
    }

    @Test
    public void aFailedRelaunchIsReported() {
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.2.0", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults(),
                ScriptedHost.answering(Decision.INSTALL, RestartChoice.RESTART_NOW),
                FakeInstallation.replacing(SUFFIX).failingToRelaunchWith("sh not found"));

        Outcome outcome = updater.checkAtStart();

        assertThat(outcome)
                .isEqualTo(new Outcome.Failed(
                        new UpdateFailure(Step.RELAUNCH, "Cannot start version 1.2.0: sh not found")));
    }

    @Test
    public void oldDownloadsAreDeletedAtEveryCheckAndNothingElse() throws IOException {
        Path old = Files.writeString(downloads.resolve(Downloader.FILE_PREFIX + "Example.msix"), "stale");
        Path unrelated = Files.writeString(downloads.resolve("keep.txt"), "mine");
        Updater updater = anUpdater(
                CannedSource.offering(Assets.aRelease("1.1.1", Assets.anAssetOf(newPackage))),
                MemorySettings.defaults().withChecksAtStart(false),
                install());

        updater.checkAtStart();

        assertThat(old).doesNotExist();
        assertThat(unrelated).exists();
    }

    @Test
    public void theBuilderNamesTheMissingPart() {
        assertThatThrownBy(() ->
                        Updater.builder().source(CannedSource.unreachable("x")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currentVersion");
    }

    private Updater anUpdater(UpdateSource source, UpdateSettings settings, UpdateHost host) {
        return anUpdater(source, settings, host, FakeInstallation.replacing(SUFFIX));
    }

    private Updater anUpdater(
            UpdateSource source, UpdateSettings settings, UpdateHost host, FakeInstallation installation) {
        return Updater.builder()
                .source(source)
                .currentVersion(CURRENT)
                .installation(installation)
                .settings(settings)
                .host(host)
                .downloadDirectory(downloads)
                .build();
    }

    private static ScriptedHost install() {
        return ScriptedHost.answering(Decision.INSTALL);
    }
}
