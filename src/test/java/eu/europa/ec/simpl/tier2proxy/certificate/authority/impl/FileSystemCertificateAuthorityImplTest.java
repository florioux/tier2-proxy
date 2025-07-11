package eu.europa.ec.simpl.tier2proxy.certificate.authority.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileSystemCertificateAuthorityImplTest {

    @TempDir
    Path tempDir;

    @Test
    void testGetStoredCertificateThrowsWhenFolderMissing() throws IOException {
        var repo = new FileSystemCertificateAuthorityImpl(tempDir);
        var host = "testhost";
        assertThrows(NoSuchElementException.class, () -> repo.getStoredCertificate(host));
    }

    @Test
    void testStoreCertificateCreatesFolderAndWritesFiles() throws IOException {
        var repo = new FileSystemCertificateAuthorityImpl(tempDir);
        var host = "testhost";
        var certInfo = mock(CertificateInfo.class);
        var mockCert = mock(X509Certificate.class);
        var mockKey = mock(PrivateKey.class);
        when(certInfo.certificate()).thenReturn(mockCert);
        when(certInfo.privateKey()).thenReturn(mockKey);

        try (MockedStatic<Certificates> certificatesMock = mockStatic(Certificates.class)) {
            certificatesMock.when(() -> Certificates.toPem(mockCert)).thenReturn("CERT".getBytes());
            certificatesMock.when(() -> Certificates.toPem(mockKey)).thenReturn("KEY".getBytes());
            assertDoesNotThrow(() -> repo.storeCertificate(host, certInfo));
        }
    }
}
