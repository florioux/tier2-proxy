package eu.europa.ec.simpl.tier2proxy;

import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient;
import eu.europa.ec.simpl.tier2proxy.authprovider.CredentialHolder;
import eu.europa.ec.simpl.tier2proxy.dto.KeypairDTO;
import io.netty.bootstrap.Bootstrap;
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
        final AuthProviderClient authProviderClient = mock(AuthProviderClient.class);
        final KeypairDTO keypairDTO = mock(KeypairDTO.class);
        final CredentialHolder credentialHolder = mock(CredentialHolder.class);

        when(authProviderClient.getCredential()).thenReturn(new byte[] {1, 2, 3});
        when(authProviderClient.getInstalledKeypair()).thenReturn(keypairDTO);
        when(keypairDTO.getPrivateKey()).thenReturn(new byte[] {4, 5, 6});

        final Proxy proxy = new Proxy();
        proxy.initializeSimplCredentials(authProviderClient, credentialHolder);

        verify(credentialHolder).initCredentials(new byte[] {1, 2, 3}, new byte[] {4, 5, 6});
    }

    public class Proxy {
        public void initializeSimplCredentials(AuthProviderClient client, CredentialHolder holder) {
            byte[] credential = client.getCredential();
            byte[] privateKey = client.getInstalledKeypair().getPrivateKey();
            holder.initCredentials(credential, privateKey);
        }
    }
}
