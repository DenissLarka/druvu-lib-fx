package com.druvu.lib.fx.theme;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import java.net.URL;
import java.util.Optional;

/**
 * The themes the toolkit offers: the druvu brand themes (compiled at build time from <a
 * href="https://github.com/mkpaz/atlantafx">AtlantaFX</a>'s SCSS sources with the druvu palette) plus the stock
 * AtlantaFX collection.
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
    // The druvu brand themes lead the menu. They are compiled at build time from AtlantaFX's own
    // SCSS sources with the palette-v1 ramps (src/main/scss; validated spec in the druvu records)
    // and ship inside this jar, so they follow the exact same variable system as the stock themes.
    DRUVU_LIGHT("Druvu Light", "druvu-light.css", false),
    DRUVU_DARK("Druvu Dark", "druvu-dark.css", true),
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

    FxTheme(String displayName, String kitResource, boolean dark) {
        this.displayName = displayName;
        this.stylesheet = kitStylesheet(kitResource);
        this.dark = dark;
    }

    /**
     * Resolves a theme stylesheet that ships in this jar (next to this class). Failing at class-load is deliberate: a
     * broken theme build should stop the first code that touches the enum, not let the app render Modena silently.
     */
    private static String kitStylesheet(String resource) {
        final URL url = FxTheme.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Kit theme stylesheet missing from the jar: " + resource);
        }
        return url.toExternalForm();
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
