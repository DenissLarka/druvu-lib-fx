package com.druvu.lib.fx.updater.fixtures;

import com.druvu.lib.fx.updater.Installed;
import com.druvu.lib.fx.updater.install.Installation;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/** An installed copy that records what the updater asked of it instead of touching the operating system. */
public final class FakeInstallation implements Installation {

    private final String suffix;
    private final Function<Version, Installed> result;
    private String applyFailure;
    private String relaunchFailure;
    private Path applied;
    private int relaunches;

    private FakeInstallation(String suffix, Function<Version, Installed> result) {
        this.suffix = suffix;
        this.result = result;
    }

    /** Behaves like the AppImage: the file is replaced, a restart runs it. */
    public static FakeInstallation replacing(String suffix) {
        return new FakeInstallation(suffix, Installed.Replaced::new);
    }

    /** Behaves like macOS: the user finishes by hand. */
    public static FakeInstallation withManualSteps(String suffix) {
        return new FakeInstallation(suffix, version -> new Installed.ManualSteps(version, "Drag it over"));
    }

    public FakeInstallation failingToApplyWith(String reason) {
        this.applyFailure = reason;
        return this;
    }

    public FakeInstallation failingToRelaunchWith(String reason) {
        this.relaunchFailure = reason;
        return this;
    }

    @Override
    public String assetSuffix() {
        return suffix;
    }

    @Override
    public Path location() {
        return Path.of("fake");
    }

    @Override
    public Installed apply(Path download, Version version) throws IOException {
        if (applyFailure != null) {
            throw new IOException(applyFailure);
        }
        applied = download;
        return result.apply(version);
    }

    @Override
    public void relaunch() throws IOException {
        if (relaunchFailure != null) {
            throw new IOException(relaunchFailure);
        }
        relaunches++;
    }

    /** @return the download handed to {@link #apply}, if any */
    public Optional<Path> applied() {
        return Optional.ofNullable(applied);
    }

    public int relaunches() {
        return relaunches;
    }
}
