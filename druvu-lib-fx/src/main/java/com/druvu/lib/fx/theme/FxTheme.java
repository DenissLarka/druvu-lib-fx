package com.druvu.lib.fx.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import java.util.Optional;

/**
 * The themes the toolkit offers, wrapping the <a href="https://github.com/mkpaz/atlantafx">AtlantaFX</a> collection.
 *
 * <p>This enum is a menu, not an abstraction: it exists so an app can list, persist and restore a theme by a stable
 * name without inventing its own mapping. Anything richer - style classes, control tweaks - comes straight from
 * AtlantaFX, which {@code com.druvu.lib.fx} re-exports (so {@code atlantafx.base.theme.Styles} is on an app's compile
 * path without declaring the dependency again).
 *
 * <p>{@link #isDark()} is the signal toolkit widgets use to pick their own colours; it is not derived from the
 * stylesheet, it is stated per theme.
 */
public enum FxTheme {
    PRIMER_LIGHT(new PrimerLight()),
    PRIMER_DARK(new PrimerDark()),
    NORD_LIGHT(new NordLight()),
    NORD_DARK(new NordDark()),
    CUPERTINO_LIGHT(new CupertinoLight()),
    CUPERTINO_DARK(new CupertinoDark()),
    DRACULA(new Dracula());

    /** The theme applied when nothing has been chosen or stored. */
    public static final FxTheme DEFAULT = PRIMER_LIGHT;

    private final String displayName;
    private final String stylesheet;
    private final boolean dark;

    FxTheme(Theme theme) {
        // Read once, at class-load: AtlantaFX themes are immutable descriptors that only hand back
        // these three values. Holding them instead of the Theme keeps the enum a value type and keeps
        // an AtlantaFX type out of the toolkit's own signatures - while still taking the name and the
        // dark flag from the source of truth rather than duplicating them here.
        this.displayName = theme.getName();
        // The plain .css, not getUserAgentStylesheetBSS(): a binary stylesheet is compiled against a
        // specific JavaFX version and refuses to load on a mismatch, and this toolkit's JavaFX comes
        // from whichever JDK the app ships.
        this.stylesheet = theme.getUserAgentStylesheet();
        this.dark = theme.isDarkMode();
    }

    /** A human-facing name, suitable for a menu item. */
    public String displayName() {
        return displayName;
    }

    /** The user-agent stylesheet URL, as {@code Application.setUserAgentStylesheet} wants it. */
    public String stylesheet() {
        return stylesheet;
    }

    /** Whether this is a dark theme - the cue for widgets that pick their own colours. */
    public boolean isDark() {
        return dark;
    }

    /**
     * Looks a theme up by {@link #name()}, case-insensitively. Returns empty rather than throwing, because the usual
     * caller is reading a value a human may have hand-edited in a preferences file.
     */
    public static Optional<FxTheme> byName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (FxTheme theme : values()) {
            if (theme.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(theme);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
