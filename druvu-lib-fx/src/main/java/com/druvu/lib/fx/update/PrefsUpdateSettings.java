package com.druvu.lib.fx.update;

import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.updater.UpdateSettings;
import com.druvu.lib.fx.util.FxThreads;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * The user's update choices in the app's preferences: {@code update.check} (on by default) and {@code update.skip} (the
 * one version never to offer again).
 *
 * <p>{@link #checkAtStartProperty()} is a JavaFX property an app binds a check box to; every change is written through.
 * The updater reads these from its own thread; writes go through the JavaFX thread, where {@link Prefs} lives.
 */
final class PrefsUpdateSettings implements UpdateSettings {

    static final String CHECK_KEY = "update.check";
    static final String SKIP_KEY = "update.skip";

    private final Prefs prefs;
    private final BooleanProperty checkAtStart;

    PrefsUpdateSettings(Prefs prefs) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
        this.checkAtStart = new SimpleBooleanProperty(this, "checkAtStart", prefs.getBoolean(CHECK_KEY, true));
        this.checkAtStart.addListener((property, was, now) -> prefs.putBoolean(CHECK_KEY, now));
    }

    BooleanProperty checkAtStartProperty() {
        return checkAtStart;
    }

    @Override
    public boolean checksAtStart() {
        return checkAtStart.get();
    }

    @Override
    public boolean isSkipped(Version version) {
        return version.toString().equals(prefs.get(SKIP_KEY, ""));
    }

    @Override
    public void skip(Version version) {
        FxThreads.onFx(() -> prefs.put(SKIP_KEY, version.toString()));
    }
}
