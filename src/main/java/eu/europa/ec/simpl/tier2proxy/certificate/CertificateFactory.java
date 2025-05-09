package eu.europa.ec.simpl.tier2proxy.certificate;

import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

@Slf4j
final class CertificateFactory {
    private static final String CA_KEY = "_ca";
    private final X500Name caSubject;

    private final ValidityPeriod certificateValidityPeriod;

    private final CertificateAuthorityRepository certificateAuthorityRepository;
    private final KeyPairGenerator keyGen;
    private final JcaX509CertificateConverter certificateConverter;
    private final JcaContentSignerBuilder contentSignerBuilder;

    CertificateFactory(
            X500Name caSubject,
            CertificateOptions.PrivateKey pk,
            CertificateOptions.CertificateValidity cv,
            String signatureAlgo,
            CertificateAuthorityRepository certificateAuthorityRepository)
            throws NoSuchAlgorithmException {
        this.caSubject = caSubject;
        this.certificateValidityPeriod = cv.of();

        this.certificateAuthorityRepository = certificateAuthorityRepository;
        this.keyGen = pk.of();

        this.certificateConverter = new JcaX509CertificateConverter();
        this.contentSignerBuilder = new JcaContentSignerBuilder(signatureAlgo);
    }

    CertificateInfo getCACertificate() {
        if (log.isInfoEnabled()) {
            log.info("certificate authority certificate for {}", this.caSubject);
        }
        try {

            return this.certificateAuthorityRepository.getStoredCertificate(CA_KEY);
        } catch (NoSuchElementException exception) {
            if (log.isDebugEnabled()) {
                log.debug("no certificate authority certificate present, generate it", exception);
            }
        }

        try {

            return this.createCACertificate();
        } catch (CertificateException | OperatorCreationException | IOException exception) {
            if (log.isErrorEnabled()) {
                log.error("certificate authority generation in error", exception);
            }

            throw new IllegalStateException("certificate authority generation in error", exception);
        }
    }

    CertificateInfo getCertificate(CertificateInfo caCertificate, String host) {
        if (log.isDebugEnabled()) {
            log.debug("getting certificate information for {}", host);
        }
        try {

            return this.certificateAuthorityRepository.getStoredCertificate(host);
        } catch (NoSuchElementException exception) {
            if (log.isInfoEnabled()) {
                log.info("no certificate for {} present, generate it", host);
            }
        }

        try {

            return this.createCertificate(caCertificate, host);
        } catch (CertificateException | OperatorCreationException | IOException exception) {
            if (log.isErrorEnabled()) {
                log.error("certificate generation for {} in error", host, exception);
            }
            throw new IllegalStateException(String.format("certificate generation %s in error", host), exception);
        }
    }

    private static BigInteger serial() {
        return new BigInteger(Long.toString(System.currentTimeMillis() / 1000));
    }

    private static X500Name getSubject(String host) {
        return new X500Name("CN=" + host);
    }

    private CertificateInfo createCertificate(CertificateInfo caCertificateInfo, String host)
            throws IOException, OperatorCreationException, CertificateException {
        if (log.isDebugEnabled()) {
            log.debug("creating certificate for {} given {} as certificate authority", host, caCertificateInfo);
        }
        X509Certificate caCertificate = caCertificateInfo.certificate();
        PrivateKey caPrivateKey = caCertificateInfo.privateKey();

        X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());
        BigInteger serial = serial();
        var certificateValidityPeriod = this.certificateValidityPeriod.from();
        X500Name subject = getSubject(host);
        KeyPair keyPair = this.keyGen.generateKeyPair();

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                certificateValidityPeriod.before(),
                certificateValidityPeriod.after(),
                subject,
                keyPair.getPublic());

        GeneralNames generalNames =
                GeneralNames.getInstance(new DERSequence(new GeneralName(GeneralName.dNSName, host)));
        certBuilder.addExtension(Extension.subjectAlternativeName, true, generalNames);
        certBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        certBuilder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        certBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(new KeyPurposeId[] {
            KeyPurposeId.id_kp_serverAuth
        }));

        var signer = this.contentSignerBuilder.build(caPrivateKey);

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate certificate = this.certificateConverter.getCertificate(certHolder);

        CertificateInfo certInfo = new CertificateInfo(keyPair.getPrivate(), certificate);
        this.certificateAuthorityRepository.storeCertificate(host, certInfo);
        return certInfo;
    }

    private CertificateInfo createCACertificate() throws CertificateException, OperatorCreationException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("creating certificate authority certificate");
        }
        KeyPair keyPair = this.keyGen.generateKeyPair();

        var certificateValidityPeriod = this.certificateValidityPeriod.from();

        var serial = serial();
        PrivateKey privateKey = keyPair.getPrivate();

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                caSubject,
                serial,
                certificateValidityPeriod.before(),
                certificateValidityPeriod.after(),
                caSubject,
                keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = this.contentSignerBuilder.build(privateKey);

        X509CertificateHolder certHolder = certBuilder.build(signer);
        X509Certificate caCert = this.certificateConverter.getCertificate(certHolder);

        CertificateInfo certInfo = new CertificateInfo(privateKey, caCert);
        this.certificateAuthorityRepository.storeCertificate(CA_KEY, certInfo);
        return certInfo;
    }
}
