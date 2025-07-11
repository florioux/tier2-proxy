package eu.europa.ec.simpl.tier2proxy.certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CertificateFactoryTest {

    private static final String TEST_HOST = "test.example.com";
    private static final String CA_KEY = "_ca";
    private static final String SIGNATURE_ALGO = "SHA256withRSA";

    @Mock
    private CertificateAuthorityRepository certificateAuthorityRepository;

    @Mock
    private KeyPairGenerator keyPairGenerator;

    @Mock
    private KeyPair keyPair;

    @Mock
    private PrivateKey privateKey;

    @Mock
    private X509Certificate certificate;

    @Mock
    private X500Principal x500Principal;

    private CertificateFactory certificateFactory;
    private X500Name caSubject;
    private CertificateOptions.PrivateKey privateKeyOptions;
    private CertificateOptions.CertificateValidity certificateValidity;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        caSubject = new X500Name("CN=Test CA");

        RSAKeyGenParameterSpec rsaSpec = new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4);

        privateKeyOptions = new CertificateOptions.PrivateKey("RSA", rsaSpec, new SecureRandom());

        certificateValidity = new CertificateOptions.CertificateValidity(1, 1, ChronoUnit.DAYS, ChronoUnit.YEARS);

        lenient().when(certificate.getSubjectX500Principal()).thenReturn(x500Principal);
        lenient().when(x500Principal.getName()).thenReturn("CN=Test CA");

        certificateFactory = new CertificateFactory(
                caSubject, privateKeyOptions, certificateValidity, SIGNATURE_ALGO, certificateAuthorityRepository);
    }

    @Test
    void testGetCACertificateShouldReturnStoredCertificateWhenExists() {
        // Given
        var expectedCertInfo = new CertificateInfo(privateKey, certificate);
        when(certificateAuthorityRepository.getStoredCertificate(CA_KEY)).thenReturn(expectedCertInfo);

        // When
        var result = certificateFactory.getCACertificate();

        // Then
        assertThat(result).isEqualTo(expectedCertInfo);
        verify(certificateAuthorityRepository).getStoredCertificate(CA_KEY);
    }

    @Test
    void testGetCACertificateShouldCreateNewCertificateWhenNotExists() throws IOException {
        // Given
        when(certificateAuthorityRepository.getStoredCertificate(CA_KEY))
                .thenThrow(new NoSuchElementException("Certificate not found"));

        // When
        var result = certificateFactory.getCACertificate();

        // Then
        verify(certificateAuthorityRepository).getStoredCertificate(CA_KEY);
        verify(certificateAuthorityRepository).storeCertificate(eq(CA_KEY), any(CertificateInfo.class));

        assertThat(result).isNotNull();
    }

    @Test
    void testGetCACertificateShouldThrowExceptionWhenCreationFails() throws IOException {
        // Given
        when(certificateAuthorityRepository.getStoredCertificate(CA_KEY))
                .thenThrow(new NoSuchElementException("Certificate not found"));

        doThrow(new IOException("Storage error"))
                .when(certificateAuthorityRepository)
                .storeCertificate(eq(CA_KEY), any(CertificateInfo.class));

        // When/Then
        assertThatThrownBy(() -> certificateFactory.getCACertificate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("certificate authority generation in error");
    }

    @Test
    void testGetCertificateShouldReturnStoredCertificateWhenExists() throws IOException {
        // Given
        var caCertInfo = new CertificateInfo(privateKey, certificate);
        var expectedCertInfo = new CertificateInfo(privateKey, certificate);

        when(certificateAuthorityRepository.getStoredCertificate(TEST_HOST)).thenReturn(expectedCertInfo);

        // When
        var result = certificateFactory.getCertificate(caCertInfo, TEST_HOST);

        // Then
        assertThat(result).isEqualTo(expectedCertInfo);
        verify(certificateAuthorityRepository).getStoredCertificate(TEST_HOST);
        verify(certificateAuthorityRepository, never()).storeCertificate(anyString(), any(CertificateInfo.class));
    }

    @Test
    void testConstructorShouldThrowExceptionWhenInvalidAlgorithm() {
        // Given
        var invalidPrivateKeyOptions = new CertificateOptions.PrivateKey("INVALID_ALGORITHM", null, null);

        // When/Then
        assertThatThrownBy(() -> new CertificateFactory(
                        caSubject,
                        invalidPrivateKeyOptions,
                        certificateValidity,
                        SIGNATURE_ALGO,
                        certificateAuthorityRepository))
                .isInstanceOf(NoSuchAlgorithmException.class);
    }
}
