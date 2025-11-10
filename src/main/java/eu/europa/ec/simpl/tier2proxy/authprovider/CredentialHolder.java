package eu.europa.ec.simpl.tier2proxy.authprovider;

import eu.europa.ec.simpl.tier2proxy.util.CredentialUtil;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public final class CredentialHolder {

    private KeyStore keyStore;
    private KeyStore trustStore;

    private CredentialHolder() {}

    @SneakyThrows
    public void initCredentials(List<X509Certificate> chain, PrivateKey privateKey) {
        if (chain == null || chain.isEmpty() || privateKey == null) {
            throw new IllegalStateException("Credential is not set");
        }
        this.keyStore = CredentialUtil.loadCredential(chain, privateKey);
        this.trustStore = CredentialUtil.buildTrustStore(this.keyStore);
    }

    public static CredentialHolder getInstance() {
        return CredentialHolderInstanceHolder.INSTANCE;
    }

    private static class CredentialHolderInstanceHolder {
        private static final CredentialHolder INSTANCE = new CredentialHolder();
    }
}
