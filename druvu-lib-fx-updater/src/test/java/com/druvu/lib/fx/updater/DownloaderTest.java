package com.druvu.lib.fx.updater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.druvu.lib.fx.updater.fixtures.Assets;
import com.druvu.lib.fx.updater.fixtures.TinyHttpServer;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Downloads over HTTP and from a file, the progress reports, the SHA-256 check and the clean-up of old downloads. */
public class DownloaderTest {

    private static final byte[] PACKAGE = "the package bytes, served over HTTP".getBytes(StandardCharsets.UTF_8);

    private TinyHttpServer server;
    private Path folder;

    @BeforeClass
    public void aServerAndAFolder() throws IOException {
        folder = Files.createTempDirectory("downloader-test");
        server = new TinyHttpServer().serving("/Example.msix", PACKAGE);
    }

    @AfterClass
    public void stopTheServer() throws IOException {
        server.close();
    }

    @Test
    public void downloadsOverHttpReportingTheProgressAgainstTheContentLength() throws IOException {
        Asset asset = new Asset(
                "Example.msix",
                served("/Example.msix"),
                -1,
                Assets.sha256(Files.write(folder.resolve("expected"), PACKAGE)));
        List<Progress> progresses = new ArrayList<>();

        Path download = new Downloader(folder).fetch(asset, progresses::add);

        assertThat(download)
                .hasFileName(Downloader.FILE_PREFIX + "Example.msix")
                .hasBinaryContent(PACKAGE);
        assertThat(progresses.getLast()).isEqualTo(new Progress(PACKAGE.length, PACKAGE.length));
        assertThat(progresses.getLast().fraction()).isEqualTo(1.0);
    }

    @Test
    public void aMissingPackageNamesTheStatus() {
        Asset asset = new Asset("missing.msix", served("/missing.msix"), -1, "0".repeat(64));

        assertThatThrownBy(() -> new Downloader(folder).fetch(asset, progress -> {}))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    public void copiesAFileUrlAndChecksIt() throws IOException {
        Path source = Assets.aPackage(folder, "Example.AppImage", "from a file");

        Path download = new Downloader(folder).fetch(Assets.anAssetOf(source), progress -> {});

        assertThat(download).hasSameBinaryContentAs(source);
    }

    @Test
    public void aWrongDigestDeletesTheDownloadAndRefuses() {
        Path source = Assets.aPackage(folder, "Tampered.AppImage", "not what was published");

        assertThatThrownBy(() -> new Downloader(folder).fetch(Assets.aTamperedAssetOf(source), progress -> {}))
                .isInstanceOf(Downloader.VerificationException.class)
                .hasMessageContaining("Tampered.AppImage");
        assertThat(folder.resolve(Downloader.FILE_PREFIX + "Tampered.AppImage")).doesNotExist();
    }

    @Test
    public void deletesOnlyItsOwnOldDownloads() throws IOException {
        Path own = Files.writeString(folder.resolve(Downloader.FILE_PREFIX + "Old.dmg"), "old");
        Path foreign = Files.writeString(folder.resolve("notes.txt"), "mine");

        new Downloader(folder).deleteOldDownloads();

        assertThat(own).doesNotExist();
        assertThat(foreign).exists();
    }

    @Test
    public void aMissingDownloadFolderIsNotAnError() {
        new Downloader(folder.resolve("never-created")).deleteOldDownloads();
    }

    private URI served(String path) {
        return server.url(path);
    }
}
