package eu.europa.ec.simpl.tier2proxy.authprovider;

import static eu.europa.ec.simpl.tier2proxy.authprovider.AuthProviderClient.PARTICIPANT_EPHEMERAL_PROOF_TARGET_TIER2_V2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import eu.europa.ec.simpl.tier2proxy.dto.ActiveCredentialDTO;
import eu.europa.ec.simpl.tier2proxy.dto.EphemeralProofDTO;
import eu.europa.ec.simpl.tier2proxy.dto.KeypairDTO;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HTTPClient;
import eu.europa.ec.simpl.tier2proxy.util.Resources;
import eu.europa.ec.simpl.util.PemConverter;
import io.netty.handler.codec.http.HttpMethod;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthProviderClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private HTTPClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PemConverter pemConverter;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration configuration;

    @InjectMocks
    private AuthProviderClient authProviderClient;

    @Test
    void testGetCredentialSuccess() throws Exception {
        var pem = Resources.Pem.CERTIFICATE_CHAIN.load();
        var chain = new PemConverter().parseX509CertificateChain(pem);
        var activeCredential = Instancio.create(ActiveCredentialDTO.class).setContent(pem.toString());

        given(httpClient.getResponseFuture().get()).willReturn("credential-json");
        given(objectMapper.readValue(anyString(), eq(ActiveCredentialDTO.class)))
                .willReturn(activeCredential);

        given(pemConverter.parseX509CertificateChain(pem)).willReturn(chain);

        var result = authProviderClient.getCredential();

        assertThat(result).isEqualTo(chain);
    }

    @Test
    void testGetCredentialFailReturnEmptyList() throws Exception {
        given(httpClient.getResponseFuture().get()).willThrow(new RuntimeException("fail"));

        var result = authProviderClient.getCredential();

        assertThat(result).isEmpty();
    }

    @Test
    void testLoadPrivateKeySuccess() throws Exception {
        var pem = Resources.Pem.PRIVATE_KEY.load();
        var privateKey = new PemConverter().parsePrivateKey(pem);
        var activeCredential = Instancio.create(KeypairDTO.class).setPrivateKey(pem.toString());

        given(httpClient.getResponseFuture().get()).willReturn("credential-json");
        given(objectMapper.readValue(anyString(), eq(KeypairDTO.class))).willReturn(activeCredential);

        given(pemConverter.parsePrivateKey(pem)).willReturn(privateKey);

        var result = authProviderClient.loadPrivateKey();

        assertThat(result).as("Private key should be present").isPresent().hasValue(privateKey);
    }

    @Test
    void testLoadPrivateKeyReturnEmptyResult() throws Exception {
        given(httpClient.getResponseFuture().get()).willThrow(new RuntimeException("fail"));

        var result = authProviderClient.loadPrivateKey();

        assertThat(result).as("Empty optional should be returned").isEmpty();
    }

    @Test
    void shouldRetrieveEphemeralProofAndSendItToDestination() throws Exception {

        var proof = "ephemeral-proof";
        var destination = "consumer.com";
        var ephemeralProofDTO = Instancio.create(EphemeralProofDTO.class).setProof(proof);
        given(httpClient.getResponseFuture()).willReturn(CompletableFuture.completedFuture(proof));
        doNothing().when(httpClient).call(any());
        given(objectMapper.readValue(anyString(), eq(EphemeralProofDTO.class))).willReturn(ephemeralProofDTO);
        given(objectMapper.writeValueAsString(any())).willReturn("ephemeral-proof-json");
        var future = authProviderClient.getEphemeralProofAndSendToDest(destination);

        CompletableFuture.allOf(future).join();

        then(httpClient)
                .should()
                .call(argThat(req -> req.getUrl().toString().startsWith("http://localhost:8085")
                        && req.getUrl().toString().endsWith("/tier1/v2/ephemeralProof")
                        && Objects.equals(req.getMethod(), HttpMethod.GET)));
        then(httpClient)
                .should()
                .call(argThat(req -> req.getHost().equals(destination)
                        && req.getUrl().toString().endsWith(PARTICIPANT_EPHEMERAL_PROOF_TARGET_TIER2_V2)
                        && Objects.equals(req.getMethod(), HttpMethod.POST)));
    }

    @Test
    void testRetrieveEphemeralProofAndSendItToDestinationWhenFailEphemeralProofParsingShouldThrow() throws Exception {

        var proof = "malformed{json}";
        var destination = "consumer.com";
        given(httpClient.getResponseFuture()).willReturn(CompletableFuture.completedFuture(proof));
        doNothing().when(httpClient).call(any());
        given(objectMapper.readValue(anyString(), eq(EphemeralProofDTO.class)))
                .willThrow(new JsonParseException("fail"));
        var future = authProviderClient.getEphemeralProofAndSendToDest(destination);

        assertThatThrownBy(() -> CompletableFuture.allOf(future).join()).isInstanceOf(CompletionException.class);
    }
}
