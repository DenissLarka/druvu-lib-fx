/**
 * Self-update for an app on the kit: {@link com.druvu.lib.fx.update.Updates} wires the updater module to JavaFX - the
 * dialogs with the owner window, the download in the status bar, the user's choices in the app's preferences, the
 * Help-menu action.
 *
 * <pre>{@code
 * // in start(), once the stage is showing
 * updates = Updates.builder()
 *         .appName("Letterblade")
 *         .app(LetterbladeApp.class)
 *         .source(GitHubReleases.of("DenissLarka", "letterblade"))
 *         .owner(stage)
 *         .prefs(prefs)
 *         .exec(exec)
 *         .build();
 * updates.checkAtStart();
 * helpMenu.getItems().add(updates.checkForUpdatesMenuItem());
 * }</pre>
 *
 * <p>A development run is not an installed copy, so {@code updates.available()} is false there and nothing happens; the
 * Help-menu action then says so.
 */
package com.druvu.lib.fx.update;
