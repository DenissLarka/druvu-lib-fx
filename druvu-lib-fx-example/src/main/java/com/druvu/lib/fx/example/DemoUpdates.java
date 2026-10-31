package com.druvu.lib.fx.example;

import com.druvu.lib.fx.updater.Asset;
import com.druvu.lib.fx.updater.Installed;
import com.druvu.lib.fx.updater.Release;
import com.druvu.lib.fx.updater.UpdateSource;
import com.druvu.lib.fx.updater.install.Installation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javafx.application.Application;

/**
 * The whole update conversation without a release: started with {@code --demo-updates}, the app believes it is an
 * installed copy at version 1.0.0 and the "release" 1.1.0 is a 24 MB file of random bytes in the temp folder, so the
 * offer, the download in the status bar, the SHA-256 check, the install and the restart dialog can all be seen.
 * Applying it does nothing but wait a moment; restarting quits the app, as it would for real.
 */
final class DemoUpdates {

    private static final String FLAG = "--demo-updates";
    private static final int PACKAGE_BYTES = 24 * 1024 * 1024;
    private static final Version CURRENT = Version.parse("1.0.0");
    private static final Version OFFERED = Version.parse("1.1.0");

    private DemoUpdates() {}

    static boolean requested(Application app) {
        return app.getParameters().getUnnamed().contains(FLAG);
    }

    static Version currentVersion() {
        return CURRENT;
    }

    /** A release whose one asset is a file of random bytes, with its true digest. */
    static UpdateSource source() {
        Path file = randomPackage();
        Path fileName = file.getFileName();
        Asset asset =
                new Asset(fileName == null ? "demo" : fileName.toString(), file.toUri(), size(file), sha256(file));
        Release release = new Release(
                OFFERED,
                URI.create("https://github.com/DenissLarka/druvu-lib-fx"),
                "Market Watch " + OFFERED
                        + " (a demo)\n\n- The update conversation, end to end\n- Nothing is installed",
                List.of(asset));
        return () -> release;
    }

    /** An installed copy that applies a package by waiting a moment. */
    static Installation installation() {
        return new Installation() {
            @Override
            public String assetSuffix() {
                return ".demo";
            }

            @Override
            public Path location() {
                return Path.of("demo");
            }

            @Override
            public Installed apply(Path download, Version version) throws IOException {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
                return new Installed.Replaced(version);
            }

            @Override
            public void relaunch() {
                // a demo restarts nothing; the app quits as it would for real
            }

            @Override
            public String toString() {
                return "the demo installation";
            }
        };
    }

    private static Path randomPackage() {
        try {
            Path file = Files.createTempFile("market-watch-demo-", ".demo");
            byte[] bytes = new byte[PACKAGE_BYTES];
            ThreadLocalRandom.current().nextBytes(bytes);
            Files.write(file, bytes);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
