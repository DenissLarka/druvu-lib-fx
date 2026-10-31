package com.druvu.lib.fx.updater.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;

/**
 * TLS trust that still works behind a corporate TLS-inspecting proxy.
 *
 * <p>A packaged application runs on a private {@code jlink} runtime whose {@code cacerts} is rebuilt by every update,
 * so the usual desktop remedy - importing the company CA into the JDK - cannot be applied by the user and would not
 * survive if it were. Without this class an update check behind such a proxy fails permanently with {@code PKIX path
 * building failed}.
 *
 * <p>Resolution order for a server certificate:
 *
 * <ol>
 *   <li>the JVM default trust store ({@code cacerts}) accepts it: done, the normal internet case;
 *   <li>otherwise the <b>operating-system</b> trust store is tried - a company CA is installed there by IT on every
 *       managed machine, which is why browsers and other native applications keep working;
 *   <li>if that fails only because the proxy withheld an intermediate CA, the missing certificate is fetched from the
 *       {@code caIssuers} URL the chain itself publishes (RFC 5280 Authority Information Access) and validation is
 *       retried.
 * </ol>
 *
 * <p><b>The operating-system store supplies the trust anchors; AIA only supplies path material.</b> A downloaded
 * certificate is never an anchor - it is handed to the platform trust manager as an extra chain element and is
 * therefore useless unless it is genuinely signed by a CA the machine already trusts. An attacker who points
 * {@code caIssuers} at a certificate authority of their own gains nothing.
 *
 * <p>Hostname verification, algorithm constraints and revocation policy are left to the platform trust manager: this
 * class decides <em>which anchors</em> to offer and <em>what chain</em> to offer them, never whether a chain is valid.
 *
 * <p>Set {@code -Ddruvu.updater.systemTrust=false} to switch the whole mechanism off and use plain JVM defaults.
 *
 * @author Deniss Larka <br>
 *     on 04 Aug 2026
 */
public final class SystemTrust {

    /** System property; set to {@code false} to keep the plain JVM default trust behaviour. */
    public static final String ENABLED_PROPERTY = "druvu.updater.systemTrust";

    private static final System.Logger LOG = System.getLogger(SystemTrust.class.getName());

    /** Authority Information Access extension (RFC 5280 section 4.2.2.1). */
    private static final String AIA_OID = "1.3.6.1.5.5.7.1.1";
    /** DER encoding of the {@code id-ad-caIssuers} access method OID, {@code 1.3.6.1.5.5.7.48.2}. */
    private static final byte[] CA_ISSUERS_OID = {0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02};
    /** GeneralName CHOICE tag for {@code uniformResourceIdentifier} (context-specific 6). */
    private static final int URI_TAG = 0x86;

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(5);
    /** Bounds how many certificates a single chain may gain; a legitimate gap is one or two links. */
    private static final int MAX_FETCHED_LINKS = 4;
    /** Successfully downloaded intermediates, keyed by URL. Bounded - a machine meets very few proxies. */
    private static final Map<String, X509Certificate> FETCHED = new ConcurrentHashMap<>();

    private static final int MAX_CACHED = 16;
    /** Well-known CA bundles on Linux, which has no queryable system trust store. */
    private static final List<String> LINUX_BUNDLES = List.of(
            "/etc/ssl/certs/ca-certificates.crt",
            "/etc/pki/tls/certs/ca-bundle.crt",
            "/etc/ssl/ca-bundle.pem",
            "/etc/ssl/cert.pem");

    private static final Object LOCK = new Object();
    private static Optional<SSLContext> context;

    private SystemTrust() {}

    /**
     * The shared {@link SSLContext} for traffic that must work on a managed machine, or empty when the mechanism is
     * disabled or the platform offers nothing beyond {@code cacerts} - callers then simply use JVM defaults.
     *
     * @return the context, built once
     */
    public static Optional<SSLContext> sslContext() {
        synchronized (LOCK) {
            if (context == null) {
                context = build();
            }
            return context;
        }
    }

    /**
     * Applies {@link #sslContext()} to a client builder, leaving it untouched when there is nothing to add.
     *
     * @param builder the client under construction
     * @return the same builder
     */
    public static HttpClient.Builder apply(HttpClient.Builder builder) {
        sslContext().ifPresent(builder::sslContext);
        return builder;
    }

