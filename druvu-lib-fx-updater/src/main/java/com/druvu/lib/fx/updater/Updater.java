package com.druvu.lib.fx.updater;

import com.druvu.lib.fx.updater.UpdateFailure.Step;
import com.druvu.lib.fx.updater.install.Installation;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * The update conversation: asks the source for the newest release, offers it through the host, downloads and verifies
 * the package, applies it through the installation and arranges the restart.
 *
 * <p>Built once per application run. A check runs on the caller's thread, which must not be the UI thread: the host
 * blocks it while the user answers. Two entries: {@link #checkAtStart()} for the automatic check, quiet unless it has
 * something to offer and off when the user said so; {@link #checkNow()} for the user's own "Check for updates", which
 * reports everything.
 *
 * <pre>{@code
 * Updater updater = Updater.builder()
 *         .source(GitHubReleases.of("DenissLarka", "letterblade"))
 *         .currentVersion(AppVersion.of(LetterbladeApp.class).orElseThrow())
 *         .installation(Installation.detect().orElseThrow())
 *         .settings(settings)
 *         .host(host)
 *         .build();
 * Outcome outcome = updater.checkAtStart();
 * }</pre>
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public final class Updater {

    private static final System.Logger LOG = System.getLogger(Updater.class.getName());

    private final UpdateSource source;
    private final Version current;
    private final Installation installation;
    private final UpdateSettings settings;
    private final UpdateHost host;
    private final Downloader downloader;

    private Updater(Builder builder) {
        this.source = builder.source;
        this.current = builder.current;
        this.installation = builder.installation;
        this.settings = builder.settings;
        this.host = builder.host;
        this.downloader = new Downloader(builder.downloadDirectory);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The automatic check at application start. Honours {@link UpdateSettings#checksAtStart()} and a skipped version;
     * stays quiet unless a newer release is there to offer. Always deletes the downloads of earlier runs first.
     *
     * @return how it ended
     */
    public Outcome checkAtStart() {
        return check(false);
    }

    /**
     * The user's own check. Offers a skipped version again and reports "up to date" and every failure.
     *
     * @return how it ended
     */
    public Outcome checkNow() {
        return check(true);
    }

    private Outcome check(boolean userAsked) {
        downloader.deleteOldDownloads();
        if (!userAsked && !settings.checksAtStart()) {
            return new Outcome.Disabled();
        }
        Release release;
        try {
            release = source.latest();
        } catch (IOException e) {
            return failed(Step.CHECK, "Cannot check for updates: " + e.getMessage(), e, userAsked);
        }
        if (release.version().compareTo(current) <= 0) {
            LOG.log(System.Logger.Level.DEBUG, "Up to date: {0} is the newest release", current);
            if (userAsked) {
                host.upToDate(current);
            }
            return new Outcome.UpToDate(current);
        }
        if (!userAsked && settings.isSkipped(release.version())) {
            LOG.log(System.Logger.Level.DEBUG, "Version {0} is available and skipped by the user", release.version());
            return new Outcome.Skipped(release.version());
        }
        Optional<Asset> asset = release.asset(installation.assetSuffix());
        if (asset.isEmpty()) {
            return failed(
                    Step.CHECK,
                    "Version " + release.version() + " has no " + installation.assetSuffix() + " package",
                    null,
                    userAsked);
        }
        if (!asset.get().verifiable()) {
            return failed(
                    Step.CHECK,
                    "Version " + release.version() + " publishes no SHA-256 for "
                            + asset.get().name() + ", so it cannot be verified after download",
                    null,
                    userAsked);
        }
        Update update = new Update(release.version(), release.notes(), release.page(), asset.get());
        LOG.log(System.Logger.Level.INFO, "Update available: {0} -> {1}", current, update.version());
        Decision decision = host.offer(update);
        if (decision != Decision.INSTALL) {
            if (decision == Decision.SKIP_VERSION) {
                settings.skip(update.version());
            }
            return new Outcome.Declined(update.version(), decision);
        }
        return install(update);
    }

    /** The user said yes: from here on every failure is reported to the host. */
    private Outcome install(Update update) {
        Path download;
        try {
            download = downloader.fetch(update.asset(), host::progress);
        } catch (Downloader.VerificationException e) {
            return failed(Step.VERIFY, e.getMessage(), e, true);
        } catch (IOException e) {
            return failed(
                    Step.DOWNLOAD, "Cannot download version " + update.version() + ": " + e.getMessage(), e, true);
        }
        Installed installed;
        try {
            installed = installation.apply(download, update.version());
        } catch (IOException e) {
            return failed(Step.APPLY, "Cannot install version " + update.version() + ": " + e.getMessage(), e, true);
        }
        LOG.log(System.Logger.Level.INFO, "Version {0} applied to {1}: {2}", update.version(), installation, installed);
        RestartChoice choice = host.installed(installed);
        if (choice == RestartChoice.RESTART_NOW && installed.restartable()) {
            try {
                installation.relaunch();
            } catch (IOException e) {
                return failed(
                        Step.RELAUNCH, "Cannot start version " + update.version() + ": " + e.getMessage(), e, true);
            }
            return new Outcome.Applied(installed, RestartChoice.RESTART_NOW);
        }
        return new Outcome.Applied(installed, RestartChoice.LATER);
    }

    private Outcome failed(Step step, String message, Throwable cause, boolean tellTheUser) {
        LOG.log(System.Logger.Level.WARNING, "Update stopped at " + step + ": " + message, cause);
        UpdateFailure failure = new UpdateFailure(step, message);
        if (tellTheUser) {
            host.failed(failure);
        }
        return new Outcome.Failed(failure);
    }

    /** Collects the five parts of an updater; {@link #build()} names what is missing. */
    public static final class Builder {
        private UpdateSource source;
        private Version current;
        private Installation installation;
        private UpdateSettings settings;
        private UpdateHost host;
        private Path downloadDirectory = Path.of(System.getProperty("java.io.tmpdir"));

        private Builder() {}

        /** @param source where releases are published */
        public Builder source(UpdateSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        /** @param current the running version, see {@link AppVersion} */
        public Builder currentVersion(Version current) {
            this.current = Objects.requireNonNull(current, "current");
            return this;
        }

        /** @param installation the installed copy, see {@link Installation#detect()} */
        public Builder installation(Installation installation) {
            this.installation = Objects.requireNonNull(installation, "installation");
            return this;
        }

        /** @param settings the user's lasting choices */
        public Builder settings(UpdateSettings settings) {
            this.settings = Objects.requireNonNull(settings, "settings");
            return this;
        }

        /** @param host the application's side of the conversation */
        public Builder host(UpdateHost host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        /** @param directory where packages are downloaded; the system's temporary folder by default */
        public Builder downloadDirectory(Path directory) {
            this.downloadDirectory = Objects.requireNonNull(directory, "directory");
            return this;
        }

        /**
         * @return the updater
         * @throws IllegalStateException naming the first missing part
         */
        public Updater build() {
            require(source, "source");
            require(current, "currentVersion");
            require(installation, "installation");
            require(settings, "settings");
            require(host, "host");
            return new Updater(this);
        }

        private static void require(Object part, String name) {
            if (part == null) {
                throw new IllegalStateException("Updater.builder() needs " + name + "(...)");
            }
        }
    }
}
