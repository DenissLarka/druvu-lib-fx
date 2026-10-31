package com.druvu.lib.fx.updater.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.druvu.lib.fx.updater.Asset;
import com.druvu.lib.fx.updater.Release;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

/**
 * Reading a release from the GitHub API's JSON: the saved answer for letterblade v1.1.1, tags with and without the
 * {@code v}, digests of other algorithms, a document that is not a release, and the file URL used for tests of a
 * packaged build.
 */
public class GitHubReleasesTest {

    @Test
    public void readsTheSavedAnswerForLetterblade() throws IOException {
        Release release = GitHubReleases.parse(fixture());

        assertThat(release.version()).isEqualTo(Version.parse("1.1.1"));
        assertThat(release.page())
                .isEqualTo(URI.create("https://github.com/DenissLarka/letterblade/releases/tag/v1.1.1"));
        assertThat(release.notes()).isNotEmpty();
        assertThat(release.assets())
                .extracting(Asset::name)
                .containsExactly("Letterblade.AppImage", "Letterblade.dmg", "Letterblade.msix");
        Asset msix = release.asset(".msix").orElseThrow();
        assertThat(msix.url())
                .isEqualTo(URI.create(
                        "https://github.com/DenissLarka/letterblade/releases/download/v1.1.1/Letterblade.msix"));
        assertThat(msix.size()).isEqualTo(95325475L);
        assertThat(msix.sha256()).isEqualTo("0f063ec8be63f27e870fcf50a3db760d29d2bdcc554417fbaf3c97cab5398c7c");
    }

    @Test
    public void aTagWithoutTheVIsAVersionToo() throws IOException {
        Release release = GitHubReleases.parse("""
                {"tag_name": "2.0.0", "html_url": "https://x/r", "body": null, "assets": []}
                """);

        assertThat(release.version()).isEqualTo(Version.parse("2.0.0"));
        assertThat(release.notes()).isEmpty();
        assertThat(release.assets()).isEmpty();
    }

    @Test
    public void aDigestOfAnotherAlgorithmLeavesTheAssetUnverifiable() throws IOException {
        Release release = GitHubReleases.parse("""
                {"tag_name": "v2.0.0", "html_url": "https://x/r", "assets": [
                  {"name": "A.msix", "browser_download_url": "https://x/A.msix", "size": 5, "digest": "md5:abc"},
                  {"name": "B.msix", "browser_download_url": "https://x/B.msix", "digest": null}
                ]}
                """);

        assertThat(release.assets())
                .allSatisfy(asset -> assertThat(asset.verifiable()).isFalse());
        assertThat(release.assets().get(1).size()).isEqualTo(-1);
    }

    @Test
    public void aTagThatIsNotAVersionIsRefused() {
        assertThatThrownBy(() ->
                        GitHubReleases.parse("{\"tag_name\": \"latest\", \"html_url\": \"https://x\", \"assets\": []}"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'latest'");
    }

    @Test
    public void aDocumentThatIsNotAReleaseIsRefused() {
        assertThatThrownBy(() -> GitHubReleases.parse("[1, 2]"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not the release description expected");
        assertThatThrownBy(() -> GitHubReleases.parse("{\"tag_name\": "))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("malformed JSON");
    }

    @Test
    public void aFileUrlServesAReleaseForTestsOfAPackagedBuild() throws IOException {
        Path file = Files.writeString(Files.createTempFile("release", ".json"), fixture(), StandardCharsets.UTF_8);

        Release release = GitHubReleases.at(file.toUri()).latest();

        assertThat(release.version()).isEqualTo(Version.parse("1.1.1"));
    }

    @Test
    public void theReleasePropertyRedirectsTheSource() throws IOException {
        Path file = Files.writeString(Files.createTempFile("release", ".json"), fixture(), StandardCharsets.UTF_8);
        System.setProperty(GitHubReleases.RELEASE_PROPERTY, file.toUri().toString());
        try {
            assertThat(GitHubReleases.of("DenissLarka", "letterblade").latest().version())
                    .isEqualTo(Version.parse("1.1.1"));
        } finally {
            System.clearProperty(GitHubReleases.RELEASE_PROPERTY);
        }
        assertThat(GitHubReleases.of("DenissLarka", "letterblade").toString())
                .contains("https://api.github.com/repos/DenissLarka/letterblade/releases/latest");
    }

    private static String fixture() throws IOException {
        try (InputStream in = GitHubReleasesTest.class.getResourceAsStream("letterblade-v1.1.1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
