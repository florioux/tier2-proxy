package eu.europa.ec.simpl.tier2proxy.authprovider;

import static eu.europa.ec.simpl.tier2proxy.proxy.handler.MitmHandler.PARTICIPANT_EPHEMERAL_PROOF_TARGET_V1;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import eu.europa.ec.simpl.tier2proxy.configurations.SimplProperties;
import eu.europa.ec.simpl.tier2proxy.dto.KeypairDTO;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HTTPClient;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthProviderClientTest {

    @Mock
    private Bootstrap bootstrap;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Configuration configuration;

    private AuthProviderClient authProviderClient;

    @BeforeEach
    void setUp() {
        authProviderClient = new AuthProviderClient(bootstrap);
    }

    @Test
    void testGetCredentialSuccess() throws Exception {
        var url = "http://test/credentials";
        var response = "credential-response";
        var properties = mock(SimplProperties.class);
        var authProvider = mock(SimplProperties.AuthenticationProviderProperties.class);
        var request = mock(HttpRequest.class);
        try (MockedConstruction<HTTPClient> mocked = Mockito.mockConstruction(HTTPClient.class, (mock, context) -> {
            doNothing().when(mock).call(any(HttpRequest.class));
            when(mock.getResponseFuture()).thenReturn(CompletableFuture.completedFuture(response));
        })) {
            var result = authProviderClient.getCredential();
            assertNotNull(result);
            assertArrayEquals(response.getBytes(StandardCharsets.UTF_8), result);
        }
    }

    @Test
    void testGetCredentialException() throws Exception {
        var url = "http://test/credentials";
        var properties = mock(SimplProperties.class);
        var authProvider = mock(SimplProperties.AuthenticationProviderProperties.class);
        var request = mock(HttpRequest.class);
        try (MockedConstruction<HTTPClient> mocked = Mockito.mockConstruction(HTTPClient.class, (mock, context) -> {
            doNothing().when(mock).call(any(HttpRequest.class));
            when(mock.getResponseFuture()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("fail")));
        })) {
            var result = authProviderClient.getCredential();
            assertNull(result);
        }
    }

    @Test
    void testGetInstalledKeypairSuccess() throws Exception {
        var url = "http://test/keypairs";
        var response = "keypair-json";
        var keypairDTO = mock(KeypairDTO.class);
        var properties = mock(SimplProperties.class);
        var authProvider = mock(SimplProperties.AuthenticationProviderProperties.class);
        var request = mock(HttpRequest.class);
        try (MockedConstruction<HTTPClient> mocked = Mockito.mockConstruction(HTTPClient.class, (mock, context) -> {
            doNothing().when(mock).call(any(HttpRequest.class));
            when(mock.getResponseFuture()).thenReturn(CompletableFuture.completedFuture(response));
        })) {
            // mock objectMapper static field via reflection
            var objectMapperField = AuthProviderClient.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            objectMapperField.set(authProviderClient, objectMapper);
            when(objectMapper.readValue(response, KeypairDTO.class)).thenReturn(keypairDTO);
            var result = authProviderClient.getInstalledKeypair();
            assertNotNull(result);
            assertEquals(keypairDTO, result);
        }
    }

    @Test
    void testGetInstalledKeypairException() throws Exception {
        var url = "http://test/keypairs";
        var request = mock(HttpRequest.class);
        try (MockedConstruction<HTTPClient> mocked = Mockito.mockConstruction(HTTPClient.class, (mock, context) -> {
            doNothing().when(mock).call(any(HttpRequest.class));
            when(mock.getResponseFuture()).thenReturn(CompletableFuture.completedFuture("bad-json"));
        })) {
            var objectMapperField = AuthProviderClient.class.getDeclaredField("objectMapper");
            objectMapperField.setAccessible(true);
            objectMapperField.set(authProviderClient, objectMapper);
            when(objectMapper.readValue(anyString(), eq(KeypairDTO.class))).thenThrow(new RuntimeException("fail"));

            var result = authProviderClient.getInstalledKeypair();
            assertEquals(KeypairDTO.class, result.getClass());
            assertNull(result.getPublicKey());
            assertNull(result.getPrivateKey());
        }
    }

    @Test
    void shouldRetrieveEphemeralProofAndSendItToDestination() {
        // Given
        var dummyProof = "ephemeral-proof";
        var destination = "some-destination.com";

        try (var mocked = mockConstruction(HTTPClient.class, (mock, context) -> {
            given(mock.getResponseFuture()).willReturn(CompletableFuture.completedFuture(dummyProof));
            willDoNothing().given(mock).call(any(HttpRequest.class));
        })) {
            // When
            authProviderClient.getEphemeralProofAndSendToDest(destination).join();

            // Then
            then(mocked.constructed().get(0)).should().call(any());
            then(mocked.constructed().get(1))
                    .should()
                    .call(argThat(req -> Objects.equals(req.getMethod(), HttpMethod.POST)
                            && Objects.equals(
                                    req.getUrl().toString(),
                                    "https://%s%s".formatted(destination, PARTICIPANT_EPHEMERAL_PROOF_TARGET_V1))
                            && Objects.equals(dummyProof, req.getBody())));
        }
    }
}
