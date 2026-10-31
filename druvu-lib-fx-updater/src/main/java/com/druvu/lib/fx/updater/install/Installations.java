package com.druvu.lib.fx.updater.install;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Recognises the installed copy from an {@link Environment}. The rules are the three installers' layouts: the AppImage
 * runtime exports {@code APPIMAGE}; the macOS installer puts the Java runtime inside {@code <App>.app/Contents}; the
 * MSIX installer puts it under Windows' {@code WindowsApps} folder. A JDK's own {@code .jdk/Contents/Home} on macOS is
 * not a bundle, and a package built but not installed (a staging folder) is not under {@code WindowsApps}: both are
 * development runs.
 */
final class Installations {

    private static final String APPIMAGE_VARIABLE = "APPIMAGE";
    private static final String BUNDLE_SUFFIX = ".app";
    private static final String BUNDLE_CONTENTS = "Contents";
    private static final String WINDOWS_PACKAGES_FOLDER = "WindowsApps";

    private Installations() {}

    static Optional<Installation> detect(Environment environment) {
        Optional<String> appImage = environment.variable(APPIMAGE_VARIABLE);
        if (appImage.isPresent()) {
            return Optional.of(new AppImageInstallation(Path.of(appImage.get()), environment.pid()));
        }
        if (environment.isMac()) {
            return bundleAround(environment.javaHome()).map(bundle -> new MacInstallation(bundle, environment.pid()));
        }
        if (environment.isWindows() && hasSegment(environment.javaHome(), WINDOWS_PACKAGES_FOLDER)) {
            return Optional.of(new MsixInstallation(environment.javaHome(), environment.pid()));
        }
        return Optional.empty();
    }

    /** The {@code .app} folder whose {@code Contents} holds the given path, if any. */
    private static Optional<Path> bundleAround(Path inside) {
        for (Path ancestor = inside.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            Path name = ancestor.getFileName();
            if (name != null
                    && name.toString().endsWith(BUNDLE_SUFFIX)
                    && inside.startsWith(ancestor.resolve(BUNDLE_CONTENTS))) {
                return Optional.of(ancestor);
            }
        }
        return Optional.empty();
    }

    /** Split on both separators, so a Windows path reads the same on every JVM (the tests run on all three). */
    private static boolean hasSegment(Path path, String segment) {
        for (String element : path.toString().split("[\\\\/]")) {
            if (element.equalsIgnoreCase(segment)) {
                return true;
            }
        }
        return false;
    }
}
