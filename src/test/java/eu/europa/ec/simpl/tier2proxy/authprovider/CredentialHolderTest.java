package eu.europa.ec.simpl.tier2proxy.authprovider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import eu.europa.ec.simpl.tier2proxy.util.CredentialUtil;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
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
    void testInitCredentialsSuccess() throws Exception {
        var credential = new byte[] {1, 2, 3};
        var privateKey = new byte[] {4, 5, 6};
        var keyStoreMock = mock(KeyStore.class);
        var trustStoreMock = mock(KeyStore.class);
        var privateKeyObj = mock(PrivateKey.class);

        // Mock static methods
        try (var credentialUtilMocked = mockStatic(CredentialUtil.class)) {
            credentialUtilMocked
                    .when(() -> CredentialUtil.loadPrivateKey(privateKey))
                    .thenReturn(privateKeyObj);
            credentialUtilMocked
                    .when(() -> CredentialUtil.loadCredential(any(ByteArrayInputStream.class), eq(privateKeyObj)))
                    .thenReturn(keyStoreMock);
            credentialUtilMocked
                    .when(() -> CredentialUtil.buildTrustStore(keyStoreMock))
                    .thenReturn(trustStoreMock);

            var holder = CredentialHolder.getInstance();
            holder.initCredentials(credential, privateKey);
            assertEquals(keyStoreMock, holder.getKeyStore());
            assertEquals(trustStoreMock, holder.getTrustStore());
        }
    }

    @Test
    void testInitCredentialsThrowsOnNullCredential() {
        var holder = CredentialHolder.getInstance();
        var ex = assertThrows(IllegalStateException.class, () -> holder.initCredentials(null, new byte[] {1}));
        assertEquals("Credential is not set", ex.getMessage());
    }

    @Test
    void testInitCredentialsThrowsOnNullPrivateKey() {
        var holder = CredentialHolder.getInstance();
        var ex = assertThrows(IllegalStateException.class, () -> holder.initCredentials(new byte[] {1}, null));
        assertEquals("Credential is not set", ex.getMessage());
    }
}
