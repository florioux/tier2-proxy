package eu.europa.ec.simpl.tier2proxy.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;

import eu.europa.ec.simpl.client.util.CertificateRevocation;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MTLSTrustManagerTest {

    @Mock
    private X509TrustManager delegate;

    @Mock
    private CertificateRevocation certificateRevocation;

    @InjectMocks
    private MTLSTrustManager mtlsTrustManager;

    @Test
    void shouldDelegateClientTrustCheck() throws CertificateException {
        // given
        var cert = mock(X509Certificate.class);
        var certs = new X509Certificate[] {cert};

        // when
        mtlsTrustManager.checkClientTrusted(certs, "RSA");

        // then
        then(delegate).should().checkClientTrusted(certs, "RSA");
    }

    @Test
    void shouldCheckServerTrustWithRevocation() {
        // given
        var cert = mock(X509Certificate.class);
        given(cert.getSubjectX500Principal()).willReturn(new X500Principal("CN=test"));
        given(cert.getSerialNumber()).willReturn(java.math.BigInteger.ONE);

        var certs = new X509Certificate[] {cert};

        mtlsTrustManager.checkServerTrusted(certs, "RSA");

        // then
        then(certificateRevocation).should().verify(cert);
    }

    @Test
    void shouldReturnAcceptedIssuersFromDelegate() {
        // given
        var expected = new X509Certificate[] {mock(X509Certificate.class)};
        given(delegate.getAcceptedIssuers()).willReturn(expected);

        // when + then
        assertThatCode(() -> assertThat(mtlsTrustManager.getAcceptedIssuers()).isSameAs(expected))
                .doesNotThrowAnyException();
    }
}
