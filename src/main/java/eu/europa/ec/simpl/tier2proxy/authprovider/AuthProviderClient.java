package eu.europa.ec.simpl.tier2proxy.authprovider;

import static eu.europa.ec.simpl.tier2proxy.proxy.handler.MitmHandler.PARTICIPANT_EPHEMERAL_PROOF_TARGET_V1;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.ec.simpl.tier2proxy.configurations.Configuration;
import eu.europa.ec.simpl.tier2proxy.dto.KeypairDTO;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HTTPClient;
import eu.europa.ec.simpl.tier2proxy.proxy.http.HttpRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class AuthProviderClient {

    private static final Configuration configuration = Configuration.getInstance();
    private final Bootstrap bootstrap;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public byte[] getCredential() {
        var url = configuration.getSimplProperties().authenticationProvider().getCredentialsUrl();
        log.debug("Requesting credentials from authentication provider {}", url);

        var httpClient = new HTTPClient(bootstrap);
        var request = new HttpRequest().setMethod(HttpMethod.GET).setUrl(url);

        httpClient.call(request);

        byte[] out = null;

        try {
            out = httpClient.getResponseFuture().get().getBytes(StandardCharsets.UTF_8);
            log.debug("Retrieved credentials from authentication provider");
        } catch (Exception e) {
            log.error("Failed to download credentials", e);
        }
        return out;
    }

    public KeypairDTO getInstalledKeypair() {
        var url = configuration.getSimplProperties().authenticationProvider().getKeypairsUrl();
        log.debug("Requesting keypairs from authentication provider {}", url);

        var httpClient = new HTTPClient(bootstrap);
        var request = new HttpRequest().setMethod(HttpMethod.GET).setUrl(url);

        httpClient.call(request);

        try {
            var out = httpClient.getResponseFuture().get();
            var keypairs = objectMapper.readValue(out, KeypairDTO.class);
            log.debug("Retrieved keypairs from authentication provider, {}", keypairs);
            return keypairs;
        } catch (Exception e) {
            log.error("Failed to download keypair", e);
            return new KeypairDTO();
        }
    }

    public CompletableFuture<Void> getEphemeralProofAndSendToDest(String dest) {
        var url = configuration.getSimplProperties().authenticationProvider().getEphemeralProofUrl();
        log.debug("Requesting ephemeral proof from authentication provider {}", url);
        var httpClient = new HTTPClient(bootstrap);
        var request = new HttpRequest().setMethod(HttpMethod.GET).setUrl(url);

        httpClient.call(request);
        return httpClient
                .getResponseFuture()
                .thenApply(ephemeralProof -> logSuccess(ephemeralProof, dest))
                .exceptionally(ex -> {
                    log.error("Failed to retrieve ephemeral proof from authentication provider", ex);
                    return null;
                })
                .thenAccept(ephemeralProof -> sendEphemeralProof(ephemeralProof, dest))
                .thenApply(unused -> {
                    log.info("Sent ephemeral proof to destination {}", dest);
                    return null;
                });
    }

    private void sendEphemeralProof(String ephemeralProof, String dest) {
        var httpClient = new HTTPClient(bootstrap);
        var request = new HttpRequest()
                .setMethod(HttpMethod.POST)
                .setContentType(HttpHeaderValues.TEXT_PLAIN.toString())
                .setUrl("https://%s".formatted(dest + PARTICIPANT_EPHEMERAL_PROOF_TARGET_V1))
                .setBody(ephemeralProof);
        httpClient.call(request);
    }

    private String logSuccess(String ephemeralProof, String dest) {
        log.debug("Retrieved ephemeral proof from authentication provider");
        log.debug("Sending ephemeral proof to destination {}", dest);
        return ephemeralProof;
    }
}
