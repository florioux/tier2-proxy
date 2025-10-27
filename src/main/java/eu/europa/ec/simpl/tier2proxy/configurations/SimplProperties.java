package eu.europa.ec.simpl.tier2proxy.configurations;

import io.netty.handler.codec.http.HttpMethod;
import java.util.List;

public record SimplProperties(
        AuthenticationProviderProperties authenticationProvider, List<NoPreflightPath> noPreflightPaths) {

    public record NoPreflightPath(HttpMethod method, String path) {
        public boolean matches(HttpMethod method, String requestPath) {
            return this.method.equals(method) && this.path.equalsIgnoreCase(requestPath);
        }
    }

    public record AuthenticationProviderProperties(
            String baseurl, String getCredentialsUrl, String getKeypairsUrl, String getEphemeralProofUrl) {}
}
