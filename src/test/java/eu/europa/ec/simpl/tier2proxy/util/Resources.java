package eu.europa.ec.simpl.tier2proxy.util;

import eu.europa.ec.simpl.util.CredentialUtil;
import eu.europa.ec.simpl.util.PemString;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.instancio.Instancio;

@UtilityClass
public class Resources {

    @UtilityClass
    public static class P12 {
        public static final P12Resource KEYSTORE = new P12Resource("p12/keystore.p12.sample");
    }

    @UtilityClass
    public static class Pem {

        private static final List<PemResource> pems = new ArrayList<>();

        public static final PemResource CERTIFICATE = add("pem/certificate.pem.sample");
        public static final PemResource CERTIFICATE_CHAIN = add("pem/certificateChain.pem.sample");
        public static final PemResource PRIVATE_KEY = add("pem/privateKey.pem.sample");
        public static final PemResource PUBLIC_KEY = add("pem/publicKey.pem.sample");
        public static final PemResource CERTIFICATE_SIGN_REQUEST = add("pem/csr.pem.sample");

        private static PemResource add(String resource) {
            var pem = new PemResource(resource);
            pems.add(pem);
            return pem;
        }

        public static PemResource random() {
            return pems.get(Instancio.gen().ints().min(0).max(pems.size() - 1).get());
        }
    }

    @RequiredArgsConstructor
    public static class PemResource {
        private final String resource;

        public PemString load() {
            return PemString.of(loadResourceAsString(resource));
        }
    }

    @RequiredArgsConstructor
    public static class P12Resource {
        private final String resource;

        public byte[] load() {
            return loadResource(resource);
        }

        @SneakyThrows
        public KeyStore loadKeyStore() {
            var ks = KeyStore.getInstance("PKCS12");
            var is = loadResourceAsStream(resource);
            CredentialUtil.tryLoad(ks, is);
            return ks;
        }
    }

    @RequiredArgsConstructor
    public static class TextFileResource {
        private final String resource;

        public String load() {
            return loadResourceAsString(resource);
        }
    }

    private static String loadResourceAsString(String resource) {
        return new String(loadResource(resource));
    }

    @SneakyThrows
    private static InputStream loadResourceAsStream(String resource) {
        return ClassLoader.getSystemClassLoader().getResourceAsStream(resource);
    }

    @SneakyThrows
    private static byte[] loadResource(String resource) {
        return loadResourceAsStream(resource).readAllBytes();
    }
}
