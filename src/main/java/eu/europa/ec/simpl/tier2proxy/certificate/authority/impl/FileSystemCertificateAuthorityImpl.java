package eu.europa.ec.simpl.tier2proxy.certificate.authority.impl;

import eu.europa.ec.simpl.tier2proxy.certificate.CertificateInfo;
import eu.europa.ec.simpl.tier2proxy.certificate.Certificates;
import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class FileSystemCertificateAuthorityImpl implements CertificateAuthorityRepository {

    private final Path certificatesFolder;

    public FileSystemCertificateAuthorityImpl(Path certificatesFolder) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("using filesystem certificate authority repository using {} as root folder", certificatesFolder);
        }
        this.certificatesFolder = certificatesFolder;

        if (!certificatesFolder.toFile().exists()) {
            Files.createDirectory(certificatesFolder);
        }
    }

    @Override
    public CertificateInfo getStoredCertificate(String host) throws NoSuchElementException {
        if (log.isDebugEnabled()) {
            log.debug("retrieving certificate information from {}", host);
        }
        var certificateSubFolder = this.certificatesFolder.resolve(host);
        if (!certificateSubFolder.toFile().exists()) {
            throw new NoSuchElementException(host + " certificate not found (folder is missing)");
        }

        var certificatePem = certificateSubFolder.resolve("certificate.pem");
        var privateKeyPem = certificateSubFolder.resolve("private-key.pem");

        X509Certificate certificate;
        PrivateKey privateKey;
        try {
            var bytes = Files.readAllBytes(certificatePem);
            certificate = Certificates.certificateFromPem(bytes);
        } catch (IOException | CertificateException e) {
            throw new NoSuchElementException(host + " certificate not found (certificate pem file)", e);
        }

        try {
            var bytes = Files.readAllBytes(privateKeyPem);

            privateKey = Certificates.privateKeyFromPem(bytes);
        } catch (IOException e) {
            throw new NoSuchElementException(host + " certificate not found (private key pem file)", e);
        }

        return new CertificateInfo(privateKey, certificate);
    }

    @Override
    public void storeCertificate(String host, CertificateInfo certificateInfo) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("storing certificate information for {}", host);
        }
        var certificateSubFolder = this.certificatesFolder.resolve(host);
        if (!certificateSubFolder.toFile().exists()) {
            Files.createDirectory(certificateSubFolder);
        } else {

            return;
        }

        var certificatePem = certificateSubFolder.resolve("certificate.pem");
        var privateKeyPem = certificateSubFolder.resolve("private-key.pem");

        Files.write(certificatePem, Certificates.toPem(certificateInfo.certificate()));
        Files.write(privateKeyPem, Certificates.toPem(certificateInfo.privateKey()));
    }
}
