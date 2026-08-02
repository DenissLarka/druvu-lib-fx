package com.druvu.lib.fx.theme;

import java.util.Objects;
import javafx.scene.Parent;

/**
 * The toolkit's own stylesheet and the style classes its widgets carry.
 *
 * <p>Toolkit widgets do not paint themselves with literal colours; they wear a style class defined in
 * {@code druvu-kit.css} in terms of the applied theme's colours. That is why a {@code Notifications} toast turns light
 * or dark with the rest of the app without anyone telling it which theme is in force.
 *
 * <p>Apps do not normally touch this class - the widgets attach the stylesheet themselves. It is public for apps that
 * build their own controls and want to match, e.g. a status chip that should use the toolkit's error colour.
 */
public final class KitStyles {

    /** Style class of a toast container; combined with one of the level classes below. */
    public static final String TOAST = "druvu-toast";

    public static final String TOAST_INFO = "druvu-toast-info";
    public static final String TOAST_SUCCESS = "druvu-toast-success";
    public static final String TOAST_WARNING = "druvu-toast-warning";
    public static final String TOAST_ERROR = "druvu-toast-error";

    /** Style class for text that reports a failure, e.g. the login pane's message. */
    public static final String ERROR_LABEL = "druvu-error-label";

    private static final String STYLESHEET = Objects.requireNonNull(
                    KitStyles.class.getResource("druvu-kit.css"), "druvu-kit.css is missing from the jar")
            .toExternalForm();

    private KitStyles() {}

    /** The toolkit stylesheet URL, for anything that manages its own stylesheet list. */
    public static String stylesheet() {
        return STYLESHEET;
    }

    /**
     * Attaches the toolkit stylesheet to a widget root, unless it is already there. Also warns once if no theme has
     * been applied yet, because the stylesheet's colours resolve only under an {@link FxTheme}.
     *
     * @param root the widget's own root, so the styles travel with it into any scene or popup
     */
    public static void install(Parent root) {
        Objects.requireNonNull(root, "root");
        if (!root.getStylesheets().contains(STYLESHEET)) {
            root.getStylesheets().add(STYLESHEET);
        }
        ThemeManager.warnIfNoThemeApplied();
    }
}
