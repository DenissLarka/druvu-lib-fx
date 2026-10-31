/**
 * The installed copy of the application: which kind runs, how an installer package is applied to it and how the new
 * version is started.
 *
 * <p>{@link com.druvu.lib.fx.updater.install.Installation#detect()} recognises the three kinds a druvu installer
 * produces - a Linux AppImage, a macOS application bundle, a Windows MSIX package - and answers empty for anything
 * else, a development run above all.
 */
package com.druvu.lib.fx.updater.install;
