package eu.europa.ec.simpl.tier2proxy.util;

import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

@Slf4j
@UtilityClass
public class CredentialUtil {

    private static final Configuration configuration = Configuration.getInstance();

    public static KeyStore loadCredential(InputStream input, PrivateKey privateKey) {
        log.debug("Start loading credential");
        try {
            var keyStore = KeyStore.getInstance("PKCS12");
            var chain = parseCertificates(input);
            verifyPrivateKeyMatchesCertificateChain(privateKey, chain.getFirst());
            keyStore.load(null, null);
            keyStore.setKeyEntry("chain", privateKey, null, chain.toArray(X509Certificate[]::new));
            return keyStore;
        } catch (KeyStoreException
                | IOException
                | NoSuchAlgorithmException
                | CertificateException
                | SignatureException
                | InvalidKeyException e) {
            log.error("Failed to load credential");
            throw new RuntimeException(e);
        }
    }

    public static KeyStore buildTrustStore(KeyStore keyStore)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        var alias = keyStore.aliases().nextElement();
        var chain = Arrays.stream(keyStore.getCertificateChain(alias))
                .map(X509Certificate.class::cast)
                .collect(Collectors.toCollection(LinkedList::new));
        chain.removeFirst();
        var trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        chain.forEach(cert -> setCertificateEntry(trustStore, cert));
        return trustStore;
    }

    private static void verifyPrivateKeyMatchesCertificateChain(PrivateKey privateKey, X509Certificate certificate)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        byte[] testData = "test".getBytes();

        Signature signature = Signature.getInstance(certificate.getSigAlgName());
        signature.initSign(privateKey);
        signature.update(testData);
        byte[] signedData = signature.sign();

        signature.initVerify(certificate.getPublicKey());

        signature.update(testData);
        if (!signature.verify(signedData)) {
            throw new SignatureException("Private Key sign doesn't match the certificate chain");
        }
    }

    @SneakyThrows
    private static void setCertificateEntry(KeyStore keyStore, X509Certificate cert) {
        keyStore.setCertificateEntry(cert.getSubjectX500Principal().getName(), cert);
    }

    @SneakyThrows
    private static List<X509Certificate> parseCertificates(InputStream input) {
        var pems = PemUtil.loadPemObjects(input);
        return pems.stream().map(CredentialUtil::generateCertificate).toList();
    }

    @SneakyThrows
    public static X509Certificate generateCertificate(InputStream inputStream) {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certFactory.generateCertificate(inputStream);
    }

    public static X509Certificate extractCertificateFromKeystore(KeyStore keyStore) {
        log.debug("Extracting certificate from keystore");
        try {
            var alias = keyStore.aliases().nextElement();
            return (X509Certificate) keyStore.getCertificateChain(alias)[0];
        } catch (KeyStoreException e) {
            log.error("Failed to extract certificate");
            throw new RuntimeException(e);
        }
    }

    public static ECPublicKey extractPublicKeyFromKeystore(KeyStore keyStore) {
        log.debug("Extracting public key from certificate");
        return (ECPublicKey) extractCertificateFromKeystore(keyStore).getPublicKey();
    }

    public static ECPrivateKey extractPrivateKeyFromKeystore(KeyStore keyStore, String keyEntryPassword) {
        log.debug("Extracting private key from certificate");
        try {
            var alias = keyStore.aliases().nextElement();
            return (ECPrivateKey) keyStore.getKey(alias, keyEntryPassword.toCharArray());
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
            log.error("Failed to extract private key");
            throw new RuntimeException(e);
        }
    }

    public static String getIdentifierFromSubject(X509Certificate cert, ASN1ObjectIdentifier identifier) {
        try {
            var x500name = new JcaX509CertificateHolder(cert).getSubject();
            return getIdentifierFromSubject(x500name, identifier);
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getIdentifierFromSubject(X500Name x500Name, ASN1ObjectIdentifier identifier) {
        var cn = x500Name.getRDNs(identifier)[0];
        return IETFUtils.valueToString(cn.getFirst().getValue());
    }

    @SneakyThrows
    public static PrivateKey loadPrivateKey(byte[] encoded) {
        var keySpec = new PKCS8EncodedKeySpec(encoded);
        var algorithm = configuration
                .getCertificateServerOptions()
                .certificateOptions()
                .privateKey()
                .algo();
        var keyFactory = KeyFactory.getInstance(algorithm);
        return keyFactory.generatePrivate(keySpec);
    }
}
