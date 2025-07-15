package eu.europa.ec.simpl.tier2proxy.authprovider;

import eu.europa.ec.simpl.tier2proxy.util.CredentialUtil;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public final class CredentialHolder {

    private KeyStore keyStore;
    private KeyStore trustStore;

    private CredentialHolder() {}

    @SneakyThrows
    public void initCredentials(byte[] credential, byte[] privateKey) {
        if (credential == null || privateKey == null) {
            throw new IllegalStateException("Credential is not set");
        }
        var pk = CredentialUtil.loadPrivateKey(privateKey);
        this.keyStore = CredentialUtil.loadCredential(new ByteArrayInputStream(credential), pk);
        this.trustStore = CredentialUtil.buildTrustStore(this.keyStore);
    }

    public static CredentialHolder getInstance() {
        return CredentialHolderInstanceHolder.INSTANCE;
    }

    private static class CredentialHolderInstanceHolder {
        private static final CredentialHolder INSTANCE = new CredentialHolder();
    }
}
