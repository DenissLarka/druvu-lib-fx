package com.druvu.lib.fx.updater.fixtures;

import com.druvu.lib.fx.updater.UpdateSettings;
import java.lang.module.ModuleDescriptor.Version;
import java.util.HashSet;
import java.util.Set;

/** Settings that live for one test. */
public final class MemorySettings implements UpdateSettings {

    private boolean checksAtStart = true;
    private final Set<Version> skipped = new HashSet<>();

    public static MemorySettings defaults() {
        return new MemorySettings();
    }

    public MemorySettings withChecksAtStart(boolean on) {
        this.checksAtStart = on;
        return this;
    }

    public MemorySettings skipping(String version) {
        skipped.add(Version.parse(version));
        return this;
    }

    @Override
    public boolean checksAtStart() {
        return checksAtStart;
    }

    @Override
    public boolean isSkipped(Version version) {
        return skipped.contains(version);
    }

    @Override
    public void skip(Version version) {
        skipped.add(version);
    }

    public Set<Version> skipped() {
        return Set.copyOf(skipped);
    }
}
