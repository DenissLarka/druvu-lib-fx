package com.druvu.lib.fx.updater.github;

import com.druvu.lib.fx.updater.Asset;
import com.druvu.lib.fx.updater.Release;
import com.druvu.lib.fx.updater.UpdateSource;
import com.druvu.lib.fx.updater.http.SystemTrust;
import java.io.IOException;
import java.lang.module.ModuleDescriptor.Version;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The newest release of a GitHub repository, through the REST API's {@code releases/latest}: its tag is the version (a
 * leading {@code v} dropped), its assets the installer packages with the SHA-256 GitHub publishes per asset.
 *
 * <p>For a packaged build that has no release yet, the system property {@value #RELEASE_PROPERTY} - or, for an
 * installed app started from a shell, the environment variable {@value #RELEASE_VARIABLE} - points the source at any
 * URL, a {@code file:} one included, that serves a document of the API's shape; {@link #of} honours them so an app
 * needs no code for the test.
 *
 * @author Deniss Larka <br>
 *     on 31 Oct 2026
 */
public final class GitHubReleases implements UpdateSource {

    /** System property: a URL that replaces the GitHub API for a test of a packaged build. */
    public static final String RELEASE_PROPERTY = "druvu.updater.release";
    /** Environment variable with the same meaning, for an installed app started from a shell. */
    public static final String RELEASE_VARIABLE = "DRUVU_UPDATER_RELEASE";

    private static final String API = "https://api.github.com/repos/%s/%s/releases/latest";
    private static final String FILE_SCHEME = "file";
    private static final String DIGEST_PREFIX = "sha256:";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final URI releaseDescription;

    private GitHubReleases(URI releaseDescription) {
        this.releaseDescription = releaseDescription;
    }

    /**
     * @param owner the repository's owner, for example {@code DenissLarka}
     * @param repo the repository, for example {@code letterblade}
     * @return the source, or the one {@value #RELEASE_PROPERTY} or {@value #RELEASE_VARIABLE} names when set
     */
    public static GitHubReleases of(String owner, String repo) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(repo, "repo");
        String override = System.getProperty(RELEASE_PROPERTY, System.getenv(RELEASE_VARIABLE));
        if (override != null && !override.isBlank()) {
            return at(URI.create(override));
        }
        return at(URI.create(String.format(API, owner, repo)));
    }

    /**
     * @param releaseDescription a URL serving one release in the API's shape; {@code file:} and {@code http(s):}
     * @return the source
     */
    public static GitHubReleases at(URI releaseDescription) {
        return new GitHubReleases(Objects.requireNonNull(releaseDescription, "releaseDescription"));
    }

    @Override
    public Release latest() throws IOException {
        String json = FILE_SCHEME.equals(releaseDescription.getScheme())
                ? Files.readString(Path.of(releaseDescription))
                : fetch(releaseDescription);
        return parse(json);
    }

    private static String fetch(URI uri) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        try (HttpClient client = SystemTrust.apply(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(TIMEOUT))
                .build()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("GitHub answered HTTP " + response.statusCode() + " for " + uri);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while asking GitHub for the latest release", e);
        }
    }

    /**
     * Reads one release from the API's JSON.
     *
     * @throws IOException when the document is not a release or its tag is not a version
     */
    static Release parse(String json) throws IOException {
        Map<String, Object> release;
        try {
            release = Json.object(Json.read(json));
        } catch (IllegalArgumentException e) {
            throw new IOException("GitHub's answer is not the release description expected: " + e.getMessage(), e);
        }
        String tag = Json.string(release, "tag_name");
        List<Asset> assets = new ArrayList<>();
        for (Map<String, Object> asset : Json.objects(release, "assets")) {
            assets.add(new Asset(
                    Json.string(asset, "name"),
                    URI.create(Json.string(asset, "browser_download_url")),
                    Json.number(asset, "size", -1),
                    sha256(Json.string(asset, "digest"))));
        }
        return new Release(
                version(tag), URI.create(Json.string(release, "html_url")), Json.string(release, "body"), assets);
    }

    /** {@code v1.2.0} and {@code 1.2.0} both read as 1.2.0. */
    private static Version version(String tag) throws IOException {
        String bare = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        try {
            return Version.parse(bare);
        } catch (IllegalArgumentException e) {
            throw new IOException("The release tag '" + tag + "' is not a version", e);
        }
    }

    /** GitHub writes {@code sha256:<hex>}; anything else is no SHA-256. */
    private static String sha256(String digest) {
        String lower = digest.toLowerCase(Locale.ROOT);
        return lower.startsWith(DIGEST_PREFIX) ? lower.substring(DIGEST_PREFIX.length()) : "";
    }

    @Override
    public String toString() {
        return "GitHub releases at " + releaseDescription;
    }
}
