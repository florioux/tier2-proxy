package eu.europa.ec.simpl.tier2proxy.configurations;

public record SimplProperties(AuthenticationProviderProperties authenticationProvider) {

    public record AuthenticationProviderProperties(
            String baseurl, String getCredentialsUrl, String getKeypairsUrl, String getEphemeralProofUrl) {}
}
