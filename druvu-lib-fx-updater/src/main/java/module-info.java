/**
 * druvu-lib-fx-updater - self-update for a packaged desktop app.
 *
 * <p>Three parts: an {@link com.druvu.lib.fx.updater.UpdateSource} says what the newest release is, an
 * {@link com.druvu.lib.fx.updater.install.Installation} knows the installed copy and how to replace it, and the
 * {@link com.druvu.lib.fx.updater.Updater} runs the conversation with the user through an
 * {@link com.druvu.lib.fx.updater.UpdateHost}. No JavaFX here: the kit module supplies the JavaFX host, and any other
 * toolkit or a console can supply its own.
 */
module com.druvu.lib.fx.updater {
    requires java.net.http;

    exports com.druvu.lib.fx.updater;
    exports com.druvu.lib.fx.updater.github;
    exports com.druvu.lib.fx.updater.install;
    exports com.druvu.lib.fx.updater.http;
}