    /** Visible for testing; {@link #sslContext()} is the cached entry point. */
    static Optional<SSLContext> build() {
        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Operating-system trust store disabled via -D{0}=false",
                    ENABLED_PROPERTY);
            return Optional.empty();
        }
        try {
            X509ExtendedTrustManager fallback = trustManager(systemTrustStore());
            if (fallback == null) {
                LOG.log(System.Logger.Level.DEBUG, "No operating-system trust store on this platform - JVM defaults");
                return Optional.empty();
            }
            X509ExtendedTrustManager defaults = trustManager(null);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[] {new FallbackTrustManager(defaults, fallback)}, null);
            return Optional.of(ssl);
        } catch (GeneralSecurityException | RuntimeException e) {
            // Never let a trust-store quirk break updates outright: degrade to the JVM default behaviour.
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Cannot initialise operating-system trust - JVM defaults ({0})",
                    e.toString());
            return Optional.empty();
        }
    }

    // ---- platform trust stores ----

    /** @return the OS trust store, or {@code null} when the platform exposes none */
    private static KeyStore systemTrustStore() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            // SunMSCAPI exposes the root store only; the Windows intermediate store is unreachable from Java,
            // which is precisely why the AIA completion below is needed.
            return load("Windows-ROOT");
        }
        if (os.contains("mac")) {
            KeyStore roots = load("KeychainStore-ROOT");
            return roots != null ? roots : load("KeychainStore");
        }
        return linuxBundle();
    }

    private static KeyStore load(String type) {
        try {
            KeyStore store = KeyStore.getInstance(type);
            store.load(null, null);
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Operating-system trust store {0} opened, {1} entries",
                    type,
                    store.size());
            return store;
        } catch (IOException | GeneralSecurityException e) {
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Operating-system trust store {0} unavailable ({1})",
                    type,
                    e.toString());
            return null;
        }
    }

    private static KeyStore linuxBundle() {
        for (String candidate : LINUX_BUNDLES) {
            Path path = Path.of(candidate);
            if (!Files.isReadable(path)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(path)) {
                Collection<? extends Certificate> certs =
                        CertificateFactory.getInstance("X.509").generateCertificates(in);
                if (certs.isEmpty()) {
                    continue;
                }
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, null);
                int i = 0;
                for (Certificate cert : certs) {
                    store.setCertificateEntry("bundle-" + i++, cert);
                }
                LOG.log(System.Logger.Level.DEBUG, "Linux CA bundle {0} loaded, {1} entries", candidate, certs.size());
                return store;
            } catch (IOException | GeneralSecurityException e) {
                LOG.log(System.Logger.Level.DEBUG, "Cannot read CA bundle {0} ({1})", candidate, e.toString());
            }
        }
        return null;
    }

    /** @param anchors trust anchors, or {@code null} for the JVM default store */
    private static X509ExtendedTrustManager trustManager(KeyStore anchors) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(anchors);
        for (TrustManager candidate : factory.getTrustManagers()) {
            // The extended variant is required: it is the one that performs hostname verification.
            if (candidate instanceof X509ExtendedTrustManager extended) {
                return extended;
            }
        }
        return null;
    }

    // ---- AIA chain completion ----

    /**
     * Returns {@code chain} extended with any intermediates it is missing, resolved through the {@code caIssuers}
     * pointers the certificates publish. Returns the input unchanged when the chain already reaches an anchor, when
     * nothing is published, or when a download fails - completion is best-effort and never fatal.
     */
    private static X509Certificate[] complete(X509Certificate[] chain, Set<X500Principal> anchorSubjects) {
        List<X509Certificate> path = new ArrayList<>(List.of(chain));
        for (int link = 0; link < MAX_FETCHED_LINKS; link++) {
            X509Certificate top = path.get(path.size() - 1);
            X500Principal issuer = top.getIssuerX500Principal();
            if (issuer.equals(top.getSubjectX500Principal()) || anchorSubjects.contains(issuer)) {
                break; // self-issued, or the next link is an anchor we already hold - nothing to fetch
            }
            X509Certificate parent = fetchIssuer(top);
            if (parent == null || path.contains(parent)) {
                break;
            }
            path.add(parent);
        }
        return path.size() == chain.length ? chain : path.toArray(new X509Certificate[0]);
    }

    /** Downloads the issuer of {@code cert} from its {@code caIssuers} URLs; {@code null} when unavailable. */
    private static X509Certificate fetchIssuer(X509Certificate cert) {
        for (String url : caIssuerUris(cert.getExtensionValue(AIA_OID))) {
            X509Certificate cached = FETCHED.get(url);
            if (cached != null) {
                return cached;
            }
            X509Certificate downloaded = download(url, cert.getIssuerX500Principal());
            if (downloaded != null) {
                if (FETCHED.size() < MAX_CACHED) {
                    FETCHED.put(url, downloaded);
                }
                return downloaded;
            }
        }
        return null;
    }

    private static X509Certificate download(String url, X500Principal wanted) {
        String scheme = URI.create(url).getScheme();
        if (scheme == null || !Set.of("http", "https").contains(scheme.toLowerCase(Locale.ROOT))) {
            return null; // LDAP pointers are common in Active Directory PKIs but are not reachable this way
        }
        // A plain client on purpose: it uses the JVM default SSLContext, so an https pointer cannot recurse into
        // this trust manager. If such a pointer is itself intercepted the fetch simply fails, as it should.
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(FETCH_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(FETCH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                LOG.log(System.Logger.Level.DEBUG, "caIssuers fetch {0} returned HTTP {1}", url, response.statusCode());
                return null;
            }
            // The endpoint may serve a bare certificate or a PKCS#7 bag; both parse through generateCertificates.
            Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(response.body()));
            for (Certificate candidate : certs) {
                if (candidate instanceof X509Certificate x509
                        && x509.getSubjectX500Principal().equals(wanted)) {
                    LOG.log(
                            System.Logger.Level.DEBUG,
                            "Fetched missing intermediate {0} from {1}",
                            wanted.getName(),
                            url);
                    return x509;
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "caIssuers fetch {0} failed ({1})", url, e.toString());
            return null;
        }
    }

    /**
     * Extracts {@code caIssuers} URLs from a DER-encoded Authority Information Access extension.
     *
     * <p>The structure is a SEQUENCE of {@code AccessDescription ::= { accessMethod OID, accessLocation GeneralName }},
     * so every {@code id-ad-caIssuers} OID is followed by its location. Only URI locations are of interest here, so the
     * encoding is scanned for that OID followed by a context-specific tag 6.
     */
    static List<String> caIssuerUris(byte[] extensionValue) {
        List<String> uris = new ArrayList<>();
        if (extensionValue == null) {
            return uris;
        }
        for (int i = 0; i + CA_ISSUERS_OID.length < extensionValue.length; i++) {
            if (!startsWith(extensionValue, i, CA_ISSUERS_OID)) {
                continue;
            }
            int p = i + CA_ISSUERS_OID.length;
            if ((extensionValue[p] & 0xFF) != URI_TAG) {
                continue;
            }
            p++;
            int first = extensionValue[p++] & 0xFF;
            int length;
            if ((first & 0x80) == 0) {
                length = first;
            } else if (first == 0x81 && p < extensionValue.length) {
                length = extensionValue[p++] & 0xFF;
            } else if (first == 0x82 && p + 1 < extensionValue.length) {
                length = ((extensionValue[p] & 0xFF) << 8) | (extensionValue[p + 1] & 0xFF);
                p += 2;
            } else {
                continue;
            }
            if (length <= 0 || p + length > extensionValue.length) {
                continue;
            }
            uris.add(new String(extensionValue, p, length, StandardCharsets.US_ASCII));
        }
        return uris;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    // ---- trust manager ----

    /** A validation call, so the four {@code checkServerTrusted} overloads share one fallback path. */
    @FunctionalInterface
    private interface Validation {
        void run(X509ExtendedTrustManager manager, X509Certificate[] chain) throws CertificateException;
    }

    /**
     * Delegates to the JVM default trust manager and, only when that rejects a chain, retries against the
     * operating-system trust store with any missing intermediates filled in.
     */
    private static final class FallbackTrustManager extends X509ExtendedTrustManager {
        private final X509ExtendedTrustManager defaults;
        private final X509ExtendedTrustManager system;
        private final Set<X500Principal> anchorSubjects;

        private FallbackTrustManager(X509ExtendedTrustManager defaults, X509ExtendedTrustManager system) {
            this.defaults = defaults;
            this.system = system;
            Set<X500Principal> subjects = new HashSet<>();
            for (X509Certificate anchor : system.getAcceptedIssuers()) {
                subjects.add(anchor.getSubjectX500Principal());
            }
            this.anchorSubjects = Collections.unmodifiableSet(subjects);
        }

        private void verify(X509Certificate[] chain, Validation validation) throws CertificateException {
            try {
                validation.run(defaults, chain);
            } catch (CertificateException notInCacerts) {
                X509Certificate[] path = complete(chain, anchorSubjects);
                try {
                    validation.run(system, path);
                } catch (CertificateException notInSystemStore) {
                    notInCacerts.addSuppressed(notInSystemStore);
                    throw notInCacerts;
                }
                LOG.log(
                        System.Logger.Level.INFO,
                        "Certificate for {0} accepted through the operating-system trust store{1}",
                        chain[0].getSubjectX500Principal().getName(),
                        path.length > chain.length ? " after completing the chain from AIA" : "");
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            verify(chain, (manager, path) -> manager.checkServerTrusted(path, authType));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            verify(chain, (manager, path) -> manager.checkServerTrusted(path, authType, socket));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            verify(chain, (manager, path) -> manager.checkServerTrusted(path, authType, engine));
        }

        // The application is always the client here; these exist only to satisfy the contract.
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            defaults.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            defaults.checkClientTrusted(chain, authType, socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            defaults.checkClientTrusted(chain, authType, engine);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>(List.of(defaults.getAcceptedIssuers()));
            all.addAll(List.of(system.getAcceptedIssuers()));
            return all.toArray(new X509Certificate[0]);
        }
    }
}
