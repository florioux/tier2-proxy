package eu.europa.ec.simpl.tier2proxy.authprovider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.util.CredentialUtil;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialHolderTest {

    @Test
    void testGetInstanceSingleton() {
        var instance1 = CredentialHolder.getInstance();
        var instance2 = CredentialHolder.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    void testInitCredentialsSuccess() {
        var privateKey = mock(PrivateKey.class);
        var chain = List.of(mock(X509Certificate.class), mock(X509Certificate.class));

        var keyStoreMock = mock(KeyStore.class);
        var trustStoreMock = mock(KeyStore.class);

        // Mock static methods
        try (var credentialUtilMocked = mockStatic(CredentialUtil.class)) {

            credentialUtilMocked
                    .when(() -> CredentialUtil.loadCredential(anyList(), eq(privateKey)))
                    .thenReturn(keyStoreMock);
            credentialUtilMocked
                    .when(() -> CredentialUtil.buildTrustStore(keyStoreMock))
                    .thenReturn(trustStoreMock);

            var holder = CredentialHolder.getInstance();
            holder.initCredentials(chain, privateKey);
            assertEquals(keyStoreMock, holder.getKeyStore());
            assertEquals(trustStoreMock, holder.getTrustStore());
        }
    }

    @Test
    void testInitCredentialsThrowsOnNullChain() {
        var holder = CredentialHolder.getInstance();
        var ex = assertThrows(IllegalStateException.class, () -> holder.initCredentials(null, mock(PrivateKey.class)));
        assertEquals("Credential is not set", ex.getMessage());
    }

    @Test
    void testInitCredentialsThrowsOnEmptyChain() {
        var holder = CredentialHolder.getInstance();
        var ex = assertThrows(
                IllegalStateException.class, () -> holder.initCredentials(List.of(), mock(PrivateKey.class)));
        assertEquals("Credential is not set", ex.getMessage());
    }

    @Test
    void testInitCredentialsThrowsOnNullPrivateKey() {
        var holder = CredentialHolder.getInstance();
        var ex = assertThrows(
                IllegalStateException.class, () -> holder.initCredentials(List.of(mock(X509Certificate.class)), null));
        assertEquals("Credential is not set", ex.getMessage());
    }
}
