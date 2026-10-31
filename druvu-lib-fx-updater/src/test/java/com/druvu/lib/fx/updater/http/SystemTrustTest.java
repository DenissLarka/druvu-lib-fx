package com.druvu.lib.fx.updater.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.testng.annotations.Test;

/**
 * The Authority Information Access parsing that lets {@link SystemTrust} recover an intermediate CA a proxy withheld,
 * and the two rules of its initialisation: it can be switched off, and it never throws. No network access.
 */
public class SystemTrustTest {

    /** DER of the {@code id-ad-caIssuers} access method OID. */
    private static final byte[] CA_ISSUERS = {0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02};
    /** DER of the {@code id-ad-ocsp} access method OID, which must be ignored. */
    private static final byte[] OCSP = {0x06, 0x08, 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x01};

    @Test
    public void noExtensionYieldsNoUris() {
        assertThat(SystemTrust.caIssuerUris(null)).isEmpty();
        assertThat(SystemTrust.caIssuerUris(new byte[0])).isEmpty();
    }

    @Test
    public void readsTheCaIssuersUriAndSkipsOcsp() {
        String url = "http://pki.example.com/cert/SUB-CA.crt";
        assertThat(SystemTrust.caIssuerUris(accessDescription(CA_ISSUERS, url))).containsExactly(url);
        assertThat(SystemTrust.caIssuerUris(accessDescription(OCSP, "http://pki.example.com/ocsp")))
                .isEmpty();

        ByteArrayOutputStream der = new ByteArrayOutputStream();
        der.writeBytes(accessDescription(OCSP, "http://pki.example.com/ocsp"));
        der.writeBytes(accessDescription(CA_ISSUERS, url));
        assertThat(SystemTrust.caIssuerUris(der.toByteArray())).containsExactly(url);
    }

    @Test
    public void readsSeveralCaIssuersUris() {
        ByteArrayOutputStream der = new ByteArrayOutputStream();
        der.writeBytes(accessDescription(CA_ISSUERS, "http://one.example.com/a.crt"));
        der.writeBytes(accessDescription(CA_ISSUERS, "http://two.example.com/b.crt"));

        assertThat(SystemTrust.caIssuerUris(der.toByteArray()))
                .containsExactly("http://one.example.com/a.crt", "http://two.example.com/b.crt");
    }

    /** URLs longer than 127 bytes use the DER long form for the length; the parser must follow it. */
    @Test
    public void readsALongFormLengthUri() {
        String url = "http://pki.example.com/cert/" + "x".repeat(200) + ".crt";
        assertThat(url.length()).isGreaterThan(127);
        assertThat(SystemTrust.caIssuerUris(accessDescription(CA_ISSUERS, url))).containsExactly(url);
    }

    /** A truncated encoding is skipped quietly rather than thrown out of a TLS handshake. */
    @Test
    public void aTruncatedEncodingIsIgnored() {
        byte[] full = accessDescription(CA_ISSUERS, "http://pki.example.com/cert/SUB-CA.crt");
        byte[] truncated = new byte[full.length - 10];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        assertThat(SystemTrust.caIssuerUris(truncated)).isEmpty();
    }

    /** A caIssuers entry whose location is not a URI (an LDAP directoryName) is not misread. */
    @Test
    public void aNonUriLocationIsIgnored() {
        ByteArrayOutputStream der = new ByteArrayOutputStream();
        der.writeBytes(CA_ISSUERS);
        der.write(0x84); // some other GeneralName CHOICE
        der.write(4);
        der.writeBytes(new byte[] {1, 2, 3, 4});

        assertThat(SystemTrust.caIssuerUris(der.toByteArray())).isEmpty();
    }

    /** The mechanism is opt-out; disabling it leaves callers on plain JVM defaults. */
    @Test
    public void disabledByPropertyYieldsNoContext() {
        System.setProperty(SystemTrust.ENABLED_PROPERTY, "false");
        try {
            assertThat(SystemTrust.build()).isEmpty();
        } finally {
            System.clearProperty(SystemTrust.ENABLED_PROPERTY);
        }
    }

    /** Whatever the platform offers, initialisation never throws: updates outrank trust niceties. */
    @Test
    public void buildNeverThrows() {
        assertThat(SystemTrust.build()).isNotNull();
        assertThat(SystemTrust.sslContext()).isNotNull();
    }

    /** {@code AccessDescription ::= SEQUENCE { accessMethod OID, accessLocation GeneralName }}, URI form. */
    private static byte[] accessDescription(byte[] accessMethod, String uri) {
        byte[] value = uri.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(accessMethod);
        out.write(0x86); // context-specific 6: uniformResourceIdentifier
        if (value.length < 0x80) {
            out.write(value.length);
        } else if (value.length <= 0xFF) {
            out.write(0x81);
            out.write(value.length);
        } else {
            out.write(0x82);
            out.write((value.length >> 8) & 0xFF);
            out.write(value.length & 0xFF);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
