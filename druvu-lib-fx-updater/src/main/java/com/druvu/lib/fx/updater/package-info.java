/**
 * The update conversation and its vocabulary.
 *
 * <p>{@link com.druvu.lib.fx.updater.Updater} is the entry: built once from a source, the current version, the
 * installation, the user's settings and a host, it runs {@link com.druvu.lib.fx.updater.Updater#checkAtStart()} or
 * {@link com.druvu.lib.fx.updater.Updater#checkNow()} on the caller's thread and returns an
 * {@link com.druvu.lib.fx.updater.Outcome}. Everything the user sees goes through the
 * {@link com.druvu.lib.fx.updater.UpdateHost}; everything the user decided for good goes through the
 * {@link com.druvu.lib.fx.updater.UpdateSettings}.
 */
package com.druvu.lib.fx.updater;
