package eu.europa.ec.simpl.tier2proxy.certificate;

import eu.europa.ec.simpl.tier2proxy.certificate.authority.CertificateAuthorityRepository;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

@Slf4j
final class CertificateFactory {
    private static final String CA_KEY = "_ca";
    public static final int MILLIS_IN_SECOND = 1000;
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
        log.info("certificate authority certificate for {}", this.caSubject);
        try {
            return this.certificateAuthorityRepository.getStoredCertificate(CA_KEY);
        } catch (NoSuchElementException exception) {
            log.debug("no certificate authority certificate present, generate it - {}", exception.getMessage());
        }

        try {

            return this.createCACertificate();
        } catch (CertificateException | OperatorCreationException | IOException exception) {
            log.error("certificate authority generation in error", exception);
            throw new IllegalStateException("certificate authority generation in error", exception);
        }
    }

    CertificateInfo getCertificate(CertificateInfo caCertificate, String host) {
        log.debug("getting certificate information for {}", host);
        try {
            return this.certificateAuthorityRepository.getStoredCertificate(host);
        } catch (NoSuchElementException ignored) {
            log.info("no certificate for {} present, generate it", host);
        }

        try {

            return this.createCertificate(caCertificate, host);
        } catch (CertificateException | OperatorCreationException | IOException exception) {
            log.error("certificate generation for {} in error", host, exception);
            throw new IllegalStateException(String.format("certificate generation %s in error", host), exception);
        }
    }

    private static BigInteger serial() {
        return new BigInteger(Long.toString(System.currentTimeMillis() / MILLIS_IN_SECOND));
    }

    private static X500Name getSubject(String host) {
        return new X500Name("CN=" + host);
    }

    private CertificateInfo createCertificate(CertificateInfo caCertificateInfo, String host)
            throws IOException, OperatorCreationException, CertificateException {
        log.debug("creating certificate for {} given {} as certificate authority", host, caCertificateInfo);
        var caPrivateKey = caCertificateInfo.privateKey();

        var issuer = Configuration.getInstance()
                .getCertificateServerOptions()
                .certificateOptions()
                .caSubject();
        var serial = serial();
        var certValidityPeriod = this.certificateValidityPeriod.from();
        var subject = getSubject(host);
        var keyPair = this.keyGen.generateKeyPair();

        var certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, certValidityPeriod.before(), certValidityPeriod.after(), subject, keyPair.getPublic());

        var generalNames = GeneralNames.getInstance(new DERSequence(new GeneralName(GeneralName.dNSName, host)));
        certBuilder.addExtension(Extension.subjectAlternativeName, true, generalNames);
        certBuilder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        certBuilder.addExtension(
                Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        certBuilder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(new KeyPurposeId[] {
            KeyPurposeId.id_kp_serverAuth
        }));

        var signer = this.contentSignerBuilder.build(caPrivateKey);

        var certHolder = certBuilder.build(signer);
        var certificate = this.certificateConverter.getCertificate(certHolder);

        var certInfo = new CertificateInfo(keyPair.getPrivate(), certificate);
        this.certificateAuthorityRepository.storeCertificate(host, certInfo);
        return certInfo;
    }

    private CertificateInfo createCACertificate() throws CertificateException, OperatorCreationException, IOException {
        log.debug("creating certificate authority certificate");
        var keyPair = this.keyGen.generateKeyPair();

        var certValidityPeriod = this.certificateValidityPeriod.from();

        var serial = serial();
        var privateKey = keyPair.getPrivate();

        var certBuilder = new JcaX509v3CertificateBuilder(
                caSubject,
                serial,
                certValidityPeriod.before(),
                certValidityPeriod.after(),
                caSubject,
                keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = this.contentSignerBuilder.build(privateKey);

        var certHolder = certBuilder.build(signer);
        var caCert = this.certificateConverter.getCertificate(certHolder);

        var certInfo = new CertificateInfo(privateKey, caCert);
        this.certificateAuthorityRepository.storeCertificate(CA_KEY, certInfo);
        return certInfo;
    }
}
