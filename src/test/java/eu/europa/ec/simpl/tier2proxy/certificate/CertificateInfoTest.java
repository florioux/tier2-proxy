package eu.europa.ec.simpl.tier2proxy.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CertificateInfoTest {

    private PrivateKey privateKey;
    private X509Certificate certificate;

    @BeforeEach
    void setup() {
        privateKey = mock(PrivateKey.class);
        certificate = mock(X509Certificate.class);
        var principal = new X500Principal("CN=Test Subject");
        when(certificate.getSubjectX500Principal()).thenReturn(principal);
    }

    @Test
    void testRecordFields() {
        CertificateInfo info = new CertificateInfo(privateKey, certificate);

        assertThat(info.privateKey()).isEqualTo(privateKey);
        assertThat(info.certificate()).isEqualTo(certificate);
    }

    @Test
    void testToStringReturnsSubject() {
        CertificateInfo info = new CertificateInfo(privateKey, certificate);

        String toStringResult = info.toString();

        assertThat(toStringResult).isEqualTo("CN=Test Subject");
    }
}
