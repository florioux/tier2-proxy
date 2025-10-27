package eu.europa.ec.simpl.tier2proxy;

import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.authprovider.CredentialHolder;
import io.netty.bootstrap.Bootstrap;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyTest {
    @Mock
    Bootstrap bootstrap;

    @Test
    void testInitializeSimplCredentials() {
        var authProviderClient = mock(AuthProviderClient.class);
        var privateKey = mock(PrivateKey.class);
        var chain = List.of(mock(X509Certificate.class), mock(X509Certificate.class));
        var credentialHolder = mock(CredentialHolder.class);

        when(authProviderClient.getCredential()).thenReturn(chain);
        when(authProviderClient.loadPrivateKey()).thenReturn(Optional.of(privateKey));

        final Proxy proxy = new Proxy();
        proxy.initializeSimplCredentials(authProviderClient, credentialHolder);

        verify(credentialHolder).initCredentials(chain, privateKey);
    }

    public class Proxy {
        public void initializeSimplCredentials(AuthProviderClient client, CredentialHolder holder) {
            var credential = client.getCredential();
            var privateKey = client.loadPrivateKey();
            holder.initCredentials(credential, privateKey.orElseThrow());
        }
    }
}
