package com.druvu.lib.fx.theme;

import com.druvu.lib.fx.prefs.Prefs;
import com.druvu.lib.fx.util.FxThreads;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a {@link FxTheme} and remembers the choice.
 *
 * <p>Applying a theme is a <em>process-global</em> act: it goes through {@link Application#setUserAgentStylesheet},
 * which restyles every window in the JVM. The toolkit therefore never applies a theme on its own - the app decides
 * when, exactly as it decides where {@code LoginPane} goes next. A typical {@code start(Stage)} does:
 *
 * <pre>{@code
 * themeManager = new ThemeManager(prefs);
 * themeManager.applyStored(); // restores last session's theme, or the default
 * }</pre>
 *
 * <p>{@link #currentProperty()} lets toolkit widgets and app code follow theme changes; {@link FxTheme#isDark()} on the
 * current value is the cue for anything that paints its own colours.
 *
 * <p>FX thread only.
 */
public final class ThemeManager {

    /** The preferences key this manager reads and writes. */
    public static final String PREF_KEY = "ui.theme";

    private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);

    // The applied theme is JVM-wide state (setUserAgentStylesheet is static), so tracking whether one
    // has been applied is JVM-wide too. Toolkit widgets read it to warn about the one configuration
    // where their styling silently does nothing.
    private static final AtomicBoolean THEME_APPLIED = new AtomicBoolean();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private final Prefs prefs;
    private final ReadOnlyObjectWrapper<FxTheme> current = new ReadOnlyObjectWrapper<>(this, "current");

    /**
     * A manager that persists the chosen theme.
     *
     * @param prefs where the choice is stored; must not be null (use {@link #ThemeManager()} for no persistence)
     */
    public ThemeManager(Prefs prefs) {
        if (prefs == null) {
            throw new NullPointerException("prefs (use the no-arg constructor for a non-persisting manager)");
        }
        this.prefs = prefs;
    }

    /** A manager that applies themes but stores nothing - for tests and throwaway windows. */
    public ThemeManager() {
        this.prefs = null;
    }

    /**
     * Applies the stored theme, falling back to {@link FxTheme#DEFAULT} when nothing is stored (or the stored name is
     * no longer a known theme).
     *
     * @return the theme that was applied
     */
    public FxTheme applyStored() {
        return applyStored(FxTheme.DEFAULT);
    }

    /**
     * Applies the stored theme, falling back to the given theme.
     *
     * @param fallback used when nothing is stored or the stored name is unknown
     * @return the theme that was applied
     */
    public FxTheme applyStored(FxTheme fallback) {
        final FxTheme theme = stored().orElse(fallback);
        apply(theme);
        return theme;
    }

    /** The stored theme, if a known one has been stored. */
    public Optional<FxTheme> stored() {
        return prefs == null ? Optional.empty() : FxTheme.byName(prefs.get(PREF_KEY, null));
    }

    /**
     * Applies a theme to the whole JVM and stores the choice.
     *
     * @param theme the theme to apply
     */
    public void apply(FxTheme theme) {
        FxThreads.requireFx();
        if (theme == null) {
            throw new NullPointerException("theme");
        }
        Application.setUserAgentStylesheet(theme.stylesheet());
        THEME_APPLIED.set(true);
        if (prefs != null) {
            prefs.put(PREF_KEY, theme.name());
        }
        current.set(theme);
    }

    /** Whether any {@code ThemeManager} in this JVM has applied a theme. */
    public static boolean isThemeApplied() {
        return THEME_APPLIED.get();
    }

    /**
     * Warns - once per JVM - that a toolkit widget was built before any theme was applied. In that state JavaFX falls
     * back to Modena, which does not define the colours {@code druvu-kit.css} looks up, so the widget renders unstyled
     * and JavaFX logs a conversion warning per rule. Called by {@link KitStyles#install(javafx.scene.Parent)}.
     */
    static void warnIfNoThemeApplied() {
        if (!THEME_APPLIED.get() && WARNED.compareAndSet(false, true)) {
            LOG.warn("A druvu-lib-fx widget was created before any theme was applied, so its colours will not resolve."
                    + " Call ThemeManager.applyStored() (or apply(FxTheme)) during application start-up.");
        }
    }

    /** The theme currently applied by this manager, or null until one has been applied. */
    public FxTheme current() {
        return current.get();
    }

    /** Observable view of {@link #current()} - bind widgets that paint their own colours to this. */
    public ReadOnlyObjectProperty<FxTheme> currentProperty() {
        return current.getReadOnlyProperty();
    }

    /** Whether the current theme is dark; false when no theme has been applied yet. */
    public boolean isDark() {
        final FxTheme theme = current.get();
        return theme != null && theme.isDark();
    }
}
