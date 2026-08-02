package com.druvu.lib.fx.theme;

import static org.assertj.core.api.Assertions.assertThat;

import com.druvu.lib.fx.FxTestToolkit;
import com.druvu.lib.fx.dock.DockNode;
import com.druvu.lib.fx.dock.DockPane;
import com.druvu.lib.fx.dock.DockPos;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Guards the one thing that silently breaks: a toolkit style class whose colours do not resolve. JavaFX does not fail
 * on an unresolvable looked-up colour - it logs and leaves the property unset - so only reading the rendered value
 * proves the CSS works.
 */
public class KitStylesTest {

    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern DECLARATION = Pattern.compile("(-fx-[a-z-]+)\\s*:\\s*([^;{}]+);");
    private static final Pattern MODENA_LOOKUP = Pattern.compile("(?<![\\w-])-fx-[a-z-]+");

    @BeforeClass
    public void startToolkit() {
        FxTestToolkit.ensureStarted();
    }

    @Test
    public void toastColoursResolveUnderEveryTheme() throws InterruptedException {
        for (FxTheme theme : FxTheme.values()) {
            final AtomicReference<Color> fill = new AtomicReference<>();
            final AtomicReference<Color> textFill = new AtomicReference<>();
            FxTestToolkit.runOnFx(() -> {
                new ThemeManager().apply(theme);

                final Label label = new Label("toast");
                final HBox toast = new HBox(label);
                toast.getStyleClass().addAll(KitStyles.TOAST, KitStyles.TOAST_ERROR);
                KitStyles.install(toast);

                showAndApplyCss(toast);
                fill.set((Color) toast.getBackground().getFills().getFirst().getFill());
                textFill.set((Color) label.getTextFill());
            });

            assertThat(fill.get()).as("toast background under %s", theme).isNotNull();
            assertThat(textFill.get()).as("toast text under %s", theme).isNotNull();
            // The error fill must actually differ from the text on it, or the toast is unreadable.
            assertThat(fill.get())
                    .as("toast fill differs from its text under %s", theme)
                    .isNotEqualTo(textFill.get());
        }
    }

    /**
     * The vendored dock stylesheet used to look up Modena-only colours, which resolve to nothing under an FxTheme - the
     * dock chrome rendered unstyled and JavaFX logged a conversion warning per rule. Assert the swapped-in theme
     * colours actually resolve, and that they follow the theme.
     */
    @Test
    public void dockChromeFollowsTheTheme() throws InterruptedException {
        final AtomicReference<Color> light = new AtomicReference<>();
        final AtomicReference<Color> dark = new AtomicReference<>();
        FxTestToolkit.runOnFx(() -> {
            final ThemeManager manager = new ThemeManager();
            light.set(dockNodeFillUnder(manager, FxTheme.PRIMER_LIGHT));
            dark.set(dockNodeFillUnder(manager, FxTheme.PRIMER_DARK));
        });

        assertThat(light.get()).as("dock-node background under a light theme").isNotNull();
        assertThat(dark.get()).as("dock-node background under a dark theme").isNotNull();
        assertThat(light.get()).as("dock chrome follows the theme").isNotEqualTo(dark.get());
    }

    private static Color dockNodeFillUnder(ThemeManager manager, FxTheme theme) {
        manager.apply(theme);
        final DockPane dockPane = new DockPane();
        final DockNode node = new DockNode(new Label("content"), "Panel");
        node.dock(dockPane, DockPos.CENTER);

        final Stage stage = new Stage();
        stage.setScene(new Scene(dockPane, 400, 300));
        stage.show();
        dockPane.applyCss();
        dockPane.layout();
        stage.hide();
        return (Color) node.getBackground().getFills().getFirst().getFill();
    }

    /**
     * The regression guard the colour assertions cannot provide. A failed lookup is invisible to a rendered-value
     * check: JavaFX drops the declaration and the node inherits from a less specific rule, which usually looks close
     * enough - three Modena leftovers survived the AtlantaFX migration exactly that way, and one of them
     * ({@code .dock-area-indicator}) left the drag-and-drop preview with no fill at all.
     *
     * <p>So assert on the source instead: in kit stylesheets every colour must come from AtlantaFX, whose variables are
     * all {@code -color-*}. A {@code -fx-*} token in a <em>value</em> is therefore always a Modena lookup, and Modena
     * is exactly what is not applied once a theme is.
     */
    @Test
    public void kitStylesheetsLookUpNoModenaVariables() throws Exception {
        for (String stylesheet : List.of("dock/default.css", "theme/druvu-kit.css")) {
            final String css = readKitResource(stylesheet);
            final Matcher declaration = DECLARATION.matcher(COMMENT.matcher(css).replaceAll(" "));
            while (declaration.find()) {
                final String property = declaration.group(1);
                final String value = declaration.group(2).trim();
                assertThat(MODENA_LOOKUP.matcher(value).find())
                        .as(
                                "%s: '%s: %s' looks up a Modena variable, which no FxTheme defines",
                                stylesheet, property, value)
                        .isFalse();
            }
        }
    }

    private static String readKitResource(String path) throws Exception {
        try (InputStream in = KitStyles.class.getModule().getResourceAsStream("com/druvu/lib/fx/" + path)) {
            assertThat(in).as("%s is on the classpath", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void darkAndLightThemesGiveDifferentToastFills() throws InterruptedException {
        final AtomicReference<Color> light = new AtomicReference<>();
        final AtomicReference<Color> dark = new AtomicReference<>();
        FxTestToolkit.runOnFx(() -> {
            final ThemeManager manager = new ThemeManager();
            light.set(toastFillUnder(manager, FxTheme.PRIMER_LIGHT));
            dark.set(toastFillUnder(manager, FxTheme.PRIMER_DARK));
        });
        assertThat(light.get()).isNotEqualTo(dark.get());
    }

    private static Color toastFillUnder(ThemeManager manager, FxTheme theme) {
        manager.apply(theme);
        final HBox toast = new HBox(new Label("toast"));
        toast.getStyleClass().addAll(KitStyles.TOAST, KitStyles.TOAST_INFO);
        KitStyles.install(toast);
        showAndApplyCss(toast);
        return (Color) toast.getBackground().getFills().getFirst().getFill();
    }

    /** CSS is only computed for a node in a shown scene, so give it one. */
    private static void showAndApplyCss(Node node) {
        final Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(node), 200, 100));
        stage.show();
        node.applyCss();
        ((Parent) node).layout();
        stage.hide();
    }

    @AfterClass
    public void resetTheme() throws InterruptedException {
        FxTestToolkit.resetUserAgentStylesheet();
    }
}
