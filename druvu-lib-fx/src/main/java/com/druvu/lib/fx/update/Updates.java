package com.druvu.lib.fx.update;

import com.druvu.lib.fx.exec.FxExec;
import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.updater.AppVersion;
import com.druvu.lib.fx.updater.Outcome;
import com.druvu.lib.fx.updater.RestartChoice;
import com.druvu.lib.fx.updater.UpdateSource;
import com.druvu.lib.fx.updater.Updater;
import com.druvu.lib.fx.updater.install.Installation;
import com.druvu.lib.fx.util.FxThreads;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

/**
 * Self-update for an app on the kit, in one object: build it once the stage is showing, call {@link #checkAtStart()},
 * put {@link #checkForUpdatesMenuItem()} in the Help menu. The conversation - the offer, the download in the status
 * bar, the restart - runs on the app's {@link FxExec} with dialogs on the owner window.
 *
 * <p>An installed copy is recognised by {@link Installation#detect()}; a development run is none, so
 * {@link #available()} is false and every check is a no-op, the menu item says so. The system property
 * {@value #OFF_PROPERTY}, or the environment variable {@value #OFF_VARIABLE} for an installed app started from a shell,
 * forces the same for a packaged test run.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public final class Updates {

    /** System property: {@code true} switches updates off for this run, whatever the installation. */
    public static final String OFF_PROPERTY = "druvu.updater.off";
    /** Environment variable with the same meaning, for an installed app started from a shell. */
    public static final String OFF_VARIABLE = "DRUVU_UPDATER_OFF";

    private static final System.Logger LOG = System.getLogger(Updates.class.getName());
    private static final String MENU_ITEM = "Check for Updates...";

    private final String appName;
    private final Window owner;
    private final FxExec exec;
    private final PrefsUpdateSettings settings;
    private final FxUpdateHost host;
    private final Optional<Updater> updater;
    private final Runnable onRestart;
    private final AtomicBoolean running = new AtomicBoolean();

    private Updates(Builder builder) {
        this.appName = builder.appName;
        this.owner = builder.owner;
        this.exec = builder.exec;
        this.settings = new PrefsUpdateSettings(builder.prefs);
        this.host = new FxUpdateHost(builder.owner, builder.appName, settings);
        this.onRestart = builder.onRestart;
        this.updater = builder.updater(settings, host);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @return whether this run can update itself: an installed copy with a known version, and not switched off */
    public boolean available() {
        return updater.isPresent();
    }

    /**
     * The automatic check, quiet unless it has an update to offer; nothing happens when {@link #available()} is false.
     */
    public void checkAtStart() {
        updater.ifPresent(present -> run(present::checkAtStart));
    }

    /** The user's own check: reports "up to date" and failures too; a dialog says so when updates are unavailable. */
    public void checkNow() {
        if (updater.isEmpty()) {
            FxThreads.onFx(() -> UpdateDialogs.unavailable(owner, appName));
            return;
        }
        run(updater.get()::checkNow);
    }

    /** @return a "Check for Updates..." item wired to {@link #checkNow()}, for the Help menu */
    public MenuItem checkForUpdatesMenuItem() {
        MenuItem item = new MenuItem(MENU_ITEM);
        item.setOnAction(event -> checkNow());
        return item;
    }

    /** @return the user's "check at start" switch, written through to the preferences; bind a check box to it */
    public BooleanProperty checkAtStartProperty() {
        return settings.checkAtStartProperty();
    }

    private void run(Supplier<Outcome> check) {
        if (!running.compareAndSet(false, true)) {
            LOG.log(System.Logger.Level.DEBUG, "An update check is already running");
            return;
        }
        exec.run("Checking for updates", progress -> {
            try {
                host.reportTo(progress);
                Outcome outcome = check.get();
                LOG.log(System.Logger.Level.INFO, "Update check: {0}", outcome);
                if (outcome instanceof Outcome.Applied applied && applied.choice() == RestartChoice.RESTART_NOW) {
                    FxThreads.onFx(onRestart);
                }
            } finally {
                running.set(false);
            }
        });
    }

    /** Collects what the conversation needs; {@link #build()} names what is missing. */
    public static final class Builder {
        private String appName;
        private Class<?> app;
        private UpdateSource source;
        private Window owner;
        private Prefs prefs;
        private FxExec exec;
        private Installation installation;
        private Version currentVersion;
        private Runnable onRestart = Platform::exit;

        private Builder() {}

        /** @param appName the name the dialogs use, {@code Letterblade} */
        public Builder appName(String appName) {
            this.appName = Objects.requireNonNull(appName, "appName");
            return this;
        }

        /** @param app a class of the app's module; its module version is the running version */
        public Builder app(Class<?> app) {
            this.app = Objects.requireNonNull(app, "app");
            return this;
        }

        /** @param source where releases are published, {@code GitHubReleases.of(owner, repo)} */
        public Builder source(UpdateSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        /** @param owner the window the dialogs belong to, the primary stage */
        public Builder owner(Window owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
            return this;
        }

        /** @param prefs the app's preferences, where the user's choices are kept */
        public Builder prefs(Prefs prefs) {
            this.prefs = Objects.requireNonNull(prefs, "prefs");
            return this;
        }

        /** @param exec the app's executor; the check runs as one of its tasks, so the status bar shows it */
        public Builder exec(FxExec exec) {
            this.exec = Objects.requireNonNull(exec, "exec");
            return this;
        }

        /** @param installation the installed copy, instead of {@link Installation#detect()}; a demo or a test */
        public Builder installation(Installation installation) {
            this.installation = Objects.requireNonNull(installation, "installation");
            return this;
        }

        /** @param version the running version, instead of the app module's; a demo or a test */
        public Builder currentVersion(Version version) {
            this.currentVersion = Objects.requireNonNull(version, "version");
            return this;
        }

        /**
         * @param onRestart how the app quits when the user restarts into the new version, run on the JavaFX thread;
         *     {@link Platform#exit()} unless the app has its own exit path
         */
        public Builder onRestart(Runnable onRestart) {
            this.onRestart = Objects.requireNonNull(onRestart, "onRestart");
            return this;
        }

        /**
         * @return the updates, available or not
         * @throws IllegalStateException naming the first missing part
         */
        public Updates build() {
            require(appName, "appName");
            require(app, "app");
            require(source, "source");
            require(owner, "owner");
            require(prefs, "prefs");
            require(exec, "exec");
            return new Updates(this);
        }

        private Optional<Updater> updater(PrefsUpdateSettings settings, FxUpdateHost host) {
            if (Boolean.getBoolean(OFF_PROPERTY) || Boolean.parseBoolean(System.getenv(OFF_VARIABLE))) {
                LOG.log(
                        System.Logger.Level.INFO,
                        "Updates are off for this run ({0} or {1})",
                        OFF_PROPERTY,
                        OFF_VARIABLE);
                return Optional.empty();
            }
            Optional<Installation> installed = Optional.ofNullable(installation).or(Installation::detect);
            if (installed.isEmpty()) {
                LOG.log(System.Logger.Level.INFO, "Not an installed copy: no updates in this run");
                return Optional.empty();
            }
            Optional<Version> version = Optional.ofNullable(currentVersion).or(() -> AppVersion.of(app));
            if (version.isEmpty()) {
                LOG.log(
                        System.Logger.Level.WARNING,
                        "The module of {0} carries no version: no updates in this run",
                        app);
                return Optional.empty();
            }
            LOG.log(
                    System.Logger.Level.INFO,
                    "{0} {1} runs as {2}; updates from {3}",
                    appName,
                    version.get(),
                    installed.get(),
                    source);
            return Optional.of(Updater.builder()
                    .source(source)
                    .currentVersion(version.get())
                    .installation(installed.get())
                    .settings(settings)
                    .host(host)
                    .build());
        }

        private static void require(Object part, String name) {
            if (part == null) {
                throw new IllegalStateException("Updates.builder() needs " + name + "(...)");
            }
        }
    }
}
