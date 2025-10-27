package eu.europa.ec.simpl.tier2proxy.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class CredentialUtil {

    public static KeyStore loadCredential(List<X509Certificate> chain, PrivateKey privateKey) {
        log.debug("Start loading credential");
        try {
            var keyStore = KeyStore.getInstance("PKCS12");
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
        byte[] testData = "test".getBytes(Charset.defaultCharset());

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
}
