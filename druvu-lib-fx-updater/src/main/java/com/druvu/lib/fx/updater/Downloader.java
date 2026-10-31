package com.druvu.lib.fx.updater;

import com.druvu.lib.fx.updater.http.SystemTrust;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Fetches an {@link Asset} into the download folder and checks it against its published SHA-256 on the way, in one
 * pass. A download that does not match is deleted and refused.
 */
final class Downloader {

    /** Every download starts with this, so a later start can find the leftovers and delete them. */
    static final String FILE_PREFIX = "druvu-update-";

    private static final System.Logger LOG = System.getLogger(Downloader.class.getName());
    private static final String FILE_SCHEME = "file";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(2);
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final long REPORT_EVERY_BYTES = 1024 * 1024;

    private final Path directory;

    Downloader(Path directory) {
        this.directory = directory;
    }

    /**
     * @param asset what to fetch; must be {@link Asset#verifiable()}
     * @param progress told roughly once per megabyte and once at the end
     * @return the verified file, named after the asset
     * @throws VerificationException when the download does not match the published SHA-256
     * @throws IOException when the download fails
     */
    Path fetch(Asset asset, Consumer<Progress> progress) throws IOException {
        Path target = directory.resolve(FILE_PREFIX + asset.name());
        Files.createDirectories(directory);
        LOG.log(System.Logger.Level.INFO, "Downloading {0} from {1}", asset.name(), asset.url());
        String actual = FILE_SCHEME.equals(asset.url().getScheme())
                ? copyFile(asset, target, progress)
                : copyHttp(asset, target, progress);
        if (!actual.equals(asset.sha256())) {
            Files.deleteIfExists(target);
            throw new VerificationException("The downloaded " + asset.name()
                    + " does not match the SHA-256 the release publishes, so it was deleted");
        }
        LOG.log(System.Logger.Level.INFO, "Downloaded and verified {0} ({1} bytes)", target, Files.size(target));
        return target;
    }

    /** Deletes the downloads of earlier runs: a deferred registration or an opened disk image may have held them. */
    void deleteOldDownloads() {
        try (DirectoryStream<Path> downloads = Files.newDirectoryStream(directory, FILE_PREFIX + "*")) {
            for (Path download : downloads) {
                deleteOldDownload(download);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.DEBUG, "Cannot list old downloads in {0}: {1}", directory, e.getMessage());
        }
    }

    private static void deleteOldDownload(Path download) {
        try {
            Files.deleteIfExists(download);
            LOG.log(System.Logger.Level.INFO, "Deleted an old download: {0}", download);
        } catch (IOException e) {
            // Another process may still hold it; the next start tries again.
            LOG.log(System.Logger.Level.DEBUG, "Cannot delete old download {0}: {1}", download, e.getMessage());
        }
    }

    private static String copyFile(Asset asset, Path target, Consumer<Progress> progress) throws IOException {
        Path source = Path.of(asset.url());
        try (InputStream in = Files.newInputStream(source)) {
            return copy(in, target, Files.size(source), progress);
        }
    }

    private static String copyHttp(Asset asset, Path target, Consumer<Progress> progress) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(asset.url())
                .timeout(RESPONSE_TIMEOUT)
                .GET()
                .build();
        try (HttpClient client = SystemTrust.apply(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(CONNECT_TIMEOUT))
                .build()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download of " + asset.name() + " failed: HTTP " + response.statusCode());
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(asset.size());
            try (InputStream in = response.body()) {
                return copy(in, target, total, progress);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download of " + asset.name() + " interrupted", e);
        }
    }

    /** Copies the stream to the file, digesting as it goes; returns the SHA-256 as lower-case hex. */
    private static String copy(InputStream in, Path target, long total, Consumer<Progress> progress)
            throws IOException {
        MessageDigest digest = sha256();
        long done = 0;
        long reportedAt = 0;
        byte[] buffer = new byte[BUFFER_BYTES];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                if (done - reportedAt >= REPORT_EVERY_BYTES) {
                    progress.accept(new Progress(done, total));
                    reportedAt = done;
                }
            }
        }
        progress.accept(new Progress(done, total));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    /** The download is complete but is not the file the release publishes. */
    static final class VerificationException extends IOException {
        private static final long serialVersionUID = 1L;

        VerificationException(String message) {
            super(message);
        }
    }
}
