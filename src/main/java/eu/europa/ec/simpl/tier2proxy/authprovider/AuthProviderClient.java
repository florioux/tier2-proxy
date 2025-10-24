package eu.europa.ec.simpl.tier2proxy.authprovider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import eu.europa.ec.simpl.tier2proxy.dto.ActiveCredentialDTO;
import eu.europa.ec.simpl.tier2proxy.dto.EphemeralProofDTO;
import eu.europa.ec.simpl.tier2proxy.dto.KeypairDTO;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HTTPClient;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpRequest;
import eu.europa.ec.simpl.util.PemConverter;
import eu.europa.ec.simpl.util.PemString;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuthProviderClient {

    protected static final String PARTICIPANT_EPHEMERAL_PROOF_TARGET_TIER2_V2 = "/authApi/tier2/v2/ephemeralProof";

    private static final Configuration configuration = Configuration.getInstance();
    private final HTTPClient httpClient;
    private final ObjectMapper objectMapper;
    private final PemConverter pemConverter;

    public List<X509Certificate> getCredential() {
        var url = configuration.getSimplProperties().authenticationProvider().getCredentialsUrl();
        log.debug("Requesting credentials from authentication provider {}", url);

        var request = new HttpRequest().setMethod(HttpMethod.GET).setUrl(url);

        httpClient.call(request);

        try {
            var out = httpClient.getResponseFuture().get();
            var activeCredential = objectMapper.readValue(out, ActiveCredentialDTO.class);
            log.debug("Retrieved credentials from authentication provider");
            log.debug("Parsing credential as PEM");
            var pem = PemString.of(activeCredential.getContent());
            log.debug("Parsed credential as PEM");
            return pemConverter.parseX509CertificateChain(pem);
        } catch (Exception e) {
            log.error("Failed to download credentials", e);
        }
        return new ArrayList<>();
    }

    public Optional<PrivateKey> loadPrivateKey() {
        var url = configuration.getSimplProperties().authenticationProvider().getKeypairsUrl();
        log.debug("Requesting keypairs from authentication provider {}", url);

        var request = new HttpRequest().setMethod(HttpMethod.GET).setUrl(url);

        httpClient.call(request);

        try {
            var out = httpClient.getResponseFuture().get();
            var keypairs = objectMapper.readValue(out, KeypairDTO.class);
            log.debug("Retrieved keypairs from authentication provider, {}", keypairs);
            log.debug("Parsing private key as PEM");
            var privateKey = PemString.of(keypairs.getPrivateKey());
            log.debug("Parsed private key as PEM");
            return Optional.of(pemConverter.parsePrivateKey(privateKey));
        } catch (Exception e) {
            log.error("Failed to download keypair", e);
            return Optional.empty();
        }
    }

    public CompletableFuture<Void> getEphemeralProofAndSendToDest(String dest) {
        var url = configuration.getSimplProperties().authenticationProvider().getEphemeralProofUrl();
        log.debug("Requesting ephemeral proof from authentication provider {}", url);
        var request = new HttpRequest().setMethod(HttpMethod.GET).setUrl(url);

        httpClient.call(request);
        return httpClient
                .getResponseFuture()
                .thenApply(ephemeralProof -> logSuccess(ephemeralProof, dest))
                .thenApply(this::parseResponse)
                .exceptionally(ex -> {
                    log.error("Failed to retrieve ephemeral proof from authentication provider", ex);
                    throw new CompletionException(ex);
                })
                .thenAccept(ephemeralProof -> sendEphemeralProof(ephemeralProof, dest))
                .thenApply(unused -> {
                    log.info("Sent ephemeral proof to destination {}", dest);
                    return null;
                });
    }

    private EphemeralProofDTO parseResponse(String response) {
        try {
            return objectMapper.readValue(response, EphemeralProofDTO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse response from authentication provider -> {}", response, e);
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    private void sendEphemeralProof(EphemeralProofDTO ephemeralProof, String dest) {
        var body = objectMapper.writeValueAsString(ephemeralProof);
        var request = new HttpRequest()
                .setMethod(HttpMethod.POST)
                .setContentType(HttpHeaderValues.APPLICATION_JSON.toString())
                .setUrl("https://%s".formatted(dest + PARTICIPANT_EPHEMERAL_PROOF_TARGET_TIER2_V2))
                .setBody(body);
        httpClient.call(request);
    }

    private String logSuccess(String ephemeralProof, String dest) {
        log.debug("Retrieved ephemeral proof from authentication provider");
        log.debug("Sending ephemeral proof to destination {}", dest);
        return ephemeralProof;
    }
}
