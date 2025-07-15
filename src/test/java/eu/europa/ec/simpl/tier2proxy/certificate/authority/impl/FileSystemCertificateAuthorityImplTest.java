package eu.europa.ec.simpl.tier2proxy.certificate.authority.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSystemCertificateAuthorityImplTest {

    @TempDir
    Path tempDir;

    FileSystemCertificateAuthorityImpl repo;

    @BeforeEach
    void setUp() throws IOException {
        repo = new FileSystemCertificateAuthorityImpl(tempDir);
    }

    @Test
    void shouldThrowWhenFolderIsMissing() {
        var host = "missinghost";

        assertThatThrownBy(() -> repo.getStoredCertificate(host))
                .as("Should throw when certificate folder is missing")
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(host);
    }

    @Test
    void shouldStoreCertificateCreatingFolderAndWritingFiles() throws IOException {
        var host = "newhost";
        var mockCert = mock(X509Certificate.class);
        var mockKey = mock(PrivateKey.class);
        var certInfo = mock(CertificateInfo.class);

        given(certInfo.certificate()).willReturn(mockCert);
        given(certInfo.privateKey()).willReturn(mockKey);

        try (MockedStatic<Certificates> certificatesMock = mockStatic(Certificates.class)) {
            certificatesMock.when(() -> Certificates.toPem(mockCert)).thenReturn("CERT".getBytes());
            certificatesMock.when(() -> Certificates.toPem(mockKey)).thenReturn("KEY".getBytes());

            repo.storeCertificate(host, certInfo);

            var certPath = tempDir.resolve(host).resolve("certificate.pem");
            var keyPath = tempDir.resolve(host).resolve("private-key.pem");

            assertThat(Files.exists(certPath))
                    .as("Certificate file should exist after storing")
                    .isTrue();

            assertThat(Files.exists(keyPath))
                    .as("Private key file should exist after storing")
                    .isTrue();
        }
    }

    @Test
    void shouldSkipStoreIfFolderExists() throws IOException {
        var host = "existinghost";
        var certInfo = mock(CertificateInfo.class);
        var hostDir = tempDir.resolve(host);
        Files.createDirectory(hostDir);

        repo.storeCertificate(host, certInfo);

        var certPath = hostDir.resolve("certificate.pem");
        var keyPath = hostDir.resolve("private-key.pem");

        assertThat(Files.exists(certPath))
                .as("Certificate file should not be created if folder already exists")
                .isFalse();

        assertThat(Files.exists(keyPath))
                .as("Private key file should not be created if folder already exists")
                .isFalse();
    }

    @Test
    void shouldReadStoredCertificateSuccessfully() throws Exception {
        var host = "validhost";
        var hostDir = tempDir.resolve(host);
        Files.createDirectory(hostDir);

        var certBytes = "CERTDATA".getBytes();
        var keyBytes = "KEYDATA".getBytes();

        Files.write(hostDir.resolve("certificate.pem"), certBytes);
        Files.write(hostDir.resolve("private-key.pem"), keyBytes);

        var mockCert = mock(X509Certificate.class);
        var mockKey = mock(PrivateKey.class);

        try (MockedStatic<Certificates> certsMock = mockStatic(Certificates.class)) {
            certsMock.when(() -> Certificates.certificateFromPem(certBytes)).thenReturn(mockCert);
            certsMock.when(() -> Certificates.privateKeyFromPem(keyBytes)).thenReturn(mockKey);

            var result = repo.getStoredCertificate(host);

            assertThat(result.certificate())
                    .as("Should return the correct certificate")
                    .isEqualTo(mockCert);

            assertThat(result.privateKey())
                    .as("Should return the correct private key")
                    .isEqualTo(mockKey);
        }
    }

    @Test
    void shouldThrowWhenReadingCertificateFails() throws Exception {
        var host = "certfail";
        var hostDir = tempDir.resolve(host);
        Files.createDirectory(hostDir);
        Files.write(hostDir.resolve("certificate.pem"), "BADCERT".getBytes());
        Files.write(hostDir.resolve("private-key.pem"), "VALIDKEY".getBytes());

        try (MockedStatic<Certificates> certsMock = mockStatic(Certificates.class)) {
            certsMock
                    .when(() -> Certificates.certificateFromPem(any()))
                    .thenThrow(new CertificateException("invalid cert"));

            assertThatThrownBy(() -> repo.getStoredCertificate(host))
                    .as("Should throw if reading certificate fails")
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(host)
                    .hasCauseInstanceOf(CertificateException.class);
        }
    }

    @Test
    void shouldThrowWhenReadingPrivateKeyFails() throws Exception {
        var host = "keyfail";
        var hostDir = tempDir.resolve(host);
        Files.createDirectory(hostDir);
        Files.write(hostDir.resolve("certificate.pem"), "VALIDCERT".getBytes());
        Files.write(hostDir.resolve("private-key.pem"), "BADKEY".getBytes());

        var mockCert = mock(X509Certificate.class);

        try (MockedStatic<Certificates> certsMock = mockStatic(Certificates.class)) {
            certsMock.when(() -> Certificates.certificateFromPem(any())).thenReturn(mockCert);
            certsMock.when(() -> Certificates.privateKeyFromPem(any())).thenThrow(new IOException("key read error"));

            assertThatThrownBy(() -> repo.getStoredCertificate(host))
                    .as("Should throw if reading private key fails")
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining(host)
                    .hasCauseInstanceOf(IOException.class);
        }
    }
}
